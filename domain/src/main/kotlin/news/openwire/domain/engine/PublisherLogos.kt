// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI

/**
 * Finds a publisher's high-resolution brand icon from its home page: web-app manifest
 * icons (often 512px), apple-touch-icons and large `rel=icon` links — instead of a tiny
 * favicon. Used to fill the photo canvas when a story has no image.
 */
object PublisherLogos {

    data class Candidate(val url: String, val size: Int)

    /** The home page's manifest URL, if it declares one. */
    fun manifestUrl(html: String, baseUrl: String): String? =
        LINK.findAll(html).map { it.value }.firstOrNull { REL_MANIFEST.containsMatchIn(it) }
            ?.let { HREF.find(it)?.groupValues?.get(1) }?.let { absolute(it, baseUrl) }

    /** Icon links in the page head, largest first. */
    fun fromHtml(html: String, baseUrl: String): List<Candidate> = LINK.findAll(html).mapNotNull { m ->
        val tag = m.value
        val rel = REL.find(tag)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
        if ("icon" !in rel || "mask" in rel) return@mapNotNull null
        val href = HREF.find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
        if (href.endsWith(".svg", ignoreCase = true)) return@mapNotNull null
        val size = SIZES.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: if ("apple-touch" in rel) 180 else 32
        absolute(href, baseUrl)?.let { Candidate(it, size) }
    }.sortedByDescending { it.size }.toList()

    /** Icons listed in a web-app manifest, largest first (maskable ones last). */
    fun fromManifest(json: String, manifestUrl: String): List<Candidate> = ICON_OBJ.findAll(json).mapNotNull { m ->
        val obj = m.value
        val src = SRC.find(obj)?.groupValues?.get(1)?.replace("\\/", "/") ?: return@mapNotNull null
        if (src.endsWith(".svg", ignoreCase = true)) return@mapNotNull null
        val size = SIZES_JSON.find(obj)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val maskable = obj.contains("maskable")
        absolute(src, manifestUrl)?.let { Candidate(it, if (maskable) size / 2 else size) }
    }.sortedByDescending { it.size }.toList()

    /** Best of the candidates; null when nothing is big enough to fill a canvas. */
    fun best(candidates: List<Candidate>, minSize: Int = 120): String? = candidates.maxByOrNull { it.size }?.takeIf { it.size >= minSize }?.url

    private fun absolute(href: String, base: String): String? = runCatching {
        val h = href.trim().replace("&amp;", "&")
        if (h.startsWith("//")) "https:$h" else URI(base).resolve(h).toString()
    }.getOrNull()?.takeIf { it.startsWith("http") }
}

private val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
private val REL = Regex("\\brel\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val REL_MANIFEST = Regex("\\brel\\s*=\\s*[\"']manifest[\"']", RegexOption.IGNORE_CASE)
private val HREF = Regex("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val SIZES = Regex("\\bsizes\\s*=\\s*[\"'](\\d+)x\\d+", RegexOption.IGNORE_CASE)
private val ICON_OBJ = Regex("\\{[^{}]*\"src\"[^{}]*\\}")
private val SRC = Regex("\"src\"\\s*:\\s*\"([^\"]+)\"")
private val SIZES_JSON = Regex("\"sizes\"\\s*:\\s*\"(\\d+)x\\d+")
