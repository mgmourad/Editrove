// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import news.openwire.i18n.tr
import news.openwire.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import news.openwire.data.local.Appearance
import news.openwire.data.local.Library
import news.openwire.data.local.SettingsRepository
import news.openwire.data.local.ThemeMode
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.SharedUrl
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

enum class NavTab(@androidx.annotation.StringRes private val res: Int) {
    TODAY(R.string.tab_today), DISCOVER(R.string.tab_discover), AUDIO(R.string.tab_audio), LIVE(R.string.tab_live), OPINION(R.string.tab_opinion), SAVED(R.string.tab_saved);

    val label: String get() = tr(res)
}

/** Hard-paywall archive availability for a story (SPEC §6.2 grey-out). */
sealed interface ArchiveState {
    data object Checking : ArchiveState
    data class Available(val url: String) : ArchiveState
    data object Locked : ArchiveState
}

/**
 * Screen state for the adaptive home: nav tab, topic (from the user's ordered,
 * visible topics), per-topic feeds and refresh, the selected story, archive
 * lock state, and the local library.
 */
class HomeViewModel(
    private val repository: FeedRepository,
    private val library: Library,
    private val appearance: Appearance,
    private val settingsRepo: SettingsRepository,
    private val registry: DomainRegistry,
    /** Returns an archive snapshot URL for a hard-paywalled story, or null when none exists. */
    private val archiveLookup: suspend (Article) -> String?,
    /** news.google.com wrapper → publisher URL (shared or pasted Google News links). */
    private val resolveLink: suspend (String) -> String? = { null },
    /** A tab's news Shorts (Top Stories: every flavour). */
    private val loadVideos: suspend (String) -> List<news.openwire.domain.video.ShortVideo> = { emptyList() },
) : ViewModel() {

    private val _videos = MutableStateFlow<Map<String, List<news.openwire.domain.video.ShortVideo>>>(emptyMap())
    val videos: StateFlow<Map<String, List<news.openwire.domain.video.ShortVideo>>> = _videos.asStateFlow()
    private val videoJobs = HashMap<String, kotlinx.coroutines.Job>()

    /** Loads (or refreshes, with [force]) a tab's video carousel. */
    fun loadVideos(topicId: String, force: Boolean = false) {
        if (!force && (_videos.value.containsKey(topicId) || videoJobs[topicId]?.isActive == true)) return
        videoJobs[topicId] = viewModelScope.launch {
            val list = runCatching { loadVideos.invoke(topicId) }.getOrDefault(emptyList())
            if (list.isNotEmpty() || !_videos.value.containsKey(topicId)) _videos.update { it + (topicId to list) }
        }
    }

    val settings: StateFlow<UserSettings> = settingsRepo.settings

    /** All topics (ordered, with visibility) for the current settings. */
    private fun allTopicsFor(s: UserSettings) = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)

    val topicsState: StateFlow<List<Topic>> = settings
        .map { s -> allTopicsFor(s).filter { it.isVisible } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, allTopicsFor(settings.value).filter { it.isVisible })

    /** Snapshot accessor for non-Compose callers. */
    val topics: List<Topic> get() = topicsState.value

    private val _topicId = MutableStateFlow(topicsState.value.first().id)
    val topicIndex: StateFlow<Int> = combine(_topicId, topicsState) { id, list -> list.indexOfFirst { it.id == id }.coerceAtLeast(0) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val currentTopic: Topic get() = topics.firstOrNull { it.id == _topicId.value } ?: topics.first()

    private val _tab = MutableStateFlow(NavTab.TODAY)
    val tab: StateFlow<NavTab> = _tab.asStateFlow()

    private val _feeds = MutableStateFlow<Map<String, FeedResult>>(emptyMap())
    val feeds: StateFlow<Map<String, FeedResult>> = _feeds.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    /** The wire under the pull band: each source of the current pull-refresh as it answers. */
    private val _ticker = MutableStateFlow(news.openwire.data.repository.RefreshTicker())
    val ticker: StateFlow<news.openwire.data.repository.RefreshTicker> = _ticker.asStateFlow()
    /** Topics whose refresh the reader asked for (only those drive the wire). */
    private val pulled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Stories that arrived with the last refresh the reader asked for, per topic. */
    data class NewStories(val ids: List<String>, val since: Long)
    private val _newStories = MutableStateFlow<Map<String, NewStories>>(emptyMap())
    val newStories: StateFlow<Map<String, NewStories>> = _newStories.asStateFlow()
    private val beforeRefresh = HashMap<String, Pair<List<String>, Long>>()

    private val _archive = MutableStateFlow<Map<String, ArchiveState>>(emptyMap())
    /** Keyed by the story's original URL. */
    val archive: StateFlow<Map<String, ArchiveState>> = _archive.asStateFlow()

    /** The story open in the reader (detail pane when unfolded, slide-up sheet on the cover screen). */
    private val _selected = MutableStateFlow<Article?>(null)
    val selected: StateFlow<Article?> = _selected.asStateFlow()

    /** True while the compact slide-up reader is showing. Independent of [selected] so unfolding keeps the story. */
    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _linkDialog = MutableStateFlow(false)
    val linkDialog: StateFlow<Boolean> = _linkDialog.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off user messages (toasts). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val saved: StateFlow<List<Article>> = library.saved
    val liked: StateFlow<Set<String>> = library.liked
    val themeMode: StateFlow<ThemeMode> = appearance.mode

    private val loadJobs = mutableMapOf<String, Job>()
    /** When each topic last came back fresh from the network (not the cache). */
    private val freshAt = mutableMapOf<String, Long>()
    /** What each loaded topic was built from; a change (edition, sources, keywords…) reloads it. */
    private val signatures = mutableMapOf<String, String>()
    private val archiveGate = Semaphore(3)

    init {
        viewModelScope.launch {
            repository.refreshEvents.collect { e ->
                _ticker.update { t ->
                    if (e is news.openwire.data.repository.RefreshEvent.Started) { if (e.topicId in pulled) t.reduce(e) else t } else t.reduce(e)
                }
            }
        }
        load(currentTopic)
        viewModelScope.launch {
            settings.collect { s ->
                // Keep the selected topic valid, and reload any loaded topic whose definition changed.
                val all = allTopicsFor(s)
                val visible = all.filter { it.isVisible }
                if (visible.none { it.id == _topicId.value }) _topicId.value = visible.first().id
                _feeds.value.keys.toList().forEach { id ->
                    val t = all.firstOrNull { it.id == id }
                    if (t == null) { _feeds.update { it - id }; signatures.remove(id) }
                    else if (signatures[id] != signature(t, s)) load(t, force = true)
                }
                visible.firstOrNull { it.id == _topicId.value }?.let { if (_feeds.value[it.id] == null) load(it) }
            }
        }
    }

    private fun signature(t: Topic, s: UserSettings) =
        listOf(t.liveUrl, t.snapshotFile, t.keywords, t.sources, s.customFeeds.filter { f -> t.id in f.topics || f.id in t.sources }.map { it.url to it.maxArticles }, s.disabledSources).toString()

    fun selectTopic(index: Int) {
        val t = topics.getOrNull(index) ?: return
        selectTopicId(t.id)
    }

    fun selectTopicId(id: String) {
        val t = topics.firstOrNull { it.id == id } ?: return
        _topicId.value = t.id
        if (_tab.value != NavTab.AUDIO) _tab.value = NavTab.TODAY
        ensureFresh(t)
    }

    fun selectTab(tab: NavTab) {
        _tab.value = tab
        // Switching pages quietly brings what's shown up to date.
        when (tab) {
            NavTab.DISCOVER -> topics.forEach { ensureFresh(it) }
            NavTab.TODAY, NavTab.AUDIO -> ensureFresh(currentTopic)
            NavTab.SAVED, NavTab.LIVE -> Unit
            // Trending tabs there come from what every topic is covering.
            NavTab.OPINION -> topics.forEach { ensureFresh(it) }
        }
    }

    fun retry(topic: Topic) = load(topic, force = true)

    /** Pull-to-refresh / Refresh action: always goes back to the network. */
    fun refresh() {
        // What was on screen, to tell which stories are new when this refresh lands.
        fun mark(t: Topic) {
            pulled += t.id
            (_feeds.value[t.id] as? FeedResult.Success)?.let { r -> beforeRefresh[t.id] = r.articles.map { news.openwire.domain.motion.NewSince.key(it.title) } to r.updatedAt }
        }
        when (_tab.value) {
            NavTab.DISCOVER -> topics.forEach(::mark)
            NavTab.SAVED, NavTab.LIVE, NavTab.OPINION -> Unit
            else -> { mark(currentTopic); loadVideos(currentTopic.id, force = true) }
        }
        when (_tab.value) {
            NavTab.DISCOVER -> topics.forEach { load(it, force = true) }
            NavTab.SAVED, NavTab.LIVE, NavTab.OPINION -> Unit
            else -> load(currentTopic, force = true)
        }
    }

    fun setSortMode(mode: SortMode) = settingsRepo.update { it.copy(sortMode = mode) }

    fun setTextScale(scale: Float) = settingsRepo.update { it.copy(textScale = scale) }

    // ---- Analysis & Opinion: follows and topic tabs ----
    fun toggleFollowAuthor(name: String) = settingsRepo.update { s ->
        val key = news.openwire.domain.opinion.OpinionCatalog.authorKey(name)
        val existing = s.followedAuthors.firstOrNull { news.openwire.domain.opinion.OpinionCatalog.authorKey(it) == key }
        // Following again turns its alerts back on.
        s.copy(
            followedAuthors = if (existing != null) s.followedAuthors - existing else s.followedAuthors + name.trim(),
            opinionMuted = if (existing == null) s.opinionMuted - news.openwire.opinion.writerKey(name) else s.opinionMuted,
        )
    }

    fun toggleFollowAccount(handle: String) = settingsRepo.update { s ->
        s.copy(
            followedAccounts = if (handle in s.followedAccounts) s.followedAccounts - handle else s.followedAccounts + handle,
            opinionMuted = if (handle !in s.followedAccounts) s.opinionMuted - news.openwire.opinion.accountKey(handle) else s.opinionMuted,
        )
    }

    fun addOpinionTab(topic: String) = settingsRepo.update { s ->
        if (s.opinionTabs.any { it.equals(topic, true) }) s
        else s.copy(opinionTabs = s.opinionTabs + topic.trim(), opinionMuted = s.opinionMuted - news.openwire.opinion.topicKey(topic))
    }

    /** Alerts for a followed writer, account or opinion topic: on by default, toggled here. */
    fun toggleOpinionMute(key: String) = settingsRepo.update { s ->
        s.copy(opinionMuted = if (key in s.opinionMuted) s.opinionMuted - key else s.opinionMuted + key)
    }

    fun removeOpinionTab(topic: String) = settingsRepo.update { s -> s.copy(opinionTabs = s.opinionTabs.filterNot { it.equals(topic, true) }) }

    fun openArticle(article: Article) {
        _selected.value = article
        _sheetOpen.value = true
    }

    /** Unfolded posture: swap the detail pane without opening the cover-screen sheet. */
    fun showInPane(article: Article) {
        _selected.value = article
        _sheetOpen.value = false
    }

    /** Unfolded: keep a story in the detail pane rather than showing an empty pane. */
    fun ensureSelection(candidate: Article?) {
        if (_selected.value == null && candidate != null) _selected.value = candidate
    }

    fun closeSheet() { _sheetOpen.value = false }

    fun toggleSaved(article: Article) = library.toggleSaved(article)
    fun toggleLiked(article: Article) = library.toggleLiked(article)

    fun setQuery(q: String) { _query.value = q }

    private val _settingsTarget = MutableStateFlow<String?>(null)
    /** Settings page to open on (e.g. "updates" from the update banner). */
    val settingsTarget: StateFlow<String?> = _settingsTarget.asStateFlow()

    fun openSettings(open: Boolean = true) { _settingsTarget.value = null; _settingsOpen.value = open }
    fun openUpdates() = openSettingsAt("updates")
    fun openSettingsAt(page: String) { _settingsTarget.value = page; _settingsOpen.value = true }

    /** Text to start the opened settings page's search with (a deep link). */
    var settingsQuery: String? = null
        private set
    fun setSettingsQuery(q: String) { settingsQuery = q; news.openwire.ui.settings.SourcesQuery.pending = q }
    fun showLinkDialog(show: Boolean = true) { _linkDialog.value = show }

    /**
     * Quick-URL opener / share target (SPEC §6.5): turns any pasted or shared link into a
     * story routed by the paywall engine and opens it in the reader.
     * @return false when no URL could be found.
     */
    fun openLink(text: String?, subject: String? = null, title: String? = null): Boolean {
        val url = SharedUrl.extract(text, subject)
        if (url.isEmpty()) {
            _messages.trySend(tr(R.string.msg_not_a_link))
            return false
        }
        val domain = registry.normalizeDomain(url)
        val base = Article(
            id = "link-${url.hashCode()}",
            title = title?.trim().orEmpty(),
            source = registry.getRule(domain).label?.takeIf { it.isNotBlank() } ?: domain,
            domain = domain,
            originalUrl = url,
        )
        _linkDialog.value = false
        _settingsOpen.value = false
        if (news.openwire.domain.engine.GoogleNewsUrls.isGoogleNewsUrl(url)) {
            // A Google News wrapper: open it as soon as the publisher's own link is known.
            openArticle(base.copy(source = "Google News"))
            viewModelScope.launch {
                val real = runCatching { resolveLink(url) }.getOrNull() ?: return@launch
                val d = registry.normalizeDomain(real)
                openArticle(StrategyRouter.applyStrategy(registry, base.copy(
                    id = "link-${real.hashCode()}", originalUrl = real, domain = d,
                    source = registry.getRule(d).label?.takeIf { it.isNotBlank() } ?: d,
                )))
            }
            return true
        }
        openArticle(StrategyRouter.applyStrategy(registry, base))
        return true
    }

    fun toggleTheme(systemDark: Boolean) {
        val currentlyDark = when (themeMode.value) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        appearance.set(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun setTheme(mode: ThemeMode) = appearance.set(mode)

    fun toggleUnfoldedLayout() = settingsRepo.update { s ->
        s.copy(unfoldedLayout = if (s.unfoldedLayout == news.openwire.domain.model.UnfoldedLayout.FULL) news.openwire.domain.model.UnfoldedLayout.SPLIT else news.openwire.domain.model.UnfoldedLayout.FULL)
    }

    private fun isStale(id: String) = System.currentTimeMillis() - (freshAt[id] ?: 0L) > STALE_MS

    /**
     * Quietly brings a topic up to date if it's older than a few minutes: no spinner,
     * no "Refreshing…", the stories on screen are simply replaced when the new ones land.
     */
    private fun ensureFresh(topic: Topic) {
        if (loadJobs[topic.id]?.isActive == true) return
        if (_feeds.value[topic.id] !is FeedResult.Success) { load(topic); return }
        if (isStale(topic.id)) load(topic, force = true, silent = true)
    }

    /** App came to the foreground: refresh what's on screen (and Discover's topics) in the background. */
    fun onForeground() {
        ensureFresh(currentTopic)
        if (_tab.value == NavTab.DISCOVER) topics.forEach { ensureFresh(it) }
    }

    /**
     * The freshest stories for [topic], for an audio briefing: refetched first when they
     * are more than a few minutes old (waits at most ~8 s, else uses what it has).
     */
    suspend fun freshStories(topic: Topic): List<Article> {
        if (isStale(topic.id) || _feeds.value[topic.id] !is FeedResult.Success) {
            load(topic, force = true, silent = true)
            kotlinx.coroutines.withTimeoutOrNull(8_000) { loadJobs[topic.id]?.join() }
        }
        val list = (_feeds.value[topic.id] as? FeedResult.Success)?.articles.orEmpty()
        return news.openwire.domain.engine.FeedOrdering.apply(list, settings.value.sortMode)
    }

    private fun load(topic: Topic, force: Boolean = false, silent: Boolean = false) {
        if (!force && loadJobs[topic.id]?.isActive == true) return
        if (!force && _feeds.value[topic.id] is FeedResult.Success) return
        loadJobs[topic.id]?.cancel()
        signatures[topic.id] = signature(topic, settings.value)
        if (force && !silent) _refreshing.update { it + topic.id }
        loadJobs[topic.id] = viewModelScope.launch {
            try {
                repository.observeTopic(topic, force).collect { raw ->
                    if (raw is FeedResult.Success && !raw.fromCache) freshAt[topic.id] = System.currentTimeMillis()
                    // A silent refresh never shows its cache pass or a refreshing indicator.
                    val result = if (silent && raw is FeedResult.Success) {
                        if (raw.fromCache && _feeds.value[topic.id] is FeedResult.Success) return@collect
                        raw.copy(refreshing = false)
                    } else raw
                    _feeds.update { current ->
                        // Never replace stories on screen with a spinner or an error during a refresh.
                        if ((result is FeedResult.Loading || (silent && result is FeedResult.Error)) && current[topic.id] is FeedResult.Success) current
                        else current + (topic.id to result)
                    }
                    if (result is FeedResult.Success && !result.fromCache) checkArchives(result.articles)
                }
                (_feeds.value[topic.id] as? FeedResult.Success)?.let { if (it.fromCache) checkArchives(it.articles) }
            } finally {
                _refreshing.update { it - topic.id }
                if (pulled.remove(topic.id)) settleNew(topic.id)
            }
        }
    }

    /** After a refresh the reader asked for: which stories weren't there before it. */
    private fun settleNew(topicId: String) {
        val (before, at) = beforeRefresh.remove(topicId) ?: return
        val now = _feeds.value[topicId] as? FeedResult.Success ?: return
        if (now.fromCache) return
        val keys = now.articles.map { news.openwire.domain.motion.NewSince.key(it.title) }
        val fresh = news.openwire.domain.motion.NewSince.newIds(before, keys).toHashSet()
        val ids = now.articles.filter { news.openwire.domain.motion.NewSince.key(it.title) in fresh }.map { it.id }
        _newStories.update { if (ids.isEmpty()) it - topicId else it + (topicId to NewStories(ids, at)) }
    }

    /** Non-blocking archive checks for hard-paywall stories; missing snapshots grey the story out. */
    private fun checkArchives(articles: List<Article>) {
        articles.take(40).filter { it.needsArchive && !it.originalUrl.isNullOrEmpty() }.forEach { a ->
            val key = a.originalUrl!!
            if (_archive.value[key] != null) return@forEach
            _archive.update { it + (key to ArchiveState.Checking) }
            viewModelScope.launch {
                val url = archiveGate.withPermit { runCatching { archiveLookup(a) }.getOrNull() }
                _archive.update { it + (key to if (url != null) ArchiveState.Available(url) else ArchiveState.Locked) }
            }
        }
    }

    private companion object {
        /** Older than this, a topic is quietly refreshed when shown again. */
        const val STALE_MS = 5 * 60_000L
    }
}
