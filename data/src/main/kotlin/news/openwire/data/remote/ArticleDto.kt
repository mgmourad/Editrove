// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.serialization.Serializable

/** Wire format of a snapshot article (matches data/news_*.json). */
@Serializable
data class ArticleDto(
    val id: String,
    val title: String,
    val source: String? = null,
    val domain: String? = null,
    val originalUrl: String? = null,
    val pubDate: String? = null,
    val status: String? = null,
    val badge: String? = null,
    val targetUrl: String? = null,
    val archiveUrl: String? = null,
    val imageUrl: String? = null,
)
