// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Fetches and parses RSS 2.0 and Atom feeds directly (native HTTP: no CORS, no
 * converter services). Google News feeds and publisher feeds share this path.
 * Uses the JDK DOM parser so the logic is JVM-testable.
 */
class GoogleNewsRssSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    /** Where moved feeds live now (the app keeps these across restarts). */
    private val relocations: FeedRelocations = FeedRelocations.Memory,
) {

    suspend fun fetch(url: String): List<RssItem> = fetchFeed(url).items

    /**
     * Fetches a feed. Plain http:// feeds are tried over https:// first (most hosts serve
     * both); only if that fails is the original http URL used.
     */
    suspend fun fetchFeed(url: String): ParsedFeed {
        val u = url.trim()
        if (u.startsWith("http://", ignoreCase = true)) {
            runCatching { return fetchOnce("https://" + u.substring(7)) }
        }
        return fetchOnce(u)
    }

    /**
     * Feeds heal themselves. When a feed stops answering the way a feed should:
     *
     * 1. a reset HTTP/2 stream ("stream was reset: INTERNAL_ERROR") is retried once over HTTP/1.1;
     * 2. a retired address (404/410, a reset that persists, a web page or an empty feed where the
     *    feed was) is looked up again on the site itself: the homepage's advertised feeds, the
     *    feed's own folder and the usual "RSS" directory pages. The candidate sharing the old
     *    address's words (same section) that reads as a real feed is used instead.
     *
     * A site that refuses Editrove (401/403) or asks it to slow down (429) is left alone: no retry
     * under another identity. A new address is remembered ([relocations], kept across restarts),
     * so later fetches go straight there; if it ever fails, the original is tried again. A feed
     * that can't be found isn't searched for again for six hours.
     */
    private val http1 by lazy { client.newBuilder().protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build() }
    private val lastSearch = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private suspend fun fetchOnce(url: String): ParsedFeed {
        relocations.get(url)?.let { m ->
            runCatching { return fetchWith(client, m).also { requireItems(it) } }.onFailure { relocations.remove(url) }
        }
        val failure: Exception = try {
            return fetchWith(client, url).also { requireItems(it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            e
        }
        val msg = failure.message.orEmpty()
        val reset = failure is okhttp3.internal.http2.StreamResetException || msg.contains("stream was reset", true) || failure is java.net.ProtocolException
        if (reset) runCatching { return fetchWith(http1, url).also { requireItems(it) } }
        val notAFeed = failure is org.xml.sax.SAXException || failure is NotAFeed
        if (reset || notAFeed || msg in GONE) relocate(url)?.let { (to, feed) ->
            relocations.put(url, to)
            return feed
        }
        throw failure
    }

    private class NotAFeed : IOException("The feed has no stories.")
    private fun requireItems(f: ParsedFeed) { if (f.items.isEmpty()) throw NotAFeed() }

    /** The site's current address for the same feed (see [fetchOnce]). */
    private suspend fun relocate(url: String): Pair<String, ParsedFeed>? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - (lastSearch[url] ?: 0L) < 6 * 3_600_000L) return@withContext null
        lastSearch[url] = now
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return@withContext null
        val host = u.host ?: return@withContext null
        val folder = u.path.substringBeforeLast('/', "")
        val pages = (listOf("https://$host/", "https://$host$folder/") + news.openwire.domain.engine.FeedRelocation.DIRECTORY_PATHS.map { "https://$host$it" }).distinct()
        val links = LinkedHashSet<String>()
        for (page in pages) {
            val html = runCatching {
                http1.newCall(Request.Builder().url(page).header("User-Agent", UA).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull() ?: continue
            links += news.openwire.domain.engine.FeedRelocation.feedLinks(html, page)
            // Enough to choose from once the best match shares every word of the old address.
            val best = news.openwire.domain.engine.FeedRelocation.rank(url, links.toList()).firstOrNull()
            if (best != null && news.openwire.domain.engine.FeedRelocation.words(url).all { it in news.openwire.domain.engine.FeedRelocation.words(best) }) break
        }
        for (candidate in news.openwire.domain.engine.FeedRelocation.rank(url, links.toList()).take(4)) {
            runCatching { return@withContext candidate to fetchWith(http1, candidate).also { requireItems(it) } }
        }
        null
    }

    private suspend fun fetchWith(c: OkHttpClient, url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            // No address in the ID: some CDNs (CBC's) drop any request whose user agent carries a URL.
            .header("User-Agent", UA)
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
            .build()
        c.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            parseFeed(body)
        }
    }

    fun parse(xml: String): List<RssItem> = parseFeed(xml).items

    fun parseFeed(xml: String): ParsedFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Never resolve external entities from untrusted feeds (XXE). Not every
            // platform parser supports every flag, so each is best-effort.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isExpandEntityReferences = false }
        }
        val cleaned = xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(cleaned)))
        val root = document.documentElement
        root.normalize()

        val items = mutableListOf<RssItem>()
        val rssItems = document.getElementsByTagName("item")
        if (rssItems.length > 0) {
            for (i in 0 until rssItems.length) {
                val node = rssItems.item(i) as? Element ?: continue
                val title = textOf(node, "title").orEmpty()
                val link = textOf(node, "link").orEmpty().ifEmpty { textOf(node, "guid").orEmpty().takeIf { it.startsWith("http") }.orEmpty() }
                if (title.isBlank() || link.isBlank()) continue
                val sourceEl = node.getElementsByTagName("source").item(0) as? Element
                items.add(
                    RssItem(
                        title = title,
                        link = link,
                        pubDate = textOf(node, "pubDate") ?: textOf(node, "dc:date"),
                        description = textOf(node, "description") ?: textOf(node, "content:encoded"),
                        source = sourceEl?.getAttribute("url")?.takeIf { it.isNotEmpty() },
                        sourceName = sourceEl?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                        imageUrl = mediaImage(node),
                        author = byline(textOf(node, "dc:creator") ?: textOf(node, "author")),
                    ),
                )
            }
        } else {
            // Atom
            val entries = document.getElementsByTagName("entry")
            for (i in 0 until entries.length) {
                val node = entries.item(i) as? Element ?: continue
                val title = textOf(node, "title").orEmpty()
                val links = node.getElementsByTagName("link")
                var link = ""
                for (j in 0 until links.length) {
                    val l = links.item(j) as? Element ?: continue
                    val rel = l.getAttribute("rel")
                    if (rel.isEmpty() || rel == "alternate") { link = l.getAttribute("href"); break }
                }
                if (title.isBlank() || link.isBlank()) continue
                items.add(
                    RssItem(
                        title = title,
                        link = link,
                        pubDate = textOf(node, "published") ?: textOf(node, "updated"),
                        description = textOf(node, "summary") ?: textOf(node, "content"),
                        source = null,
                        imageUrl = mediaImage(node),
                        author = byline((node.getElementsByTagName("author").item(0) as? Element)?.let { a -> textOf(a, "name") ?: a.textContent?.trim() }),
                    ),
                )
            }
        }
        val channelTitle = (root.getElementsByTagName("title").item(0) as? Element)?.textContent?.trim()
        return ParsedFeed(channelTitle, items)
    }

    private fun mediaImage(node: Element): String? {
        for (tag in listOf("media:content", "media:thumbnail", "enclosure", "media:group")) {
            val list = node.getElementsByTagName(tag)
            for (j in 0 until list.length) {
                val e = list.item(j) as? Element ?: continue
                val url = e.getAttribute("url").ifEmpty { e.getAttribute("href") }
                val type = e.getAttribute("type")
                val medium = e.getAttribute("medium")
                if (url.startsWith("http") && (type.isEmpty() || type.startsWith("image/")) && (medium.isEmpty() || medium == "image")) return url
            }
        }
        return null
    }

    /** "jane@x.com (Jane Doe)" → "Jane Doe"; drops e-mail-only and over-long values. */
    private fun byline(raw: String?): String? {
        val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val inParens = Regex("\\(([^)]+)\\)").find(t)?.groupValues?.get(1)
        val name = (inParens ?: t).replace(Regex("\\s+"), " ").trim().removePrefix("By ").removePrefix("by ")
        return name.takeIf { it.length in 2..80 && !it.contains('@') && !it.startsWith("http") }
    }

    private fun textOf(parent: Element, tag: String): String? =
        (parent.getElementsByTagName(tag).item(0) as? Element)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}

private const val UA = "Mozilla/5.0 (Linux; Android 15) Editrove/0.3"
private val GONE = setOf("HTTP 400", "HTTP 404", "HTTP 410")

/** Where feeds that moved live now: original address → current one. */
interface FeedRelocations {
    fun get(url: String): String?
    fun put(url: String, to: String)
    fun remove(url: String)

    /** For tests and the default: forgotten when the app closes. */
    object Memory : FeedRelocations {
        private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
        override fun get(url: String) = map[url]
        override fun put(url: String, to: String) { map[url] = to }
        override fun remove(url: String) { map.remove(url) }
    }
}
