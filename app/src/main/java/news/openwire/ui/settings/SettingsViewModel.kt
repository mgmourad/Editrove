// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

import news.openwire.i18n.tr
import news.openwire.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import news.openwire.data.local.SettingsRepository
import news.openwire.data.repository.FeedCheck
import news.openwire.data.repository.FeedRepository
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.SettingsCodec
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.Strategy
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

/**
 * Personalization (SPEC §5/§6.2–§6.4/§6.7): edition, topics, source library,
 * paywall rules, notifications and settings backup. Everything stays on device.
 */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val registry: DomainRegistry,
    private val repository: FeedRepository,
    private val catalog: () -> List<FeedSource>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val health: news.openwire.data.remote.ServiceHealth? = null,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepo.settings

    val allTopics: StateFlow<List<Topic>> = settings.map(::topicsFor)
        .stateIn(viewModelScope, SharingStarted.Eagerly, topicsFor(settings.value))

    private fun topicsFor(s: UserSettings) = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)

    /** Google News + catalog + user feeds, with enabled state. */
    val library: StateFlow<List<FeedSource>> = settings.map(::libraryFor)
        .stateIn(viewModelScope, SharingStarted.Eagerly, libraryFor(settings.value))

    private fun libraryFor(s: UserSettings) = (listOf(SourceLibrary.GOOGLE_NEWS) + SourceLibrary.allSources(catalog(), s.customFeeds))
        .map { it.copy(enabled = SourceLibrary.isEnabled(it, s.disabledSources)) }

    private val _rules = MutableStateFlow(registry.getAllRules())
    val rules: StateFlow<Map<String, DomainRule>> = _rules.asStateFlow()

    private val _checks = MutableStateFlow<Map<String, FeedCheck?>>(emptyMap())
    /** Source key → result (null while checking). */
    val checks: StateFlow<Map<String, FeedCheck?>> = _checks.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    // ---- Edition ----

    fun setEdition(city: String, region: String, country: String) {
        val c = city.trim()
        val r = region.trim().ifEmpty { c }
        if (c.isEmpty()) { _message.value = tr(R.string.msg_enter_city); return }
        val next = LocalEdition(c, r, TopicRegistry.country(country).code)
        settingsRepo.update { s ->
            if (s.edition == next) s
            else s.copy(edition = next, previousEdition = s.edition, previousLanguage = s.previousLanguage ?: s.language)
        }
        _message.value = tr(R.string.msg_edition_set, next.displayName)
        offerLanguageFor(next.country)
    }

    private val _languageOffer = MutableStateFlow<news.openwire.domain.engine.Languages.Language?>(null)
    /** A language worth offering after moving to an edition that reads in it. */
    val languageOffer: StateFlow<news.openwire.domain.engine.Languages.Language?> = _languageOffer.asStateFlow()
    fun dismissLanguageOffer() { _languageOffer.value = null }

    private fun offerLanguageFor(country: String) {
        val lang = news.openwire.domain.engine.Languages.forCountry(country) ?: return
        if (lang.code != news.openwire.domain.engine.Languages.effective(settings.value.language)) _languageOffer.value = lang
    }

    /** Records Editrove's language ("" = the phone's); the screen applies it to Android. */
    fun setLanguage(tag: String) {
        _languageOffer.value = null
        settingsRepo.update { s ->
            if (s.language == tag) s else s.copy(language = tag, previousLanguage = s.language, previousEdition = s.previousEdition ?: s.edition)
        }
    }

    /** Back to the edition and language from before the last switch. */
    fun restorePrevious(): String? {
        val s = settings.value
        val lang = s.previousLanguage
        settingsRepo.update { it.copy(edition = s.previousEdition ?: it.edition, language = lang ?: it.language, previousEdition = null, previousLanguage = null) }
        _message.value = tr(R.string.msg_back_to, (s.previousEdition ?: s.edition).displayName)
        return lang
    }

    /** Adds or removes a suggested local newsroom. */
    fun toggleLocalSource(sug: news.openwire.domain.engine.LocalSources.Suggestion, on: Boolean) = settingsRepo.update { s ->
        if (on) {
            if (s.customFeeds.any { it.url == sug.url }) s
            else s.copy(customFeeds = s.customFeeds + news.openwire.domain.engine.LocalSources.toFeed(sug, s.edition.country, news.openwire.domain.engine.Languages.effective(s.language), clock()))
        } else s.copy(customFeeds = s.customFeeds.filterNot { it.url == sug.url })
    }

    // ---- Topics ----

    fun moveTopic(id: String, up: Boolean) = settingsRepo.update { s ->
        s.copy(topicOrder = TopicRegistry.move(topicsFor(s).map { it.id }, id, up))
    }

    fun setTopicVisible(id: String, visible: Boolean) = settingsRepo.update { s ->
        if (!visible && topicsFor(s).count { it.isVisible } <= 1) {
            _message.value = tr(R.string.msg_one_visible)
            s
        } else s.copy(hiddenTopics = if (visible) s.hiddenTopics - id else s.hiddenTopics + id)
    }

    private val _keywordIdeas = MutableStateFlow<KeywordIdeas>(KeywordIdeas.Idle)
    /** Keywords suggested from live headlines for a topic that isn't a preset. */
    val keywordIdeas: StateFlow<KeywordIdeas> = _keywordIdeas

    /** Reads the latest Google News headlines for [topic] and suggests the names that recur. */
    fun suggestKeywords(topic: String) {
        val q = topic.trim()
        if (q.length < 2) return
        _keywordIdeas.value = KeywordIdeas.Loading(q)
        viewModelScope.launch {
            val ideas = runCatching {
                val url = TopicRegistry.googleNewsSearchUrl(q, TopicRegistry.country(settings.value.edition.country))
                val feed = news.openwire.data.remote.GoogleNewsRssSource().fetchFeed(url)
                news.openwire.domain.engine.KeywordMiner.fromHeadlines(q, feed.items.map { it.title })
            }.getOrNull()
            _keywordIdeas.value = if (ideas.isNullOrEmpty()) KeywordIdeas.Failed(q) else KeywordIdeas.Ready(q, ideas)
        }
    }

    fun clearKeywordIdeas() { _keywordIdeas.value = KeywordIdeas.Idle }

    fun addTopic(label: String, keywords: String, sources: List<String>): Boolean = try {
        val topic = TopicRegistry.newCustomTopic(label, TopicRegistry.parseCommaKeywords(keywords), sources, settings.value.edition, clock())
        settingsRepo.update { s -> s.copy(customTopics = s.customTopics + topic, topicOrder = s.topicOrder?.plus(topic.id)) }
        _message.value = tr(R.string.msg_added_topic, topic.label)
        true
    } catch (e: IllegalArgumentException) {
        _message.value = e.message
        false
    }

    fun deleteTopic(id: String) = settingsRepo.update { s ->
        s.copy(
            customTopics = s.customTopics.filterNot { it.id == id },
            hiddenTopics = s.hiddenTopics - id,
            topicOrder = s.topicOrder?.minus(id),
            notifyTopics = s.notifyTopics - id,
            customFeeds = s.customFeeds.map { f -> f.copy(topics = f.topics - id) },
        )
    }

    fun resetTopics() = settingsRepo.update { it.copy(customTopics = emptyList(), hiddenTopics = emptySet(), topicOrder = null, edition = LocalEdition.DEFAULT) }

    // ---- Sources ----

    fun setSourceEnabled(source: FeedSource, enabled: Boolean) = settingsRepo.update { s ->
        val key = SourceLibrary.sourceKey(source)
        s.copy(
            disabledSources = if (enabled) s.disabledSources - key else s.disabledSources + key,
            customFeeds = s.customFeeds.map { if (it.url == source.url) it.copy(enabled = enabled) else it },
        )
    }

    fun setGroupEnabled(sources: List<FeedSource>, enabled: Boolean) = sources.forEach { setSourceEnabled(it, enabled) }

    /** Verifies the feed with the same fetch path the reader uses, then adds it. */
    fun addSource(name: String, url: String, topics: List<String>, quota: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val clean = url.trim()
            try { SourceLibrary.validateFeedUrl(clean) } catch (e: IllegalArgumentException) { _message.value = e.message; onDone(false); return@launch }
            val check = repository.verifyFeed(clean)
            if (!check.ok) { _message.value = tr(R.string.msg_feed_failed, check.error ?: tr(R.string.no_stories)); onDone(false); return@launch }
            try {
                settingsRepo.update { s ->
                    s.copy(customFeeds = SourceLibrary.addCustomFeed(s.customFeeds, name.ifBlank { check.title.orEmpty() }, clean, topics, quota, clock()))
                }
                _message.value = tr(R.string.msg_added_feed, name.ifBlank { check.title ?: clean }, check.count)
                onDone(true)
            } catch (e: IllegalArgumentException) {
                _message.value = e.message
                onDone(false)
            }
        }
    }

    fun deleteSource(id: String) = settingsRepo.update { s -> s.copy(customFeeds = s.customFeeds.filterNot { it.id == id }) }

    fun resetSources() = settingsRepo.update { it.copy(customFeeds = emptyList(), disabledSources = emptySet()) }

    /** "Check enabled feeds" audit: fetches every enabled direct source, four at a time. */
    fun checkEnabledFeeds() {
        val targets = library.value.filter { it.enabled && !it.isPrimary }
        _checks.value = targets.associate { SourceLibrary.sourceKey(it) to null }
        val gate = Semaphore(4)
        viewModelScope.launch {
            targets.map { src ->
                async {
                    val r = gate.withPermit { repository.verifyFeed(src.url) }
                    _checks.update { it + (SourceLibrary.sourceKey(src) to r) }
                }
            }.awaitAll()
            val failed = _checks.value.values.count { it?.ok == false }
            _message.value = if (failed == 0) tr(R.string.msg_feeds_ok, targets.size) else tr(R.string.msg_feeds_failed, failed, targets.size)
        }
    }

    // ---- Paywall rules ----

    fun setRule(domain: String, strategy: Strategy, label: String = "") {
        val d = registry.normalizeDomain(domain.trim())
        if (d.isEmpty()) { _message.value = tr(R.string.msg_enter_domain); return }
        registry.setDomainRule(d, strategy, "custom", label.ifBlank { registry.getRule(d).label.orEmpty() })
        _rules.value = registry.getAllRules()
    }

    fun resetRule(domain: String) {
        registry.deleteDomainRule(domain)
        _rules.value = registry.getAllRules()
    }

    fun resetAllRules() {
        registry.resetToDefaults()
        _rules.value = registry.getAllRules()
    }

    // ---- Reader services (fallback chain) ----

    private val _serviceStatus = MutableStateFlow<Map<Strategy, news.openwire.data.remote.ServiceHealth.Status?>>(emptyMap())
    /** Strategy → last check (null while checking). */
    val serviceStatus: StateFlow<Map<Strategy, news.openwire.data.remote.ServiceHealth.Status?>> = _serviceStatus.asStateFlow()

    fun checkServices() {
        val h = health ?: return
        val all = news.openwire.domain.engine.BypassServices.ALL
        _serviceStatus.value = all.associate { it.strategy to null }
        all.forEach { svc ->
            viewModelScope.launch { _serviceStatus.update { it + (svc.strategy to h.check(svc.strategy, force = true)) } }
        }
    }

    fun setServiceEnabled(strategy: Strategy, enabled: Boolean) = settingsRepo.update { s ->
        s.copy(disabledServices = if (enabled) s.disabledServices - strategy else s.disabledServices + strategy)
    }

    // ---- Notifications / preferences ----

    fun setNotificationsEnabled(on: Boolean) = settingsRepo.update { it.copy(notificationsEnabled = on) }
    fun setNotifyTopic(id: String, on: Boolean) = settingsRepo.update { s -> s.copy(notifyTopics = if (on) s.notifyTopics + id else s.notifyTopics - id) }
    fun setSortMode(mode: news.openwire.domain.model.SortMode) = settingsRepo.update { it.copy(sortMode = mode) }
    fun setUnfoldedLayout(layout: news.openwire.domain.model.UnfoldedLayout) = settingsRepo.update { it.copy(unfoldedLayout = layout) }
    fun setBriefing(transform: (news.openwire.domain.model.BriefingPrefs) -> news.openwire.domain.model.BriefingPrefs) =
        settingsRepo.update { it.copy(briefing = transform(it.briefing)) }
    fun setAutoCheckUpdates(on: Boolean) = settingsRepo.update { it.copy(autoCheckUpdates = on) }
    fun setAutoInstallUpdates(on: Boolean) = settingsRepo.update { it.copy(autoInstallUpdates = on) }
    fun setPersonalize(on: Boolean) = settingsRepo.update { it.copy(personalize = on) }
    fun setRefreshMinutes(m: Int) = settingsRepo.update { it.copy(refreshMinutes = news.openwire.domain.engine.validRefresh(m)) }
    fun setBreakingEnabled(on: Boolean) = settingsRepo.update { it.copy(breakingEnabled = on) }
    fun toggleBreakingOutlet(key: String) = settingsRepo.update { s ->
        s.copy(breakingOutlets = if (key in s.breakingOutlets) s.breakingOutlets - key else s.breakingOutlets + key)
    }
    fun setBackgroundRefresh(on: Boolean) = settingsRepo.update { it.copy(backgroundRefresh = on) }

    // ---- Backup ----

    fun exportJson(): String = SettingsCodec.export(
        settings.value,
        registry.getAllRules().filterValues { it.isCustom },
        allTopics.value,
        Instant.ofEpochMilli(clock()).toString(),
    )

    fun importJson(text: String) {
        try {
            val r = SettingsCodec.import(text, settings.value)
            settingsRepo.replace(r.settings)
            r.paywallRules.forEach { (d, rule) -> registry.setDomainRule(d, rule.strategy, rule.paywall ?: "custom", rule.label.orEmpty()) }
            _rules.value = registry.getAllRules()
            _message.value = (listOf(tr(R.string.msg_imported)) + r.notes).joinToString(" ")
        } catch (e: IllegalArgumentException) {
            _message.value = e.message
        }
    }

    fun resetEverything() {
        settingsRepo.replace(UserSettings())
        resetAllRules()
        _message.value = tr(R.string.msg_reset_all)
    }
}

sealed interface KeywordIdeas {
    data object Idle : KeywordIdeas
    data class Loading(val topic: String) : KeywordIdeas
    data class Ready(val topic: String, val keywords: List<String>) : KeywordIdeas
    data class Failed(val topic: String) : KeywordIdeas
}
