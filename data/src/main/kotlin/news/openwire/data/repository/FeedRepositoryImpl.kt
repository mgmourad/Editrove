package news.openwire.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import news.openwire.data.local.ArticleDao
import news.openwire.data.remote.NewsRemote
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.SortDirection
import news.openwire.domain.model.Topic

class FeedRepositoryImpl(
    private val remote: NewsRemote,
    private val dao: ArticleDao,
) : FeedRepository {

    override fun observeTopic(topic: Topic): Flow<FeedResult> = flow {
        val file = topic.snapshotFile
        if (topic.liveOnly || file == null) {
            emit(FeedResult.Empty(topic.id))
            return@flow
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
            if (cached.isEmpty()) {
                emit(FeedResult.Error(e.message ?: "Network error"))
            }
        }
    }
}
