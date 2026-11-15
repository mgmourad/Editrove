package news.openwire.data.repository

import news.openwire.domain.model.Article

sealed interface FeedResult {
    data object Loading : FeedResult
    data class Success(val articles: List<Article>, val fromCache: Boolean) : FeedResult
    data class Error(val message: String) : FeedResult
    data class Empty(val topicId: String) : FeedResult
}
