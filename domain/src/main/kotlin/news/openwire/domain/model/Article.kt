// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * A news article, matching the JSON snapshot shape. `publishedAt` is epoch
 * millis (parsed from the raw `pubDate` string by NewsProcessor.parseDate).
 */
data class Article(
    val id: String,
    val title: String,
    val source: String? = null,
    val domain: String? = null,
    val originalUrl: String? = null,
    val publishedAt: Long? = null,
    val status: String? = null,
    val badge: String? = null,
    val targetUrl: String? = null,
    val archiveUrl: String? = null,
    val imageUrl: String? = null,
    /** Public excerpt (RSS description or preview index), plain text. */
    val summary: String? = null,
    /** The direct feed this story came from, when woven from a publisher feed. */
    val feedUrl: String? = null,
) {
    /** True when the routing engine needs an archive snapshot to open this story. */
    val needsArchive: Boolean get() = badge == "Archive.is" || badge == "Wayback"
}
