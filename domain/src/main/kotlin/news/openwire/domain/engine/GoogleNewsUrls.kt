// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.util.Base64

/**
 * Google News wrapper URL utilities, ported from js/publisherUrlService.js.
 */
object GoogleNewsUrls {

    fun isGoogleNewsUrl(value: String?): Boolean {
        if (value.isNullOrEmpty()) return false
        return try {
            java.net.URI(value).host == "news.google.com"
        } catch (e: Exception) {
            false
        }
    }

    fun googleNewsArticleId(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            val url = java.net.URI(value)
            if (url.host != "news.google.com") return ""
            val path = url.path ?: ""
            Regex("^/(?:rss/)?(?:articles|read)/([^/]+)/?$").find(path)?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Decodes a length-prefixed publisher URL embedded in older Google News wrappers. */
    fun decodeEmbeddedGoogleUrl(value: String?): String {
        val id = googleNewsArticleId(value)
        if (id.isEmpty()) return ""
        return try {
            val bytes = Base64.getDecoder().decode(id.replace('-', '+').replace('_', '/'))
            if (bytes.size < 4 || bytes[0].toInt() != 8 || bytes[1].toInt() != 19 || bytes[2].toInt() != 34) return ""
            var length = 0
            var offset = 3
            var shift = 0
            while (offset < bytes.size && shift <= 21) {
                val byte = bytes[offset++].toInt() and 0xFF
                length += (byte and 127) shl shift
                if (byte and 128 == 0) {
                    if (length == 0 || offset + length > bytes.size) return ""
                    val decoded = String(bytes, offset, length, Charsets.UTF_8)
                    return publisherUrl(decoded)
                }
                shift += 7
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun publisherUrl(value: String): String {
        return try {
            val url = java.net.URI(value)
            val host = url.host ?: return ""
            val scheme = url.scheme ?: return ""
            if ((scheme != "http" && scheme != "https") || url.userInfo != null) return ""
            if (host == "google.com" || host.endsWith(".google.com")) return ""
            url.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
