// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import news.openwire.R
import news.openwire.data.local.SettingsRepository
import news.openwire.data.repository.FeedRepository
import news.openwire.domain.engine.BriefingScript
import news.openwire.domain.engine.Languages
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.onboarding.AddedFeed
import news.openwire.domain.onboarding.KEYWORD_PREFIX
import news.openwire.domain.onboarding.LocationProblem
import news.openwire.domain.onboarding.Onboarding
import news.openwire.domain.onboarding.OnboardingContext
import news.openwire.domain.onboarding.OnboardingEvent
import news.openwire.domain.onboarding.OnboardingState
import news.openwire.domain.onboarding.Place
import news.openwire.domain.onboarding.Step
import news.openwire.i18n.LocationDetector
import news.openwire.i18n.tr

/**
 * First-run setup. The state lives in [SavedStateHandle] as JSON (rotation, folding and process
 * death keep the step and every choice); every change goes through [Onboarding.reduce].
 */
class OnboardingViewModel(
    private val handle: SavedStateHandle,
    private val settingsRepo: SettingsRepository,
    private val catalog: () -> List<FeedSource>,
    private val feeds: FeedRepository,
    rerun: Boolean,
) : ViewModel() {

    private val ctx = OnboardingContext(
        lineup = { p -> topicsFor(p).map { it.id } },
        sourcesFor = { id -> catalog().filter { f -> f.topics.any { it.equals(id, true) } }.map(SourceLibrary::sourceKey) },
    )

    private fun fresh(rerun: Boolean) = if (rerun) {
        OnboardingState(choices = Onboarding.choicesFrom(settingsRepo.settings.value, catalog()))
            .copy(editionMode = news.openwire.domain.onboarding.EditionMode.CONFIRMED)
    } else OnboardingState()

    private val _state = MutableStateFlow(Onboarding.decode(handle.get<String>(KEY))?.takeIf { it.step != Step.DONE } ?: fresh(rerun))

    /** A new run (Settings › Run setup again): from the current settings, at the welcome step. */
    fun restart(rerun: Boolean) {
        _state.value = fresh(rerun)
        handle[KEY] = Onboarding.encode(_state.value)
        _feedCheck.value = FeedCheck.Idle
    }
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun dispatch(e: OnboardingEvent) {
        val next = Onboarding.reduce(_state.value, e, ctx)
        if (next == _state.value) return
        _state.value = next
        handle[KEY] = Onboarding.encode(next)
    }

    // ---- Topics and sources (read for the graph and the panels) ----

    data class TopicNode(val id: String, val label: String, val local: Boolean, val keyword: Boolean)

    /** The topic lineup for an edition: built-ins (with the city), your own topics, setup's keyword topics. */
    fun topicsFor(p: Place?): List<news.openwire.domain.model.Topic> {
        val s = settingsRepo.settings.value
        return TopicRegistry.buildTopics(p?.toEdition() ?: s.edition, s.customTopics, emptySet(), s.topicOrder, s.language)
    }

    fun topicNodes(st: OnboardingState = _state.value): List<TopicNode> =
        topicsFor(st.choices.edition).map { TopicNode(it.id, it.label, it.id == TopicRegistry.LOCAL_ID, it.isCustom) } +
            st.choices.keywordTopics.map { TopicNode(it.id, it.name, local = false, keyword = true) }

    data class SourceNode(val key: String, val name: String, val domain: String, val topics: List<String>, val added: Boolean = false)

    /** Sources of the selected topics (a shared source once, linked to each), plus added feeds. */
    fun sourceNodes(st: OnboardingState = _state.value): List<SourceNode> {
        val chosen = st.choices.topics.filter { !it.startsWith(KEYWORD_PREFIX) }
        val out = LinkedHashMap<String, SourceNode>()
        chosen.forEach { t ->
            catalog().filter { f -> f.topics.any { it.equals(t, true) } }.forEach { f ->
                val k = SourceLibrary.sourceKey(f)
                out[k] = out[k]?.let { it.copy(topics = it.topics + t) } ?: SourceNode(k, f.name, f.domain, listOf(t))
            }
        }
        st.choices.feeds.forEach { f -> out.putIfAbsent(f.url, SourceNode(f.url, f.name, SourceLibrary.domainOf(f.url), emptyList(), added = true)) }
        return out.values.toList()
    }

    /** The briefing times (their current hours). */
    val slotDefaults: List<news.openwire.domain.model.BriefingSlot> get() = settingsRepo.settings.value.briefing.slots

    /** The whole library's size (for "12 of 117"). */
    val librarySize: Int get() = catalog().size

    // ---- Edition ----

    private var locateJob: Job? = null

    /** After the coarse-location permission answer: find the city once, or fall back to typing. */
    fun locate(context: Context, granted: Boolean) {
        if (!granted) { dispatch(OnboardingEvent.LocationFailed(LocationProblem.DENIED)); return }
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            when (val r = LocationDetector.detect(context.applicationContext)) {
                is LocationDetector.Result.Found -> dispatch(OnboardingEvent.LocationFound(Place.of(r.edition)))
                LocationDetector.Result.NoFix -> dispatch(OnboardingEvent.LocationFailed(LocationProblem.NO_FIX))
            }
        }
    }

    fun cancelLocate() { locateJob?.cancel() }

    data class CitySuggestion(val place: Place, val countryName: String)

    /** Known cities matching what's typed (city, region or country), the default edition first when empty. */
    fun citySuggestions(query: String): List<CitySuggestion> {
        val lang = Languages.effective(settingsRepo.settings.value.language)
        val q = query.trim().lowercase()
        val all = (listOf(LocalEdition.DEFAULT) + LocationDetector.knownCities).distinctBy { it.city }
        return all.filter { e ->
            q.isEmpty() || e.city.lowercase().contains(q) || e.region.lowercase().contains(q) ||
                Languages.countryName(e.country, lang).lowercase().contains(q) || TopicRegistry.country(e.country).name.lowercase().contains(q)
        }.take(5).map { CitySuggestion(Place.of(it), Languages.countryName(it.country, lang)) }
    }

    /** The country a typed city defaults to: the current edition's. */
    val defaultCountry: String get() = settingsRepo.settings.value.edition.country

    // ---- Sources: adding a feed ----

    private val _feedCheck = MutableStateFlow<FeedCheck>(FeedCheck.Idle)
    val feedCheck: StateFlow<FeedCheck> = _feedCheck.asStateFlow()

    sealed interface FeedCheck {
        data object Idle : FeedCheck
        data object Checking : FeedCheck
        data class Failed(val reason: String) : FeedCheck
    }

    /** The same check as Settings › Add a source: a real feed with stories, then added. */
    fun addFeed(raw: String) {
        val url = raw.trim().let { if (!it.startsWith("http")) "https://$it" else it }
        try { SourceLibrary.validateFeedUrl(url) } catch (e: IllegalArgumentException) { _feedCheck.value = FeedCheck.Failed(e.message.orEmpty()); return }
        _feedCheck.value = FeedCheck.Checking
        viewModelScope.launch {
            val check = runCatching { feeds.verifyFeed(url) }.getOrNull()
            if (check?.ok == true) {
                dispatch(OnboardingEvent.AddFeed(AddedFeed(check.title?.takeIf { it.isNotBlank() } ?: SourceLibrary.domainOf(url), url)))
                _feedCheck.value = FeedCheck.Idle
            } else _feedCheck.value = FeedCheck.Failed(tr(R.string.msg_feed_failed, check?.error ?: tr(R.string.no_stories)))
        }
    }

    // ---- Presenter sample (BriefingScript itself, as Settings › Audio briefing shows it) ----

    fun sampleOpening(st: OnboardingState = _state.value): String {
        val s = settingsRepo.settings.value
        val lang = Languages.effective(s.language)
        val top = Languages.topicName("TOP", lang) ?: "Top Stories"
        val p = st.choices.presenter
        val now = java.time.LocalDateTime.now()
        return BriefingScript.intro(top, s.briefing.count, s.briefing.detail, now, lang, p, seed = st.choices.seed) + " " +
            BriefingScript.lead(0, s.briefing.count, s.briefing.detail, lang, p, seed = st.choices.seed)
    }

    // ---- Finishing ----

    /** Saves every choice in one go (abandoning setup half-way saves nothing). */
    fun save() {
        val c = _state.value.choices
        settingsRepo.update { Onboarding.apply(it, c, catalog(), System.currentTimeMillis()) }
    }

    override fun onCleared() { locateJob?.cancel() }

    private companion object { const val KEY = "onboarding_state" }
}
