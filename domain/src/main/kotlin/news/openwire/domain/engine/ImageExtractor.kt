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
    /**
     * The large version of a feed thumbnail, where the image service says how to ask for one:
     * BBC ("/ace/standard/240/" → 1024), NYT ("-thumbStandard" → "-superJumbo"), Google
     * ("=w200-h150" → "=w1200"), WordPress ("-150x150.jpg" → the original), Cloudinary
     * ("/w_300/" → "/w_1200/") and plain width parameters. Signed addresses are left alone.
     */
    fun highRes(url: String?, width: Int = 1200): String? {
        val u = url?.trim()?.takeIf { it.startsWith("http") } ?: return url
        if (SIGNED.containsMatchIn(u)) return u
        var out = u
        // BBC: 1536 px for its photo library (sharp across an unfolded screen); "branded_news"
        // social cards only come in 1024.
        val bbcMax = if ("/branded_news/" in out) 1024 else 1536
        out = BBC.replace(out) { m -> "${m.groupValues[1]}/${maxOf(m.groupValues[2].toInt(), bbcMax).coerceAtMost(bbcMax)}/" }
        out = NYT.replace(out, "-superJumbo.")
        out = GOOGLE.replace(out, "=w$width")
        out = WP_SIZE.replace(out) { m -> m.groupValues[2] }
        out = CLOUDINARY.replace(out) { m -> if (m.groupValues[1].toInt() < width) "/w_$width" else m.value }
        out = WIDTH_PARAM.replace(out) { m ->
            val n = m.groupValues[3].toIntOrNull() ?: return@replace m.value
            if (n in 1 until 800) "${m.groupValues[1]}${m.groupValues[2]}=$width" else m.value
        }
        return out
    }

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
        .replace(RX_1) { m ->
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
        for (rx in META_IMAGE) rx.findAll(html).forEach { test(it.groupValues[1]) }
        RX_5.findAll(html).forEach { s ->
            val body = s.groupValues[1]
            RX_6.findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
            RX_7.findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
            RX_8.findAll(body).forEach { test(it.groupValues[1].replace("\\/", "/")) }
        }
        RX_9.findAll(html).forEach { test(it.groupValues[1]) }
        RX_10.findAll(html).forEach { test(it.groupValues[1]) }
        RX_11.findAll(html).forEach { block ->
            RX_12.findAll(block.value).forEach { tag ->
                val t = tag.value
                val w = RX_13.find(t)?.groupValues?.get(1)?.toIntOrNull()
                val h = RX_14.find(t)?.groupValues?.get(1)?.toIntOrNull()
                RX_15.find(t)?.groupValues?.get(1)?.let { srcset ->
                    srcset.split(',').map { it.trim().split(RX_2) }.filter { it.isNotEmpty() && it[0].isNotEmpty() }
                        .sortedByDescending { p -> p.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0 }
                        .forEach { p -> test(p[0], w, h) }
                }
                val src = RX_16.find(t)?.groupValues?.get(1)
                    ?: RX_3.find(t)?.groupValues?.get(1)
                test(src, w, h)
            }
        }
        return out.toList()
    }

    fun fromHtml(html: String?, baseUrl: String?): String? = candidatesFromHtml(html, baseUrl).firstOrNull()

    /** First editorial `<img src>` inside RSS description markup. */
    fun fromDescription(markup: String?, baseUrl: String?): String? {
        if (markup.isNullOrEmpty()) return null
        return RX_4.findAll(markup)
            .mapNotNull { resolve(it.groupValues[1], baseUrl) }.firstOrNull { isEditorialImage(it) }
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("&#(x[0-9a-fA-F]+|\\d+);")
private val RX_2 = Regex("\\s+")
private val RX_3 = Regex("\\ssrc=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val RX_4 = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val RX_5 = Regex("<script[^>]+type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
private val RX_6 = Regex("\"(?:image|thumbnailUrl)\"\\s*:\\s*\"([^\"]+)\"")
private val RX_7 = Regex("\"image\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"")
private val RX_8 = Regex("\"image\"\\s*:\\s*\\[\\s*\"([^\"]+)\"")
private val RX_9 = Regex("<link[^>]+rel=[\"']image_src[\"'][^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val RX_10 = Regex("<meta[^>]+itemprop=[\"']image[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val RX_11 = Regex("<(?:article|figure|main)[^>]*>([\\s\\S]*?)</(?:article|figure|main)>", RegexOption.IGNORE_CASE)
private val RX_12 = Regex("<img[^>]+>", RegexOption.IGNORE_CASE)
private val RX_13 = Regex("\\swidth=[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE)
private val RX_14 = Regex("\\sheight=[\"'](\\d+)[\"']", RegexOption.IGNORE_CASE)
private val RX_15 = Regex("\\ssrcset=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val RX_16 = Regex("\\sdata-src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

private val META_IMAGE: List<Regex> = listOf("(?:og:image|og:image:url|og:image:secure_url)", "(?:twitter:image|twitter:image:src)").flatMap { key ->
    listOf(
        Regex("<meta[^>]+(?:property|name)=[\"']$key[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
        Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']$key[\"']", RegexOption.IGNORE_CASE),
    )
}

private val SIGNED = Regex("[?&](s|sig|signature|token|hash)=", RegexOption.IGNORE_CASE)
private val BBC = Regex("(ichef\\.bbci\\.co\\.uk/(?:ace/(?:standard|ws)|news))/(\\d{2,4})/")
private val NYT = Regex("-(?:thumbStandard|thumbLarge|thumbWide|articleInline|mediumThreeByTwo\\d+|mediumSquare\\d+|threeByTwoSmallAt2X|blog\\d+|videoSixteenByNine\\d+)\\.(?=jpg|jpeg|png|webp)")
private val GOOGLE = Regex("=(?:w\\d+-h\\d+|s\\d+)(?:-[a-z0-9-]+)?$")
private val WP_SIZE = Regex("(-\\d{2,4}x\\d{2,4})(\\.(?:jpg|jpeg|png|webp))(?=$|\\?)")
private val CLOUDINARY = Regex("/w_(\\d{2,4})(?=[,/])")
private val WIDTH_PARAM = Regex("([?&])(w|width|resize_w|imwidth)=(\\d{2,4})(?=&|$)")
