// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Fetches and parses a Google News RSS feed directly (no CORS in native HTTP).
 * Uses the JDK DOM parser so the logic is JVM-testable.
 */
class GoogleNewsRssSource(
    private val client: OkHttpClient = OkHttpClient(),
) {

    suspend fun fetch(url: String): List<RssItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            parse(body)
        }
    }

    fun parse(xml: String): List<RssItem> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        document.documentElement.normalize()

        val items = mutableListOf<RssItem>()
        val itemNodes = document.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val node = itemNodes.item(i) as? Element ?: continue
            val title = textOf(node, "title").orEmpty()
            val link = textOf(node, "link").orEmpty()
            if (title.isNotBlank() && link.isNotBlank()) {
                items.add(
                    RssItem(
                        title = title,
                        link = link,
                        pubDate = textOf(node, "pubDate"),
                        description = textOf(node, "description"),
                        source = sourceUrlOf(node),
                    ),
                )
            }
        }
        return items
    }

    private fun textOf(parent: Element, tag: String): String? =
        (parent.getElementsByTagName(tag).item(0) as? Element)?.textContent?.trim()

    private fun sourceUrlOf(parent: Element): String? =
        (parent.getElementsByTagName("source").item(0) as? Element)?.getAttribute("url")
}
