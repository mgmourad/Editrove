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
import java.util.concurrent.ConcurrentHashMap
import news.openwire.data.local.ArticleDao
import news.openwire.data.local.FeedStateStore
import news.openwire.data.remote.GoogleNewsRssSource
import news.openwire.data.remote.MetadataProvider
import news.openwire.data.remote.NewsRemote
import news.openwire.data.remote.RssNormalizer
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.FeedRanker
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
    /** Outlets of the reader's saved/liked stories (local only), for Curated tuning. */
    private val interests: () -> List<String> = { emptyList() },
    /** A topic's cache was rewritten (home-screen widgets redraw). */
    private val onStored: (String) -> Unit = {},
    /** news.google.com wrapper → the publisher's own URL (null if it can't be found). */
    private val resolveLink: suspend (String) -> String? = { null },
) : FeedRepository {

    private val norm: (String) -> String = { registry.normalizeDomain(it) }

    private val reporter = RefreshReporter()
    override val refreshEvents get() = reporter.events

    private fun feedRef(cfg: FeedSource) = SourceRef(
        cfg.url,
        cfg.name,
        if (cfg.isPrimary && cfg.url.contains("/rss/search")) SourceRef.Kind.SEARCH else SourceRef.Kind.FEED,
    )
    private fun editionRef(file: String) = SourceRef("snapshot:$file", "", SourceRef.Kind.EDITION)

    /** Latest copy of every topic loaded this session: the pool used to measure cross-outlet coverage. */
    private val pool = ConcurrentHashMap<String, List<Article>>()

    /**
     * Curated order: editorial prominence + coverage across every loaded topic + freshness
     * + source authority + section fit, then diversified. See [FeedRanker].
     */
    private fun rank(topic: Topic, articles: List<Article>): List<Article> {
        pool[topic.id] = articles
        val categories = catalog().associate { it.url to it.category }
        val related = FeedRanker.relatedCoverage(pool.values.flatten(), norm)
        val ctx = FeedRanker.Context(
            topicId = topic.id,
            nowMillis = clock(),
            coverageOf = { related(it).size + 1 },
            feedCategory = { categories[it] },
            affinity = if (settings().personalize) FeedRanker.affinityFrom(interests(), norm) else { _ -> 0.0 },
            normalizeDomain = norm,
        )
        return FeedRanker.rank(articles, ctx).map { a -> a.copy(alsoCoveredBy = related(a).map { it.source }) }
    }

    override fun observeTopic(topic: Topic, force: Boolean): Flow<FeedResult> = flow {
        val s = settings()
        // Built on the phone whenever the topic has a live source; the published snapshot is
        // the first paint and the fallback, so freshness never depends on a server.
        val live = topic.liveOnly || topic.snapshotFile == null || topic.liveUrl != null ||
            SourceLibrary.isCustomized(topic, catalog(), s.customFeeds, s.disabledSources)
        if (live) observeLive(topic, s) else observeSnapshot(topic)
    }.flowOn(Dispatchers.Default) // parsing, weaving and routing stay off the main thread

    override suspend fun cachedArticles(topicId: String): List<Article> = cached(topicId)

    private suspend fun cached(topicId: String): List<Article> =
        runCatching { dao.getByTopic(topicId).map { it.toArticle() } }.getOrDefault(emptyList())

    /**
     * Swaps Google News wrapper links for the publisher's own (and routes them around the
     * paywall), so the reader, photos, "Continue on…" and briefings all use the real article.
     */
    private suspend fun withPublisherLinks(articles: List<Article>): List<Article> {
        val wrapped = articles.take(RESOLVE_LIMIT).filter { GoogleNewsUrls.isGoogleNewsUrl(it.originalUrl) }
        if (wrapped.isEmpty()) return articles
        val found = coroutineScope {
            wrapped.map { a -> async { a.id to runCatching { resolveLink(a.originalUrl!!) }.getOrNull() } }.awaitAll()
        }.toMap()
        if (found.values.all { it == null }) return articles
        return articles.map { a ->
            val url = found[a.id] ?: return@map a
            if (GoogleNewsUrls.isGoogleNewsUrl(url)) a
            else StrategyRouter.applyStrategy(registry, a.copy(originalUrl = url, domain = registry.normalizeDomain(url).ifEmpty { a.domain }, targetUrl = null, badge = null, status = null))
        }
    }

    /** Emits [list] again once its Google links are swapped for publisher links (if any changed). */
    private suspend fun FlowCollector<FeedResult>.resolveAndEmit(topicId: String, list: List<Article>, updatedAt: Long, failed: List<String> = emptyList()) {
        val resolved = withPublisherLinks(list)
        if (resolved != list) {
            store(topicId, resolved)
            emit(FeedResult.Success(resolved, fromCache = false, updatedAt = updatedAt, failedSources = failed))
        }
    }

    private suspend fun store(topicId: String, articles: List<Article>) {
        runCatching { dao.replaceTopic(topicId, articles.mapIndexed { i, a -> a.toEntity(topicId, i) }) }
        feedState?.markUpdated(topicId, clock())
        runCatching { onStored(topicId) }
    }

    private suspend fun FlowCollector<FeedResult>.observeSnapshot(topic: Topic) {
        val file = topic.snapshotFile ?: return emit(FeedResult.Empty(topic.id))
        emit(FeedResult.Loading)
        // The cache is stored in curated order already; keep it (re-sorting by date was what
        // let a minutes-old sports item displace the day's lead story).
        val cached = NewsProcessor.deduplicate(cached(topic.id)).also { if (it.isNotEmpty()) pool.putIfAbsent(topic.id, it) }
        if (cached.isNotEmpty()) emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L, refreshing = true))
        val rid = reporter.start(topic.id, listOf(editionRef(file)) + extrasFor(topic).map(::feedRef))
        try {
            // The snapshot arrives in the pipeline's editorial order; that order is a signal, not noise.
            val published = NewsProcessor.deduplicate(reporter.time(rid, editionRef(file)) { remote.fetchTopic(file) }.mapIndexed { i, dto -> dto.toArticle(rank = i) })
            val fresh = rank(topic, published)
            emit(FeedResult.Success(fresh, fromCache = false, updatedAt = clock(), refreshing = extrasFor(topic).isNotEmpty()))
            val woven = weaveExtras(topic, published, rid)
            val ranked = if (woven === published) fresh else rank(topic, woven)
            store(topic.id, ranked)
            if (ranked !== fresh) emit(FeedResult.Success(ranked, fromCache = false, updatedAt = clock()))
            resolveAndEmit(topic.id, ranked, clock())
        } catch (e: Exception) {
            Log.w(TAG, "Snapshot ${topic.id} failed", e)
            if (cached.isEmpty()) emit(FeedResult.Error(e.message ?: "Network error"))
            else emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L))
        } finally {
            reporter.finish(rid, topic.id)
        }
    }

    /** Editrove-added sources for a snapshot topic (the published snapshot doesn't carry them). */
    private fun extrasFor(topic: Topic): List<FeedSource> {
        val s = settings()
        return SourceLibrary.feedsForTopic(topic, catalog(), emptyList(), s.disabledSources).filter { it.extra }
    }

    /** Weaves Editrove-added sources into a published snapshot, fairly and deduplicated. */
    private suspend fun weaveExtras(topic: Topic, snapshot: List<Article>, rid: Long = 0): List<Article> {
        val extras = extrasFor(topic)
        if (extras.isEmpty()) return snapshot
        val gate = Semaphore(4)
        val groups = coroutineScope {
            extras.mapIndexed { i, cfg ->
                async {
                    gate.withPermit {
                        runCatching {
                            reporter.time(rid, feedRef(cfg)) { rssSource.fetchFeed(cfg.url) }.items.take(cfg.maxArticles + 4).mapIndexed { idx, item ->
                                normalizer.normalize(item, topic.id, idx, feedName = cfg.name, feedUrl = cfg.url, idPrefix = "x$i")
                            }
                        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { FeedWeaver.Group(cfg, it) }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (groups.isEmpty()) return snapshot
        val woven = FeedWeaver.weave(snapshot, groups, norm, FeedWeaver.Options(leadAnchorCount = if (topic.id == "LATEST") 0 else 2, maxTotalArticles = 80))
        return if (topic.id == "LATEST") NewsProcessor.sortByDate(woven, SortDirection.NEWEST) else woven
    }

    private data class Fetched(val source: FeedSource, val articles: List<Article>?, val error: String?)

    private suspend fun FlowCollector<FeedResult>.observeLive(topic: Topic, s: UserSettings) {
        emit(FeedResult.Loading)
        val cached = cached(topic.id)
        if (cached.isNotEmpty()) emit(FeedResult.Success(cached, fromCache = true, updatedAt = feedState?.lastUpdated(topic.id) ?: 0L, refreshing = true))

        val primaryUrl = topic.liveUrl?.takeIf { "google_news" !in s.disabledSources || !it.contains("news.google.com") }
        // A topic read in another language (Arabic, French…) keeps to that language: the built-in
        // catalog and the published snapshot are English, so only the reader's own sources join it.
        val feedLang = news.openwire.domain.engine.Languages.feedLanguage(topic.liveUrl)
        val foreign = feedLang?.let { it != "en" } == true
        val feeds = SourceLibrary.feedsForTopic(topic, catalog(), s.customFeeds, s.disabledSources).filter { !foreign || it.isCustom }
        val configs = buildList {
            if (primaryUrl != null) add(SourceLibrary.GOOGLE_NEWS.copy(url = primaryUrl, name = if (primaryUrl.contains("news.google.com")) "Google News" else topic.label))
            addAll(feeds)
        }.distinctBy { it.url }
        // A built-in topic whose sources were edited still leads with its published front page,
        // minus stories from the sources the reader switched off.
        val snapshotOk = topic.snapshotFile != null && !topic.isCustom && !foreign
        val loadsEditionFirst = snapshotOk && (primaryUrl == null || cached.isEmpty())
        val rid = reporter.start(topic.id, (if (loadsEditionFirst) listOf(editionRef(topic.snapshotFile!!)) else emptyList()) + configs.map(::feedRef))
        try { observeLiveFetch(topic, s, cached, primaryUrl, feedLang, configs, snapshotOk, loadsEditionFirst, rid) } finally { reporter.finish(rid, topic.id) }
    }

    private suspend fun FlowCollector<FeedResult>.observeLiveFetch(
        topic: Topic, s: UserSettings, cached: List<Article>, primaryUrl: String?, feedLang: String?,
        configs: List<FeedSource>, snapshotOk: Boolean, loadsEditionFirst: Boolean, rid: Long,
    ) {
        suspend fun loadPublished(): List<Article> =
            // Without the stories of switched-off feeds (the snapshot records each story's feed).
            runCatching { reporter.time(rid, editionRef(topic.snapshotFile!!)) { remote.fetchTopic(topic.snapshotFile!!) }.mapIndexed { i, d -> d.toArticle(rank = i) } }.getOrDefault(emptyList())
                .filter { a -> a.feedUrl == null || SourceLibrary.sourceKey(a.feedUrl) !in s.disabledSources }
        var published = if (loadsEditionFirst) loadPublished() else emptyList()
        // First launch: show the published front page at once while the live one is built.
        if (cached.isEmpty() && primaryUrl != null && published.isNotEmpty()) {
            emit(FeedResult.Success(rank(topic, NewsProcessor.deduplicate(published)), fromCache = true, updatedAt = clock(), refreshing = true))
        }
        if (configs.isEmpty() && published.isEmpty()) {
            if (cached.isEmpty()) emit(FeedResult.Empty(topic.id))
            return
        }

        val gate = Semaphore(4)
        val results = coroutineScope {
            configs.mapIndexed { i, cfg ->
                async {
                    gate.withPermit {
                        try {
                            val feed = reporter.time(rid, feedRef(cfg)) { rssSource.fetchFeed(cfg.url) }
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

        if (published.isEmpty() && results.all { it.articles == null }) {
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
            // A news topic never shows week-old stories: some feeds stopped updating years ago
            // and still serve their last items (CNN's rss.cnn.com, frozen since 2023).
            val cutoff = clock() - STALE_MS
            list = list.filter { a -> (a.publishedAt ?: Long.MAX_VALUE) >= cutoff || (topic.isCustom && topic.keywords.isEmpty()) }
            // An Arabic topic keeps to Arabic headlines (a source added for an English topic doesn't leak in).
            if (feedLang == "ar" && !r.source.isPrimary) list = list.filter { RX_ARABIC.containsMatchIn(it.title) }
            r.copy(articles = list)
        }
        // Google unavailable (or switched off): the published front page leads instead.
        val primary = prepared.firstOrNull { it.source.isPrimary }?.articles.orEmpty().ifEmpty {
            if (published.isEmpty() && snapshotOk) published = loadPublished()
            published
        }
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
        woven = rank(topic, woven) // LATEST stays chronological inside the ranker

        if (woven.isEmpty()) {
            if (cached.isEmpty()) emit(FeedResult.Empty(topic.id))
            return
        }
        store(topic.id, woven)
        val failedNames = results.filter { it.error != null }.map { it.source.name }
        emit(FeedResult.Success(woven, fromCache = false, updatedAt = clock(), failedSources = failedNames))
        resolveAndEmit(topic.id, woven, clock(), failedNames)
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

    private companion object {
        const val TAG = "Editrove"
        /** Google links resolved per refresh (the stories people actually see). */
        const val RESOLVE_LIMIT = 40
    }
}

private const val STALE_MS = 7L * 24 * 60 * 60 * 1000
private val RX_ARABIC = Regex("[\\u0600-\\u06FF]")
