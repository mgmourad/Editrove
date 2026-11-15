// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

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

enum class NavTab(val label: String) { TODAY("Today"), DISCOVER("Discover"), AUDIO("Audio"), SAVED("Saved") }

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
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepo.settings

    /** All topics (ordered, with visibility) for the current settings. */
    private fun allTopicsFor(s: UserSettings) = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder)

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
    /** What each loaded topic was built from; a change (edition, sources, keywords…) reloads it. */
    private val signatures = mutableMapOf<String, String>()
    private val archiveGate = Semaphore(3)

    init {
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
        load(t)
    }

    fun selectTab(tab: NavTab) {
        _tab.value = tab
        if (tab == NavTab.DISCOVER) topics.forEach { load(it) }
    }

    fun retry(topic: Topic) = load(topic, force = true)

    /** Pull-to-refresh / Refresh action: always goes back to the network. */
    fun refresh() {
        when (_tab.value) {
            NavTab.DISCOVER -> topics.forEach { load(it, force = true) }
            NavTab.SAVED -> Unit
            else -> load(currentTopic, force = true)
        }
    }

    fun setSortMode(mode: SortMode) = settingsRepo.update { it.copy(sortMode = mode) }

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
    fun toggleLiked(article: Article) = library.toggleLiked(article.id)

    fun setQuery(q: String) { _query.value = q }

    private val _settingsTarget = MutableStateFlow<String?>(null)
    /** Settings page to open on (e.g. "updates" from the update banner). */
    val settingsTarget: StateFlow<String?> = _settingsTarget.asStateFlow()

    fun openSettings(open: Boolean = true) { _settingsTarget.value = null; _settingsOpen.value = open }
    fun openUpdates() { _settingsTarget.value = "updates"; _settingsOpen.value = true }
    fun showLinkDialog(show: Boolean = true) { _linkDialog.value = show }

    /**
     * Quick-URL opener / share target (SPEC §6.5): turns any pasted or shared link into a
     * story routed by the paywall engine and opens it in the reader.
     * @return false when no URL could be found.
     */
    fun openLink(text: String?, subject: String? = null, title: String? = null): Boolean {
        val url = SharedUrl.extract(text, subject)
        if (url.isEmpty()) {
            _messages.trySend("That doesn't look like a web link.")
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

    private fun load(topic: Topic, force: Boolean = false) {
        if (!force && loadJobs[topic.id]?.isActive == true) return
        if (!force && _feeds.value[topic.id] is FeedResult.Success) return
        loadJobs[topic.id]?.cancel()
        signatures[topic.id] = signature(topic, settings.value)
        if (force) _refreshing.update { it + topic.id }
        loadJobs[topic.id] = viewModelScope.launch {
            try {
                repository.observeTopic(topic, force).collect { result ->
                    _feeds.update { current ->
                        // Never replace stories on screen with a spinner during a refresh.
                        if (result is FeedResult.Loading && current[topic.id] is FeedResult.Success) current
                        else current + (topic.id to result)
                    }
                    if (result is FeedResult.Success && !result.fromCache) checkArchives(result.articles)
                }
                (_feeds.value[topic.id] as? FeedResult.Success)?.let { if (it.fromCache) checkArchives(it.articles) }
            } finally {
                _refreshing.update { it - topic.id }
            }
        }
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
}
