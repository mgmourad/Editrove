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
     * Some servers reset HTTP/2 streams on feed paths they've retired or rewritten
     * ("stream was reset: INTERNAL_ERROR"); the same address over HTTP/1.1 often still answers,
     * and when it doesn't, the site's page usually says where the feed lives now.
     */
    private val http1 by lazy { client.newBuilder().protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build() }
    private val moved = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun fetchOnce(url: String): ParsedFeed {
        moved[url]?.let { m -> runCatching { return fetchWith(client, m) } }
        return try {
            fetchWith(client, url)
        } catch (e: IOException) {
            val reset = e is okhttp3.internal.http2.StreamResetException || e.message.orEmpty().contains("stream was reset", true) ||
                e is java.net.ProtocolException
            if (reset) runCatching { return fetchWith(http1, url) }
            // Retired address (reset, 404, 410): look for the feed the site advertises now.
            if (reset || e.message in GONE) relocate(url)?.let { (u, feed) -> moved[url] = u; return feed }
            throw e
        }
    }

    /** The site's current feed for the same section, read from its own pages' feed links. */
    private suspend fun relocate(url: String): Pair<String, ParsedFeed>? = withContext(Dispatchers.IO) {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return@withContext null
        val section = url.trimEnd('/').substringAfterLast('/').substringAfterLast("rss-").lowercase()
        for (page in listOf("https://${u.host}/", "https://${u.host}${u.path.substringBeforeLast('/')}/")) {
            val html = runCatching {
                http1.newCall(Request.Builder().url(page).header("User-Agent", UA).build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull() ?: continue
            val links = FEED_LINK.findAll(html).map { it.groupValues[1].replace("&amp;", "&") }
                .map { l -> if (l.startsWith("http")) l else "https://${u.host}/${l.trimStart('/')}" }.distinct().toList()
            val pick = links.firstOrNull { section.isNotEmpty() && it.lowercase().contains(section) } ?: links.firstOrNull() ?: continue
            if (pick == url) continue
            runCatching { return@withContext pick to fetchWith(http1, pick) }
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
private val GONE = setOf("HTTP 404", "HTTP 410", "HTTP 301", "HTTP 308")
private val FEED_LINK = Regex("<link[^>]+type=[\"']application/(?:rss|atom)\\+xml[\"'][^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
