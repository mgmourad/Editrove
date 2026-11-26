// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import news.openwire.domain.engine.FeedDiscovery
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.TopicRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Finds news sources by name or website and checks each one live, so only working feeds
 * with recent stories are offered. Sends only the words typed: to Feedly's public index,
 * Google News and the sites themselves.
 */
class SourceFinder(
    private val rss: GoogleNewsRssSource = GoogleNewsRssSource(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).followRedirects(true).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A working source, ready to add. */
    data class Found(
        val name: String,
        val url: String,
        val domain: String,
        val language: String?,
        val sample: String?,
        val newestAt: Long?,
        val count: Int,
        /** No feed of its own: its stories come through Google News. */
        val viaGoogle: Boolean,
        val score: Double,
    )

    private val checked = ConcurrentHashMap<String, Found>()
    /** Outlet names as Google News gives them ("اليوم السابع"), by site. */
    private val siteNames = ConcurrentHashMap<String, String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(6)

    /**
     * Results as they're confirmed (best first each time). [appLanguage] and [country] set
     * where Google News looks and which languages rank higher.
     */
    fun search(query: String, appLanguage: String, country: String, sections: Boolean = false): Flow<List<Found>> = channelFlow {
        val q = query.trim()
        if (q.length < 2) { send(emptyList()); return@channelFlow }
        val found = LinkedHashMap<String, Found>()
        val lock = Mutex()
        suspend fun offer(f: Found?) {
            if (f == null) return
            // Corporate press rooms and investor pages aren't news; the same name twice is one feed.
            if (PRESS.containsMatchIn(f.name) || PRESS.containsMatchIn(f.domain)) return
            val list = lock.withLock {
                if (found.values.any { it.name.equals(f.name, true) && it.domain == f.domain }) return@withLock null

                val key = f.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
                if (key !in found) found[key] = f
                // At most three feeds from one site, best first (every section when asked for).
                found.values.sortedByDescending { it.score }.groupBy { it.domain }.flatMap { it.value.take(if (sections) 12 else 3) }
                    .sortedByDescending { it.score }.take(if (sections) 40 else 12)
            } ?: return
            send(list)
        }
        withTimeoutOrNull(25_000) {
            coroutineScope {
                val domains = LinkedHashSet<String>()
                if (FeedDiscovery.looksLikeSite(q)) {
                    FeedDiscovery.domainOf(q)?.let(domains::add)
                    // A pasted feed address is checked as it is.
                    if (q.startsWith("http")) launch { offer(check(q, q, null, 0, false)) }
                }
                // 1. Feedly's index: real feed addresses, many of them stale.
                val indexed = async { get(FeedDiscovery.feedlyUrl(q, appLanguage))?.let(FeedDiscovery::parseFeedly).orEmpty() }
                // 2. Google News: which outlets publish under this name (their real websites).
                val outlets = async { outletsFor(q, appLanguage, country) }
                val idx = indexed.await()
                val sites = outlets.await() // also learns outlet names, used to name section feeds
                idx.take(10).forEach { i -> launch { offer(check(q, i.url, i.title, i.subscribers, false)) } }
                idx.mapNotNull { it.website?.let(FeedDiscovery::domainOf) }.take(4).forEach(domains::add)
                sites.forEach(domains::add)
                // 3. The sites themselves: the feed a homepage advertises, else the usual addresses,
                // else (no feed at all) the site's stories through Google News.
                domains.take(6).forEach { d -> launch { fromSite(q, d, appLanguage, country, wide = sections).forEach { offer(it) } } }
                // 4. Every section of the outlets that match the name (World, Business, Tech…).
                if (sections) sites.take(2).forEach { d -> SECTIONS.forEach { (label, word) ->
                    launch {
                        val name = "${siteNames[d] ?: d} – $label"
                        offer(check(q, TopicRegistry.googleNewsSearchUrl("site:$d $word when:3d", TopicRegistry.country(country)), name, 0, true, d)?.copy(name = name))
                    }
                } }
            }
        }
        lock.withLock {
            send(found.values.sortedByDescending { it.score }.groupBy { it.domain }.flatMap { it.value.take(if (sections) 12 else 3) }
                .sortedByDescending { it.score }.take(if (sections) 40 else 12))
        }
    }

    /** Outlets Google News lists for [q] whose name or site matches it (else the most frequent two). */
    private suspend fun outletsFor(q: String, appLanguage: String, country: String): List<String> {
        val locales = listOfNotNull(
            TopicRegistry.country(country),
            TopicRegistry.COUNTRIES.firstOrNull { it.language == appLanguage },
        ).distinct()
        val items = locales.map { c ->
            runCatching { rss.fetchFeed(TopicRegistry.googleNewsSearchUrl(q, c)).items }.getOrDefault(emptyList())
        }.flatten()
        val sources = items.mapNotNull { it.source?.let { url -> FeedDiscovery.domainOf(url)?.let { d -> d to (it.sourceName ?: d) } } }
        sources.forEach { (d, name) -> if (name != d) siteNames.putIfAbsent(d, name) }
        val nq = FeedDiscovery.normalize(q)
        val compact = nq.replace(" ", "")
        val matching = sources.filter { (d, name) ->
            val n = FeedDiscovery.normalize(name)
            n.contains(nq) || (nq.length >= 4 && nq.contains(n)) || d.replace("-", "").contains(compact)
        }.map { it.first }.distinct()
        if (matching.isNotEmpty()) return matching.take(3)
        return sources.groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }.take(2).map { it.key }
    }

    private suspend fun fromSite(q: String, domain: String, appLanguage: String, country: String, wide: Boolean = false): List<Found> {
        val home = listOf("https://$domain", "https://www.$domain").firstNotNullOfOrNull { url -> get(url, html = true)?.let { url to it } }
        val advertised = home?.let { (base, html) ->
            runCatching {
                Jsoup.parse(html, base).select("link[rel~=alternate][type~=(rss|atom)], a[href~=(?i)(/feed/?$|/rss|\\.rss$|rss\\.xml|atom\\.xml)]")
                    .map { it.absUrl("href") }.filter { it.startsWith("http") }.distinct().take(if (wide) 14 else 5)
            }.getOrDefault(emptyList())
        }.orEmpty()
        val siteName = home?.second?.let { runCatching { Jsoup.parse(it).let { d -> d.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { s -> s.isNotBlank() } ?: d.title().substringBefore(" - ").substringBefore(" | ").take(40) } }.getOrNull() }
        val candidates = advertised.ifEmpty { FeedDiscovery.COMMON_PATHS.take(5).map { "https://$domain$it" } }
        val ok = coroutineScope { candidates.map { u -> async { check(q, u, null, 0, false) } }.awaitAll().filterNotNull() }
        if (ok.isNotEmpty()) return ok
        // No working feed: the site's own stories, through Google News, in its language.
        val probe = check(q, TopicRegistry.googleNewsSearchUrl("site:$domain when:3d", TopicRegistry.country(country)), siteNames[domain] ?: siteName ?: domain, 0, true, domain)
        val lang = probe?.language
        if (probe != null && (lang == null || lang == TopicRegistry.country(country).language)) return listOf(probe)
        val c = TopicRegistry.COUNTRIES.firstOrNull { it.language == (lang ?: appLanguage) } ?: TopicRegistry.COUNTRIES.first()
        return listOfNotNull(check(q, TopicRegistry.googleNewsSearchUrl("site:$domain when:3d", c), siteNames[domain] ?: siteName ?: domain, 0, true, domain) ?: probe)
    }

    /** Reads [url] as a feed: working, at least three stories, the newest within ten days. */
    private suspend fun check(q: String, url: String, title: String?, subscribers: Long, viaGoogle: Boolean, siteDomain: String? = null): Found? {
        checked[url]?.let { return it }
        if (url in failed) return null
        val result = gate.withPermit {
            runCatching {
                val feed = withTimeoutOrNull(10_000) { rss.fetchFeed(url) } ?: return@runCatching null
                val items = feed.items
                if (items.size < 3) return@runCatching null
                val newest = items.mapNotNull { NewsProcessor.parseDate(it.pubDate) }.maxOrNull()
                if (newest != null && clock() - newest > 10L * 24 * 60 * 60 * 1000) return@runCatching null
                val domain = siteDomain ?: FeedDiscovery.domainOf(items.first().link)?.takeIf { !it.endsWith("google.com") } ?: FeedDiscovery.domainOf(url) ?: return@runCatching null
                val raw = (title ?: feed.title)?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("\"") } ?: domain
                val name = if (viaGoogle) raw else FeedDiscovery.cleanName(raw, siteNames[domain] ?: siteNames["www.$domain"])
                val ageHours = newest?.let { (clock() - it) / 3_600_000 } ?: 48
                Found(
                    name = if (viaGoogle) name else name.take(60),
                    url = url, domain = domain,
                    language = FeedDiscovery.detectLanguage(items.take(15).map { it.title }),
                    sample = items.first().title.substringBeforeLast(" - ").take(140),
                    newestAt = newest, count = items.size, viaGoogle = viaGoogle,
                    score = FeedDiscovery.score(q, name, domain, subscribers, ageHours, viaGoogle),
                )
            }.getOrNull()
        }
        if (result != null) checked[url] = result else failed += url
        return result
    }

    private suspend fun get(url: String, html: Boolean = false): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", if (html) "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36" else "Editrove/1.0 (Android news reader)")
                    .build(),
            ).execute().use { r -> if (r.isSuccessful) r.body?.string()?.take(600_000) else null }
        }.getOrNull()
    }

    private companion object {
        /** Sections offered for a matched outlet: label and the word Google News searches for. */
        val PRESS = Regex("(?i)press ?room|investor|press releases|careers")
        val SECTIONS = listOf(
            "World" to "world", "Politics" to "politics", "Business" to "business", "Tech" to "technology",
            "Science" to "science", "Health" to "health", "Sports" to "sports", "Entertainment" to "entertainment",
        )
    }
}
