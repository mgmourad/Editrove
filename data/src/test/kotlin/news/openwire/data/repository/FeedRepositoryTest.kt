// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import news.openwire.data.local.ArticleDao
import news.openwire.data.local.ArticleEntity
import news.openwire.data.remote.ArticleDto
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.NewsRemote
import news.openwire.data.remote.RssNormalizer
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FeedRepositoryTest {

    private class FakeNewsRemote(
        private val articles: List<ArticleDto> = emptyList(),
        private val error: Boolean = false,
    ) : NewsRemote {
        override suspend fun fetchTopic(file: String): List<ArticleDto> {
            if (error) throw IOException("boom")
            return articles
        }
    }

    private class FakeArticleDao : ArticleDao {
        val store = mutableMapOf<String, List<ArticleEntity>>()
        override suspend fun getByTopic(topicId: String): List<ArticleEntity> = store[topicId] ?: emptyList()
        override suspend fun upsertAll(articles: List<ArticleEntity>) {
            if (articles.isNotEmpty()) store[articles.first().topicId] = articles
        }
        override suspend fun deleteTopic(topicId: String) { store.remove(topicId) }
    }

    private fun repo(remote: NewsRemote, dao: ArticleDao = FakeArticleDao()) =
        FeedRepositoryImpl(remote, GoogleNewsRssSource(), RssNormalizer(DomainRegistry()), dao)

    private fun topic(snapshotFile: String? = "news_top.json", liveOnly: Boolean = false) =
        Topic(id = "TOP", label = "Top Stories", snapshotFile = snapshotFile, liveOnly = liveOnly)

    @Test
    fun missingOptionalFieldsMapToNullsWithoutCrashing() {
        val article = ArticleDto(id = "a", title = "t").toArticle()
        assertNull(article.imageUrl)
        assertNull(article.archiveUrl)
        assertNull(article.badge)
        assertNull(article.source)
    }

    @Test
    fun unparseablePubDateYieldsNullPublishedAt() {
        assertNull(ArticleDto(id = "a", title = "t", pubDate = "garbage").toArticle().publishedAt)
    }

    @Test
    fun repositoryDedupsAndSortsFetchedArticles() = runTest {
        val remote = FakeNewsRemote(
            listOf(
                ArticleDto(id = "a", title = "Same story", pubDate = "2026-09-14T10:00:00Z"),
                ArticleDto(id = "b", title = "Same story!", pubDate = "2026-09-14T10:00:00Z"),
                ArticleDto(id = "c", title = "Other story", pubDate = "2026-09-14T12:00:00Z"),
            ),
        )
        val results = repo(remote).observeTopic(topic()).toList()
        val last = results.last() as FeedResult.Success
        assertEquals(2, last.articles.size)
        assertEquals("c", last.articles[0].id)
    }

    @Test
    fun networkErrorWithEmptyCacheSurfacesError() = runTest {
        val results = repo(FakeNewsRemote(error = true)).observeTopic(topic()).toList()
        assertTrue(results.last() is FeedResult.Error)
    }

    @Test
    fun cachedCopyIsServedWhenRefreshFails() = runTest {
        val dao = FakeArticleDao()
        repo(FakeNewsRemote(listOf(ArticleDto(id = "a", title = "Cached story"))), dao).observeTopic(topic()).toList()
        val results = repo(FakeNewsRemote(error = true), dao).observeTopic(topic()).toList()
        val last = results.last() as FeedResult.Success
        assertTrue(last.fromCache)
        assertEquals("Cached story", last.articles.single().title)
    }

    @Test
    fun liveOnlyTopicReturnsEmpty() = runTest {
        val results = repo(FakeNewsRemote()).observeTopic(topic(snapshotFile = null, liveOnly = true)).toList()
        assertTrue(results.last() is FeedResult.Empty)
    }
}
