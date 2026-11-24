// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import kotlinx.coroutines.flow.Flow
import news.openwire.domain.model.Topic

/** Observes a topic's feed (cache-first, then network). */
interface FeedRepository {
    /** @param force skip nothing: always hit the network after emitting the cached copy. */
    fun observeTopic(topic: Topic, force: Boolean = false): Flow<FeedResult>

    /** Fetches a source with the same path the feed uses (library audit / add-source validation). */
    suspend fun verifyFeed(url: String): FeedCheck = FeedCheck(ok = false, error = "Unsupported")

    /** Each refresh's sources as they answer (pull-to-refresh progress). Never slows the fetch. */
    val refreshEvents: kotlinx.coroutines.flow.SharedFlow<RefreshEvent> get() = NO_EVENTS

    /** The stories last stored for a topic (no network). */
    suspend fun cachedArticles(topicId: String): List<news.openwire.domain.model.Article> = emptyList()
}

private val NO_EVENTS = kotlinx.coroutines.flow.MutableSharedFlow<RefreshEvent>()
