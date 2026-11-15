// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

/** A parsed RSS/Atom item, before normalization. */
data class RssItem(
    val title: String,
    val link: String,
    val pubDate: String?,
    val description: String?,
    val source: String?,
)
