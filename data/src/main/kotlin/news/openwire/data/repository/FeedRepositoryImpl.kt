// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import news.openwire.data.local.ArticleDao
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.NewsRemote
import news.openwire.data.remote.RssNormalizer
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.SortDirection
import news.openwire.domain.model.Topic

class FeedRepositoryImpl(
    private val remote: NewsRemote,
    private val rssSource: GoogleNewsRssSource,
    private val normalizer: RssNormalizer,
    private val dao: ArticleDao,
) : FeedRepository {

    override fun observeTopic(topic: Topic): Flow<FeedResult> = flow {
        val liveUrl = topic.liveUrl
        if (topic.liveOnly && liveUrl != null) {
            observeLive(topic, liveUrl)
            return@flow
        }
        observeSnapshot(topic)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<FeedResult>.observeLive(topic: Topic, liveUrl: String) {
        emit(FeedResult.Loading)
        try {
            val items = rssSource.fetch(liveUrl)
            val articles = items.mapIndexed { index, item -> normalizer.normalize(item, topic.id, index) }
            val processed = NewsProcessor.deduplicate(NewsProcessor.sortByDate(articles, SortDirection.NEWEST))
            dao.upsertAll(processed.map { it.toEntity(topic.id) })
            if (processed.isEmpty()) emit(FeedResult.Empty(topic.id)) else emit(FeedResult.Success(processed, fromCache = false))
        } catch (e: Exception) {
            Log.w("OpenWire", "Live observe ${topic.id} failed", e)
            emit(FeedResult.Error(e.message ?: "Network error"))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<FeedResult>.observeSnapshot(topic: Topic) {
        val file = topic.snapshotFile
        if (file == null) {
            emit(FeedResult.Empty(topic.id))
            return
        }
        emit(FeedResult.Loading)

        val cached = NewsProcessor.deduplicate(
            NewsProcessor.sortByDate(dao.getByTopic(topic.id).map { it.toArticle() }, SortDirection.NEWEST),
        )
        if (cached.isNotEmpty()) {
            emit(FeedResult.Success(cached, fromCache = true))
        }

        try {
            val fresh = NewsProcessor.deduplicate(
                NewsProcessor.sortByDate(
                    remote.fetchTopic(file).map { it.toArticle() },
                    SortDirection.NEWEST,
                ),
            )
            dao.upsertAll(fresh.map { it.toEntity(topic.id) })
            emit(FeedResult.Success(fresh, fromCache = false))
        } catch (e: Exception) {
            Log.w("OpenWire", "Observe topic ${topic.id} failed", e)
            if (cached.isEmpty()) {
                emit(FeedResult.Error(e.message ?: "Network error"))
            }
        }
    }
}
