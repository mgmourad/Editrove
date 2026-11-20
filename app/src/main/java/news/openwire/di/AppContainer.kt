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
import news.openwire.data.remote.ServiceHealth
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

    internal val database: OpenWireDatabase by lazy { OpenWireDatabase.create(context) }

    /** Paywall engine with the user's persisted per-domain overrides. */
    val domainRegistry = DomainRegistry(PrefsDomainRuleStore(context))

    val archiveChecker: ArchiveChecker by lazy { ArchiveChecker() }
    /** Publisher URLs behind news.google.com links (reader text, photos, briefings). */
    val googleLinks: news.openwire.data.GoogleLinkResolver by lazy { news.openwire.data.GoogleLinkResolver(context) }
    val articleTextExtractor: ArticleTextExtractor by lazy { ArticleTextExtractor(
            resolveLink = { googleLinks.resolve(it) },
            renderPage = { googleLinks.renderForReader(it) },
            isPaywalled = { url -> domainRegistry.getRule(domainRegistry.normalizeDomain(url)).paywall.let { it == "hard" || it == "metered" } },
            diagnostics = true,
        ) }
    val markets: news.openwire.data.remote.MarketDataSource by lazy { news.openwire.data.remote.MarketDataSource() }
    val radioBrowser: news.openwire.data.remote.RadioBrowserSource by lazy { news.openwire.data.remote.RadioBrowserSource() }
    /** Editrove's own downloadable voices. */
    val voices: news.openwire.voice.VoiceStore by lazy { news.openwire.voice.VoiceStore(context) }
    val sourceFinder: news.openwire.data.remote.SourceFinder by lazy { news.openwire.data.remote.SourceFinder() }
    val xPosts: news.openwire.data.remote.XPostSource by lazy { news.openwire.data.remote.XPostSource() }
    val opinion: news.openwire.data.remote.OpinionSource by lazy { news.openwire.data.remote.OpinionSource(GoogleNewsRssSource(), RssNormalizer(domainRegistry)) }
    /** Live TV and radio; starting either pauses the audio briefing. */
    val live: news.openwire.live.LiveController by lazy { news.openwire.live.LiveController(context) { if (briefingPlayer.playing) briefingPlayer.pause() } }
    val logoResolver: news.openwire.data.remote.PublisherLogoResolver by lazy {
        val prefs = context.getSharedPreferences("publisher_logos", android.content.Context.MODE_PRIVATE)
        news.openwire.data.remote.PublisherLogoResolver(load = { prefs.getString(it, null) }, save = { d, u -> prefs.edit().putString(d, u).apply() })
    }
    val imageResolver: ImageResolver by lazy { ImageResolver(resolveLink = { googleLinks.resolve(it) }, renderPage = { googleLinks.render(it) }) }
    val serviceHealth: ServiceHealth by lazy { ServiceHealth() }

    val updateManager: news.openwire.update.UpdateManager by lazy {
        news.openwire.update.UpdateManager(context, settingsRepository, news.openwire.data.remote.ReleaseSource(news.openwire.BuildConfig.UPDATE_REPO))
    }
    /** Audio briefing engine — app-scoped so playback survives leaving the app and locking the phone. */
    val briefingPlayer: news.openwire.ui.home.BriefingPlayer by lazy {
        news.openwire.ui.home.BriefingPlayer(
            context, prefs = { settingsRepository.settings.value.briefing }, extractor = articleTextExtractor,
            normalizeDomain = { domainRegistry.normalizeDomain(it) },
            storyContext = { stories -> briefingContext(stories) },
            country = { settingsRepository.settings.value.edition.country },
        )
            .also { news.openwire.widget.WidgetSync.attach(context, it) }
    }
    val changelog: news.openwire.update.Changelog by lazy { news.openwire.update.Changelog(context) }

    /** Paywall routing with service health checks and fallbacks. */
    val articleRouter: news.openwire.ui.home.ArticleRouter by lazy {
        news.openwire.ui.home.ArticleRouter(domainRegistry, archiveChecker, serviceHealth) { settingsRepository.settings.value.disabledServices }
    }

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
            interests = { libraryStore.interestDomains() },
            onStored = { news.openwire.widget.WidgetSync.feedsChanged(context) },
            resolveLink = { googleLinks.resolve(it) },
        )
    }

    /** Archive snapshot URL for a hard-paywalled story, or null when none exists (→ locked). */
    suspend fun archiveFor(article: Article): String? {
        val rule = domainRegistry.getRule(article.domain.orEmpty())
        val result = if (rule.strategy == Strategy.WAYBACK) archiveChecker.checkWayback(article.originalUrl)
        else archiveChecker.checkArchiveIs(article.originalUrl)
        return result.archiveUrl.takeIf { result.available }
    }

    /**
     * Where each briefing story sits in the reader's edition, from the tabs that carry it: the
     * local, national or international tab (scope) and a section tab (category). Only what the
     * edition itself says, so the presenter never guesses.
     */
    private suspend fun briefingContext(stories: List<news.openwire.domain.model.Article>): Map<String, Pair<news.openwire.domain.engine.BriefingScript.Scope?, news.openwire.domain.engine.BriefingScript.Category?>> {
        val s = settingsRepository.settings.value
        val urls = stories.mapNotNull { it.originalUrl }.toSet()
        if (urls.isEmpty()) return emptyMap()
        val scopes = HashMap<String, news.openwire.domain.engine.BriefingScript.Scope>()
        val cats = HashMap<String, news.openwire.domain.engine.BriefingScript.Category>()
        val priority = listOf(news.openwire.domain.engine.TopicRegistry.LOCAL_ID, news.openwire.domain.engine.TopicRegistry.NATIONAL_ID, "INTERNATIONAL")
        val topics = news.openwire.domain.engine.TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)
            .filter { it.isVisible }
            .sortedBy { t -> priority.indexOf(t.id).let { if (it < 0) priority.size else it } }
        for (t in topics) {
            val sc = news.openwire.domain.engine.BriefingPlan.scopeOf(t.id)
            val ca = news.openwire.domain.engine.BriefingPlan.categoryOf(t.id)
            if (sc == null && ca == null) continue
            for (a in feedRepository.cachedArticles(t.id)) {
                val u = a.originalUrl ?: continue
                if (u !in urls) continue
                if (sc != null) scopes.putIfAbsent(u, sc)
                if (ca != null) cats.putIfAbsent(u, ca)
            }
        }
        return urls.associateWith { scopes[it] to cats[it] }
    }
}
