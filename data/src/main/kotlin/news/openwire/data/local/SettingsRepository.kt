// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import news.openwire.domain.model.DomainRule
import news.openwire.domain.engine.DomainRuleStore
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Strategy
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

/** User personalization, observable. Implemented on-device; faked in tests. */
interface SettingsRepository {
    val settings: StateFlow<UserSettings>
    fun update(transform: (UserSettings) -> UserSettings)
    fun replace(settings: UserSettings) = update { settings }
}

/** SharedPreferences + JSON implementation. Nothing here ever leaves the device. */
class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("openwire_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    override fun update(transform: (UserSettings) -> UserSettings) {
        _settings.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(Stored.serializer(), Stored.from(_settings.value))).apply()
    }

    private fun load(): UserSettings = try {
        prefs.getString(KEY, null)?.let { json.decodeFromString(Stored.serializer(), it).toSettings() } ?: UserSettings()
    } catch (e: Exception) {
        UserSettings()
    }

    @Serializable
    private data class StoredTopic(
        val id: String, val label: String, val liveUrl: String? = null,
        val keywords: List<String> = emptyList(), val sources: List<String> = emptyList(),
    )

    @Serializable
    private data class StoredFeed(
        val id: String, val name: String, val url: String, val domain: String = "",
        val topics: List<String> = emptyList(), val maxArticles: Int = 8, val enabled: Boolean = true,
    )

    @Serializable
    private data class Stored(
        val city: String = LocalEdition.DEFAULT.city,
        val region: String = LocalEdition.DEFAULT.region,
        val country: String = LocalEdition.DEFAULT.country,
        val customTopics: List<StoredTopic> = emptyList(),
        val hiddenTopics: Set<String> = emptySet(),
        val topicOrder: List<String>? = null,
        val customFeeds: List<StoredFeed> = emptyList(),
        val disabledSources: Set<String> = emptySet(),
        val sortMode: String = SortMode.CURATED.name,
        val notificationsEnabled: Boolean = false,
        val notifyTopics: Set<String> = emptySet(),
        val backgroundRefresh: Boolean = true,
        val disabledServices: Set<String> = emptySet(),
        val unfoldedLayout: String = "SPLIT",
        val autoCheckUpdates: Boolean = true,
        val autoInstallUpdates: Boolean = false,
        val briefingVoice: String? = null,
        val briefingSpeed: Float = 1.0f,
        val briefingCount: Int = 8,
        val briefingDetail: String = "HEADLINES",
        val briefingCue: Int = 1,
        val briefingStings: Boolean = true,
        val personalize: Boolean = true,
        val refreshMinutes: Int = 60,
        val breakingEnabled: Boolean = false,
        val breakingOutlets: Set<String>? = null,
        val briefingSlots: String? = null,
    ) {
        fun toSettings() = UserSettings(
            edition = LocalEdition(city, region, country),
            customTopics = customTopics.map { Topic(it.id, it.label, liveUrl = it.liveUrl, liveOnly = true, keywords = it.keywords, sources = it.sources, isCustom = true) },
            hiddenTopics = hiddenTopics,
            topicOrder = topicOrder,
            customFeeds = customFeeds.map { FeedSource(it.id, it.name, it.url, it.domain, it.topics, it.maxArticles, it.enabled, "Custom", isCustom = true) },
            disabledSources = disabledSources,
            sortMode = SortMode.entries.firstOrNull { it.name == sortMode } ?: SortMode.CURATED,
            notificationsEnabled = notificationsEnabled,
            notifyTopics = notifyTopics,
            backgroundRefresh = backgroundRefresh,
            disabledServices = disabledServices.mapNotNull { Strategy.fromWire(it) }.toSet(),
            unfoldedLayout = news.openwire.domain.model.UnfoldedLayout.entries.firstOrNull { it.name == unfoldedLayout } ?: news.openwire.domain.model.UnfoldedLayout.SPLIT,
            autoCheckUpdates = autoCheckUpdates,
            autoInstallUpdates = autoInstallUpdates,
            personalize = personalize,
            refreshMinutes = news.openwire.domain.engine.validRefresh(refreshMinutes),
            breakingEnabled = breakingEnabled,
            breakingOutlets = breakingOutlets ?: news.openwire.domain.engine.BreakingNews.DEFAULT_KEYS,
            briefing = news.openwire.domain.model.BriefingPrefs(
                voice = briefingVoice, speed = briefingSpeed.coerceIn(0.5f, 2.5f), count = briefingCount.coerceIn(1, 30),
                detail = news.openwire.domain.model.BriefingDetail.entries.firstOrNull { it.name == briefingDetail } ?: news.openwire.domain.model.BriefingDetail.HEADLINES,
                openingCue = briefingCue.coerceIn(0, news.openwire.domain.model.BriefingPrefs.CUE_COUNT), stings = briefingStings,
                slots = news.openwire.domain.model.BriefingSlot.decode(briefingSlots),
            ),
        )

        companion object {
            fun from(s: UserSettings) = Stored(
                s.edition.city, s.edition.region, s.edition.country,
                s.customTopics.map { StoredTopic(it.id, it.label, it.liveUrl, it.keywords, it.sources) },
                s.hiddenTopics, s.topicOrder,
                s.customFeeds.map { StoredFeed(it.id, it.name, it.url, it.domain, it.topics, it.maxArticles, it.enabled) },
                s.disabledSources, s.sortMode.name, s.notificationsEnabled, s.notifyTopics, s.backgroundRefresh,
                s.disabledServices.map { it.wireValue }.toSet(),
                s.unfoldedLayout.name, s.autoCheckUpdates, s.autoInstallUpdates,
                s.briefing.voice, s.briefing.speed, s.briefing.count, s.briefing.detail.name, s.briefing.openingCue, s.briefing.stings,
                s.personalize, s.refreshMinutes, s.breakingEnabled, s.breakingOutlets,
                news.openwire.domain.model.BriefingSlot.encode(s.briefing.slots),
            )
        }
    }

    private companion object { const val KEY = "user_settings_v1" }
}

