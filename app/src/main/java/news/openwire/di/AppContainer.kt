package news.openwire.di

import android.content.Context
import news.openwire.data.local.OpenWireDatabase
import news.openwire.data.remote.ArchiveChecker
import news.openwire.data.remote.ArticleTextExtractor
import news.openwire.data.remote.AssetNewsDataSource
import news.openwire.data.remote.FallbackNewsRemote
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.NewsRemoteDataSource
import news.openwire.data.remote.RssNormalizer
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedRepositoryImpl
import news.openwire.domain.engine.DomainRegistry

/**
 * Manual dependency container (no Hilt in v1 to keep the AGP 9 scaffold lean).
 * Builds the Room database, the feed repository, and the paywall router deps.
 */
class AppContainer(context: Context) {

    private val database: OpenWireDatabase by lazy {
        OpenWireDatabase.create(context)
    }

    val domainRegistry = DomainRegistry()

    val archiveChecker: ArchiveChecker by lazy {
        ArchiveChecker()
    }

    val articleTextExtractor: ArticleTextExtractor by lazy {
        ArticleTextExtractor()
    }

    val feedRepository: FeedRepository by lazy {
        val remote = FallbackNewsRemote(
            primary = NewsRemoteDataSource(),
            fallback = AssetNewsDataSource(context),
        )
        FeedRepositoryImpl(
            remote = remote,
            rssSource = GoogleNewsRssSource(),
            normalizer = RssNormalizer(domainRegistry),
            dao = database.articleDao(),
        )
    }
}
