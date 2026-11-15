// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import news.openwire.data.local.AppearanceStore
import news.openwire.data.local.LibraryStore
import news.openwire.data.local.ThemeMode
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.Topic

enum class NavTab(val label: String) { TODAY("Today"), DISCOVER("Discover"), AUDIO("Audio"), SAVED("Saved") }

/**
 * Screen state for the adaptive home: active nav tab, topic (category pill),
 * per-topic feeds, the selected story (reader), and the local library.
 */
class HomeViewModel(
    private val repository: FeedRepository,
    private val library: LibraryStore,
    private val appearance: AppearanceStore,
) : ViewModel() {

    val topics: List<Topic> = TopicRegistry.defaultTopics()

    private val _topicIndex = MutableStateFlow(0)
    val topicIndex: StateFlow<Int> = _topicIndex.asStateFlow()

    private val _tab = MutableStateFlow(NavTab.TODAY)
    val tab: StateFlow<NavTab> = _tab.asStateFlow()

    private val _feeds = MutableStateFlow<Map<String, FeedResult>>(emptyMap())
    val feeds: StateFlow<Map<String, FeedResult>> = _feeds.asStateFlow()

    /** The story open in the reader (detail pane when unfolded, slide-up sheet on the cover screen). */
    private val _selected = MutableStateFlow<Article?>(null)
    val selected: StateFlow<Article?> = _selected.asStateFlow()

    /** True while the compact slide-up reader is showing. Independent of [selected] so unfolding keeps the story. */
    private val _sheetOpen = MutableStateFlow(false)
    val sheetOpen: StateFlow<Boolean> = _sheetOpen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val saved: StateFlow<List<Article>> = library.saved
    val liked: StateFlow<Set<String>> = library.liked
    val themeMode: StateFlow<ThemeMode> = appearance.mode

    private val loadJobs = mutableMapOf<String, Job>()

    init {
        load(topics.first())
    }

    fun selectTopic(index: Int) {
        _topicIndex.value = index
        _tab.value = NavTab.TODAY
        load(topics[index])
    }

    fun selectTab(tab: NavTab) {
        _tab.value = tab
        if (tab == NavTab.DISCOVER) topics.forEach(::load)
    }

    fun retry(topic: Topic) = load(topic, force = true)

    fun openArticle(article: Article) {
        _selected.value = article
        _sheetOpen.value = true
    }

    /** Unfolded: keep a story in the detail pane rather than showing an empty pane. */
    fun ensureSelection(candidate: Article?) {
        if (_selected.value == null && candidate != null) _selected.value = candidate
    }

    /** Unfolded posture: swap the detail pane without opening the cover-screen sheet. */
    fun showInPane(article: Article) {
        _selected.value = article
        _sheetOpen.value = false
    }

    fun closeSheet() {
        _sheetOpen.value = false
    }

    fun toggleSaved(article: Article) = library.toggleSaved(article)
    fun toggleLiked(article: Article) = library.toggleLiked(article.id)

    fun setQuery(q: String) { _query.value = q }

    fun toggleTheme(systemDark: Boolean) {
        val currentlyDark = when (themeMode.value) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        appearance.set(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    private fun load(topic: Topic, force: Boolean = false) {
        if (!force && loadJobs[topic.id]?.isActive == true) return
        if (!force && _feeds.value[topic.id] is FeedResult.Success) return
        loadJobs[topic.id]?.cancel()
        loadJobs[topic.id] = viewModelScope.launch {
            repository.observeTopic(topic).collect { result ->
                _feeds.update { current ->
                    // Never replace stories on screen with a spinner during a refresh.
                    if (result is FeedResult.Loading && current[topic.id] is FeedResult.Success) current
                    else current + (topic.id to result)
                }
            }
        }
    }
}
