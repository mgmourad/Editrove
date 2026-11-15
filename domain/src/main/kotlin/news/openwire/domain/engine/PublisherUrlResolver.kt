// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI

/**
 * Resolves Google News wrapper links to the publisher's own URL: first by decoding
 * an embedded URL, then via the pipeline-published `publisher_urls.json` map.
 * Port of js/publisherUrlService.js.
 */
object PublisherUrlResolver {

    /** Accepts only plain http(s) publisher URLs (no credentials, not Google). */
    fun publisherUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return try {
            val u = URI(value)
            val host = u.host ?: return ""
            if ((u.scheme == "http" || u.scheme == "https") && u.userInfo == null && host != "google.com" && !host.endsWith(".google.com")) u.toString() else ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Filters a raw id → url map down to safe publisher URLs. */
    fun sanitizeMap(raw: Map<String, String>): Map<String, String> =
        raw.mapNotNull { (k, v) -> publisherUrl(v).takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()

    /** Publisher URL for [value], or "" when it is a wrapper we cannot resolve. Non-Google URLs pass through. */
    fun resolve(value: String?, published: Map<String, String>): String {
        if (value.isNullOrEmpty()) return ""
        if (!GoogleNewsUrls.isGoogleNewsUrl(value)) return value
        GoogleNewsUrls.decodeEmbeddedGoogleUrl(value).takeIf { it.isNotEmpty() }?.let { return it }
        val id = GoogleNewsUrls.googleNewsArticleId(value)
        return if (id.isEmpty()) "" else published[id].orEmpty()
    }
}
