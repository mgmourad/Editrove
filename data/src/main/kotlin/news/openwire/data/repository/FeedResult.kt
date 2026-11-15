// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import news.openwire.domain.model.Article

sealed interface FeedResult {
    data object Loading : FeedResult
    data class Success(
        val articles: List<Article>,
        val fromCache: Boolean,
        /** When this copy was fetched (epoch ms), 0 when unknown. */
        val updatedAt: Long = 0L,
        /** Direct sources that failed this refresh (the rest still loaded). */
        val failedSources: List<String> = emptyList(),
        /** A refresh is still running behind this cached copy. */
        val refreshing: Boolean = false,
    ) : FeedResult
    data class Error(val message: String) : FeedResult
    data class Empty(val topicId: String) : FeedResult
}

/** Result of checking a source feed ("check enabled feeds" audit). */
data class FeedCheck(
    val ok: Boolean,
    val count: Int = 0,
    val title: String? = null,
    val newestAt: Long? = null,
    val stale: Boolean = false,
    val error: String? = null,
)
