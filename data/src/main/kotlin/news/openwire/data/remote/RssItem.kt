// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

/** A parsed RSS/Atom item, before normalization. */
data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String?,
    val description: String?,
    val source: String?,
    /** Publisher name from Google News `<source>` text, when present. */
    val sourceName: String? = null,
    /** media:content / media:thumbnail / image enclosure, when present. */
    val imageUrl: String? = null,
    /** Byline (dc:creator / author), when the feed names one. */
    val author: String? = null,
)

/** A parsed feed: its channel title and items. */
data class ParsedFeed(val title: String?, val items: List<RssItem>)
