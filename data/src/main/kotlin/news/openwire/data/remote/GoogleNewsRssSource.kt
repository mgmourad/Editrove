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

    suspend fun fetchFeed(url: String): ParsedFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) Editrove/0.3 (+https://github.com/mgmourad/Editrove)")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
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

    private fun textOf(parent: Element, tag: String): String? =
        (parent.getElementsByTagName(tag).item(0) as? Element)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
