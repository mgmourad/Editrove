// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import news.openwire.data.local.ArticleDao
import news.openwire.data.local.FeedStateStore
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.MetadataProvider
import news.openwire.data.remote.NewsRemote
import news.openwire.data.remote.RssNormalizer
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.FeedWeaver
import news.openwire.domain.engine.GoogleNewsUrls
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.PreviewMatcher
import news.openwire.domain.engine.PublisherUrlResolver
import news.openwire.domain.engine.SortDirection
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.model.Article
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

/**
 * Feed assembly, mirroring js/newsService.js `fetchTopHeadlines`:
 *
 * - **Snapshot path** — untouched built-in topics read the pipeline's published JSON
 *   (already woven server-side), cache-first.
 * - **Live path** — edition topics, custom topics and built-ins whose sources the user
 *   changed fetch Google News + every assigned direct feed (≤4 at a time), apply
 *   keyword matching and preview excerpts, weave them fairly, resolve Google News
 *   wrappers to publisher URLs and route each story through the paywall engine.
 */
class FeedRepositoryImpl(
    private val remote: NewsRemote,
    private val rssSource: GoogleNewsRssSource,
    private val normalizer: RssNormalizer,
    private val dao: ArticleDao,
    private val registry: DomainRegistry = DomainRegistry(),
    private val settings: () -> UserSettings = { UserSettings() },
    private val catalog: () -> List<FeedSource> = { emptyList() },
    private val metadata: MetadataProvider? = null,
    private val feedState: FeedStateStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : FeedRepository {

    private val norm: (String) -> String = { registry.normalizeDomain(it) }

    override fun observeTopic(topic: Topic, force: Boolean): Flow<FeedResult> = flow {
        val s = settings()
        val live = topic.liveOnly || topic.snapshotFile == null ||
            SourceLibrary.isCustomized(topic, catalog(), s.customFeeds, s.disabledSources)
        if (live) observeLive(topic, s) else observeSnapshot(topic)
    }.flowOn(Dispatchers.Default) // parsing, weaving and routing stay off the main thread

    private suspend fun cached(topicId: String): List<Article> =
        runCatching { dao.getByTopic(topicId).map { it.toArticle() } }.getOrDefault(emptyList())

    private suspend fun store(topicId: String, articles: List<Article>) {
        runCatching { dao.replaceTopic(topicId, articles.mapIndexed { i, a -> a.toEntity(topicId, i) }) }
        feedState?.markUpdated(topicId, clock())
    }

    private suspend fun FlowCollector<FeedResult>.observeSnapshot(topic: Topic) {
        val file = topic.snapshotFile ?: return emit(FeedResult.Empty(topic.id))
        emit(FeedResult.Loading)
        val cached = NewsProcessor.deduplicate(NewsProcessor.sortByDate(cached(topic.id), SortDirection.NEWEST))
        if (cached.isNotEmpty()) emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L, refreshing = true))
        try {
            val fresh = NewsProcessor.deduplicate(
                NewsProcessor.sortByDate(remote.fetchTopic(file).map { it.toArticle() }, SortDirection.NEWEST),
            )
            store(topic.id, fresh)
            emit(FeedResult.Success(fresh, fromCache = false, updatedAt = clock()))
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot ${topic.id} failed", e)
            if (cached.isEmpty()) emit(FeedResult.Error(e.message ?: "Network error"))
            else emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L))
        }
    }

    private data class Fetched(val source: FeedSource, val articles: List<Article>?, val error: String?)

    private suspend fun FlowCollector<FeedResult>.observeLive(topic: Topic, s: UserSettings) {
        emit(FeedResult.Loading)
        val cached = cached(topic.id)
        if (cached.isNotEmpty()) emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L, refreshing = true))

        val primaryUrl = topic.liveUrl?.takeIf { "google_news" !in s.disabledSources || !it.contains("news.google.com") }
        val feeds = SourceLibrary.feedsForTopic(topic, catalog(), s.customFeeds, s.disabledSources)
        val configs = buildList {
            if (primaryUrl != null) add(SourceLibrary.GOOGLE_NEWS.copy(url = primaryUrl, name = if (primaryUrl.contains("news.google.com")) "Google News" else topic.label))
            addAll(feeds)
        }.distinctBy { it.url }
        if (configs.isEmpty()) {
            if (cached.isEmpty()) emit(FeedResult.Empty(topic.id))
            return
        }

        val gate = Semaphore(4)
        val results = coroutineScope {
            configs.mapIndexed { i, cfg ->
                async {
                    gate.withPermit {
                        try {
                            val feed = rssSource.fetchFeed(cfg.url)
                            val items = feed.items.mapIndexed { idx, item ->
                                normalizer.normalize(
                                    item, topic.id, idx,
                                    feedName = if (cfg.isPrimary) null else cfg.name,
                                    feedUrl = if (cfg.isPrimary) null else cfg.url,
                                    idPrefix = if (cfg.isPrimary) "p" else "f$i",
                                )
                            }
                            Fetched(cfg, items, null)
                        } catch (e: Exception) {
                            Fetched(cfg, null, e.message ?: "Unavailable")
                        }
                    }
                }
            }.awaitAll()
        }

        if (results.all { it.articles == null }) {
            val msg = "Sources are unavailable right now. ${results.firstOrNull()?.error.orEmpty()}".trim()
            if (cached.isEmpty()) emit(FeedResult.Error(msg))
            else emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L, failedSources = results.map { it.source.name }))
            return
        }

        val previews = if (topic.keywords.isNotEmpty()) metadata?.previews().orEmpty() else emptyList()
        val prepared = results.map { r ->
            var list = r.articles ?: return@map r
            if (topic.keywords.isNotEmpty()) {
                list = PreviewMatcher.enrichPreviews(list, previews)
                // Google search already honours the query; publisher feeds are matched locally.
                if (!(r.source.isPrimary && r.source.url.contains("news.google.com"))) list = PreviewMatcher.filterKeywordArticles(list, topic.keywords)
            }
            r.copy(articles = list)
        }
        val primary = prepared.firstOrNull { it.source.isPrimary }?.articles.orEmpty()
        val groups = prepared.filter { !it.source.isPrimary && !it.articles.isNullOrEmpty() }.map { FeedWeaver.Group(it.source, it.articles!!) }
        val isLatest = topic.id == "LATEST"
        var woven = FeedWeaver.weave(primary, groups, norm, FeedWeaver.Options(leadAnchorCount = if (isLatest) 0 else 2))

        // Carry images/excerpts over from the previous copy of the same stories.
        if (cached.isNotEmpty()) woven = woven.map { a ->
            val match = cached.firstOrNull { FeedWeaver.isDuplicate(a, it, norm) } ?: return@map a
            a.copy(imageUrl = a.imageUrl ?: match.imageUrl, summary = a.summary ?: match.summary)
        }

        // Resolve Google News wrappers to publisher links, then route through the paywall engine.
        val publisherMap = if (woven.any { GoogleNewsUrls.isGoogleNewsUrl(it.originalUrl) }) metadata?.publisherUrls().orEmpty() else emptyMap()
        woven = woven.map { a ->
            if (!GoogleNewsUrls.isGoogleNewsUrl(a.originalUrl)) a else {
                val resolved = PublisherUrlResolver.resolve(a.originalUrl, publisherMap)
                if (resolved.isEmpty()) a else StrategyRouter.applyStrategy(registry, a.copy(originalUrl = resolved, domain = registry.normalizeDomain(resolved)))
            }
        }
        if (isLatest) woven = NewsProcessor.sortByDate(woven, SortDirection.NEWEST)

        if (woven.isEmpty()) {
            if (cached.isEmpty()) emit(FeedResult.Empty(topic.id))
            return
        }
        store(topic.id, woven)
        emit(FeedResult.Success(woven, fromCache = false, updatedAt = clock(), failedSources = results.filter { it.error != null }.map { it.source.name }))
    }

    override suspend fun verifyFeed(url: String): FeedCheck = try {
        SourceLibrary.validateFeedUrl(url)
        val feed = rssSource.fetchFeed(url)
        val dates = feed.items.mapNotNull { NewsProcessor.parseDate(it.pubDate) }
        val newest = dates.maxOrNull()
        FeedCheck(
            ok = feed.items.isNotEmpty(),
            count = feed.items.size,
            title = feed.title,
            newestAt = newest,
            stale = newest != null && clock() - newest > 7L * 24 * 60 * 60 * 1000,
            error = if (feed.items.isEmpty()) "The feed has no stories." else null,
        )
    } catch (e: Exception) {
        FeedCheck(ok = false, error = e.message ?: "Unable to retrieve headlines.")
    }

    private companion object { const val TAG = "Editrove" }
}