/** In-memory settings for tests and previews. */
class InMemorySettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<UserSettings> = _settings.asStateFlow()
    override fun update(transform: (UserSettings) -> UserSettings) = _settings.update(transform)
}

/** Persists the user's per-domain paywall rule overrides (SPEC §6.2 rules editor). */
class PrefsDomainRuleStore(context: Context) : DomainRuleStore {
    private val prefs = context.getSharedPreferences("openwire_paywall_rules", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class StoredRule(val strategy: String, val paywall: String? = null, val label: String? = null)

    /** getRule() runs for every story, so rules are decoded once and kept in memory. */
    @Volatile private var cache: Map<String, DomainRule>? = null

    override fun load(): Map<String, DomainRule> = cache ?: read().also { cache = it }

    private fun read(): Map<String, DomainRule> = try {
        prefs.getString(KEY, null)?.let { raw ->
            json.decodeFromString(MapSerializer(String.serializer(), StoredRule.serializer()), raw).mapNotNull { (domain, r) ->
                Strategy.fromWire(r.strategy)?.let { domain to DomainRule(domain, it, r.paywall, r.label, isCustom = true) }
            }.toMap()
        }.orEmpty()
    } catch (e: Exception) {
        emptyMap()
    }

    override fun save(rules: Map<String, DomainRule>) {
        cache = rules
        val stored = rules.mapValues { StoredRule(it.value.strategy.wireValue, it.value.paywall, it.value.label) }
        prefs.edit().putString(KEY, json.encodeToString(MapSerializer(String.serializer(), StoredRule.serializer()), stored)).apply()
    }

    private companion object { const val KEY = "custom_rules_v1" }
}

/** Per-topic bookkeeping: last successful refresh and the stories already notified. */
class FeedStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("openwire_feed_state", Context.MODE_PRIVATE)

    fun lastUpdated(topicId: String): Long = prefs.getLong("updated_$topicId", 0L)
    fun markUpdated(topicId: String, at: Long) = prefs.edit().putLong("updated_$topicId", at).apply()

    /** null until the topic has been observed once (baseline). */
    fun seen(topicId: String): Set<String>? = prefs.getStringSet("seen_$topicId", null)?.toSet()
    fun setSeen(topicId: String, keys: Set<String>) = prefs.edit().putStringSet("seen_$topicId", keys.take(400).toSet()).apply()
}
