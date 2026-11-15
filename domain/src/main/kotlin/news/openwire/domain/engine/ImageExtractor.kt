// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI

/**
 * Editorial photo extraction from article HTML, rejecting logos, favicons,
 * avatars, pixels and odd aspect ratios. Port of js/imageService.js.
 */
object ImageExtractor {

    private val REJECT = listOf(
        Regex("\\.svg(?:\\?|#|$)", RegexOption.IGNORE_CASE),
        Regex("\\.ico(?:\\?|#|$)", RegexOption.IGNORE_CASE),
        Regex("^data:image", RegexOption.IGNORE_CASE),
        Regex("(?:^|[_\\-/.])(?:fav)?icon(?:s)?(?:[_\\-/.]|\\d|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[_\\-/.])logo(?:s)?(?:[_\\-/.]|\\d|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[_\\-/.])avatar(?:s)?(?:[_\\-/.]|\\d|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[_\\-/.])badge(?:s)?(?:[_\\-/.]|\\d|$)", RegexOption.IGNORE_CASE),
        Regex("(?:^|[_\\-/.])(placeholder|spacer|pixel|tracking|spinner|sprite|1x1|clear\\.gif|blank\\.gif)", RegexOption.IGNORE_CASE),
        Regex("akamai-logo", RegexOption.IGNORE_CASE),
        Regex("apple-touch-icon", RegexOption.IGNORE_CASE),
        Regex("masthead", RegexOption.IGNORE_CASE),
    )

    /** True when the URL (and dimensions, if known) look like an editorial photo. */
    fun isEditorialImage(url: String?, width: Int? = null, height: Int? = null): Boolean {
        val u = url?.trim() ?: return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false
        if (REJECT.any { it.containsMatchIn(u) }) return false
        if (width != null && height != null && width > 0 && height > 0) {
            if (width < 280 || height < 160) return false
            val ratio = width.toDouble() / height
            if (ratio in 0.85..1.15 && width <= 450) return false
            if (ratio < 0.6 || ratio > 3.2) return false
        }
        return true
    }

    private fun decode(s: String) = s.trim().replace("&amp;", "&", ignoreCase = true)
        .replace(Regex("&#(x[0-9a-fA-F]+|\\d+);")) { m ->
            val v = m.groupValues[1]
            val c = if (v.startsWith("x", true)) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
            if (c != null && c <= 0x10FFFF) String(Character.toChars(c)) else ""
        }

    private fun resolve(candidate: String, baseUrl: String?): String? {
        var r = decode(candidate)
        if (r.isEmpty()) return null
        if (r.startsWith("//")) r = "https:$r"
        else if (!r.startsWith("http")) r = try { URI(baseUrl ?: return null).resolve(r).toString() } catch (e: Exception) { return null }
        return r
    }

    /** Ordered, distinct candidates: OpenGraph → Twitter → JSON-LD → image_src/itemprop → in-article <img>. */
    fun candidatesFromHtml(html: String?, baseUrl: String?): List<String> {
        if (html.isNullOrEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        fun test(c: String?, w: Int? = null, h: Int? = null) {
            val r = c?.let { resolve(it, baseUrl) } ?: return
            if (isEditorialImage(r, w, h)) out.add(r)
        }
        val og = "(?:og:image|og:image:url|og:image:secure_url)"
        val tw = "(?:twitter:image|twitter:image:src)"
        for (key in listOf(og, tw)) {
            Regex("<meta[^>]+(?:property|name)=[\"']$key[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { test(it.groupValues[1]) }
            Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']$key[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { test(it.groupValues[1]) }
        }
        Regex("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE).findAll(html).forEach { s ->
            val body = s.groupValues[1]
            Regex("\"(?:image|thumbnailUrl)\"\\s*:\\s*\"([^\"]+)\"").findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
            Regex("\"image\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"").findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
            Regex("\"image\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
        }
        Regex("<link[^>]+rel=[\"']image_src[\"'][^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { test(it.groupValues[1]) }
        Regex("<meta[^>]+itemprop=[\"']image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).forEach { test(it.groupValues[1]) }
        Regex("<(?:article|figure|main)[^>]*>([\\s\\S]*?)</(?:article|figure|main)>", RegexOption.IGNORE_CASE).findAll(html).forEach { block ->
            Regex("<img[^>]+>", RegexOption.IGNORE_CASE).findAll(block.value).forEach { tag ->
                val t = tag.value
                val w = Regex("\\swidth=[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull()
                val h = Regex("\\sheight=[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull()
                Regex("\\ssrcset=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.let { srcset ->
                    srcset.split(',').map { it.trim().split(Regex("\\s+")) }.filter { it.isNotEmpty() && it[0].isNotEmpty() }
                        .sortedByDescending { p -> p.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0 }
                        .forEach { p -> test(p[0], w, h) }
                }
                val src = Regex("\\sdata-src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
                    ?: Regex("\\ssrc=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
                test(src, w, h)
            }
        }
        return out.toList()
    }

    fun fromHtml(html: String?, baseUrl: String?): String? = candidatesFromHtml(html, baseUrl).firstOrNull()

    /** First editorial `<img src>` inside RSS description markup. */
    fun fromDescription(markup: String?, baseUrl: String?): String? {
        if (markup.isNullOrEmpty()) return null
        return Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(markup)
            .mapNotNull { resolve(it.groupValues[1], baseUrl) }.firstOrNull { isEditorialImage(it) }
    }
}
