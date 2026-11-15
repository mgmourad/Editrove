// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.di

import android.content.Context
import news.openwire.data.local.AppearanceStore
import news.openwire.data.local.FeedStateStore
import news.openwire.data.local.LibraryStore
import news.openwire.data.local.OpenWireDatabase
import news.openwire.data.local.PrefsDomainRuleStore
import news.openwire.data.local.PrefsSettingsRepository
import news.openwire.data.local.SettingsRepository
import news.openwire.data.remote.ArchiveChecker
import news.openwire.data.remote.ArticleTextExtractor
import news.openwire.data.remote.AssetNewsDataSource
import news.openwire.data.remote.FallbackNewsRemote
import news.openwire.data.remote.FeedCatalogSource
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.ImageResolver
import news.openwire.data.remote.NewsRemoteDataSource
import news.openwire.data.remote.PublishedMetadata
import news.openwire.data.remote.RssNormalizer
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedRepositoryImpl
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.Strategy

/**
 * Manual dependency container (no Hilt in v1 to keep the AGP 9 scaffold lean).
 * Everything is local: no analytics, no accounts, no backend beyond the static
 * snapshot site and the publishers' own feeds.
 */
class AppContainer(context: Context) {

    private val database: OpenWireDatabase by lazy { OpenWireDatabase.create(context) }

    /** Paywall engine with the user's persisted per-domain overrides. */
    val domainRegistry = DomainRegistry(PrefsDomainRuleStore(context))

    val archiveChecker: ArchiveChecker by lazy { ArchiveChecker() }
    val articleTextExtractor: ArticleTextExtractor by lazy { ArticleTextExtractor() }
    val imageResolver: ImageResolver by lazy { ImageResolver() }

    val settingsRepository: SettingsRepository by lazy { PrefsSettingsRepository(context) }
    val feedCatalog: FeedCatalogSource by lazy { FeedCatalogSource(context) }
    val publishedMetadata: PublishedMetadata by lazy { PublishedMetadata(context, baseUrl = news.openwire.BuildConfig.DATA_BASE_URL) }
    val feedState: FeedStateStore by lazy { FeedStateStore(context) }

    /** Saved + liked stories (local only). */
    val libraryStore: LibraryStore by lazy { LibraryStore(context) }

    /** Black Glass / Editorial Paper preference. */
    val appearanceStore: AppearanceStore by lazy { AppearanceStore(context) }

    val feedRepository: FeedRepository by lazy {
        FeedRepositoryImpl(
            remote = FallbackNewsRemote(primary = NewsRemoteDataSource(baseUrl = news.openwire.BuildConfig.DATA_BASE_URL), fallback = AssetNewsDataSource(context)),
            rssSource = GoogleNewsRssSource(),
            normalizer = RssNormalizer(domainRegistry),
            dao = database.articleDao(),
            registry = domainRegistry,
            settings = { settingsRepository.settings.value },
            catalog = { feedCatalog.catalog },
            metadata = publishedMetadata,
            feedState = feedState,
        )
    }

    /** Archive snapshot URL for a hard-paywalled story, or null when none exists (→ locked). */
    suspend fun archiveFor(article: Article): String? {
        val rule = domainRegistry.getRule(article.domain.orEmpty())
        val result = if (rule.strategy == Strategy.WAYBACK) archiveChecker.checkWayback(article.originalUrl)
        else archiveChecker.checkArchiveIs(article.originalUrl)
        return result.archiveUrl.takeIf { result.available }
    }
}
