// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Strategy
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

/**
 * Single-file settings backup (SPEC §6.3), wire-compatible with the PWA's
 * js/settingsExport.js (version 1). Import is defensive: unknown fields are ignored,
 * malformed entries are skipped, and legacy topic ids are migrated.
 */
object SettingsCodec {

    data class Imported(
        val settings: UserSettings,
        val paywallRules: Map<String, DomainRule>,
        val notes: List<String>,
    )

    private val json = Json { prettyPrint = true }

    fun export(settings: UserSettings, customRules: Map<String, DomainRule>, allTopics: List<Topic>, exportedAtIso: String): String {
        val root = buildJsonObject {
            put("version", 1)
            put("appName", "Editrove")
            put("platform", "android")
            put("exportedAt", exportedAtIso)
            putJsonObject("topics") {
                putJsonArray("items") {
                    allTopics.forEach { t ->
                        add(buildJsonObject {
                            put("id", t.id); put("label", t.label)
                            put("keywords", JsonArray(t.keywords.map(::JsonPrimitive)))
                            put("sources", JsonArray(t.sources.map(::JsonPrimitive)))
                            put("url", t.liveUrl ?: ""); put("isCustom", t.isCustom); put("isVisible", t.isVisible)
                        })
                    }
                }
                put("order", JsonArray(allTopics.map { JsonPrimitive(it.id) }))
                putJsonObject("localEdition") {
                    put("city", settings.edition.city); put("region", settings.edition.region); put("country", settings.edition.country)
                }
            }
            putJsonObject("rssFeeds") {
                putJsonArray("customFeeds") { settings.customFeeds.forEach { add(feedJson(it)) } }
                put("disabledSources", JsonArray(settings.disabledSources.map(::JsonPrimitive)))
            }
            putJsonObject("paywallRules") {
                customRules.forEach { (domain, r) ->
                    putJsonObject(domain) {
                        put("strategy", r.strategy.wireValue); put("paywall", r.paywall ?: "custom"); put("label", r.label ?: ""); put("isCustom", true)
                    }
                }
            }
            putJsonObject("preferences") {
                put("sortMode", settings.sortMode.name)
                put("notificationsEnabled", settings.notificationsEnabled)
                put("notifyTopics", JsonArray(settings.notifyTopics.map(::JsonPrimitive)))
                put("backgroundRefresh", settings.backgroundRefresh)
                put("disabledServices", JsonArray(settings.disabledServices.map { JsonPrimitive(it.wireValue) }))
                put("unfoldedLayout", settings.unfoldedLayout.name)
                put("autoCheckUpdates", settings.autoCheckUpdates)
                put("autoInstallUpdates", settings.autoInstallUpdates)
                put("personalize", settings.personalize)
                put("refreshMinutes", settings.refreshMinutes)
                put("breakingEnabled", settings.breakingEnabled)
                put("breakingOutlets", JsonArray(settings.breakingOutlets.map(::JsonPrimitive)))
                putJsonObject("briefing") {
                    settings.briefing.voice?.let { put("voice", it) }
                    put("speed", settings.briefing.speed); put("count", settings.briefing.count)
                    put("detail", settings.briefing.detail.name); put("openingCue", settings.briefing.openingCue); put("stings", settings.briefing.stings)
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    private fun feedJson(f: FeedSource) = buildJsonObject {
        put("id", f.id); put("name", f.name); put("domain", f.domain); put("url", f.url)
        put("topics", JsonArray(f.topics.map(::JsonPrimitive))); put("maxArticles", f.maxArticles); put("enabled", f.enabled); put("isCustom", true)
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = this as? JsonArray
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonElement?.bool() = (this as? JsonPrimitive)?.booleanOrNull
    private fun JsonElement?.strings() = arr()?.mapNotNull { it.str() }.orEmpty()
    private fun mapTopicId(id: String) = SourceLibrary.TOPIC_ALIASES[id.uppercase()] ?: id.uppercase()

    /** Parses an Editrove or original OpenWire (web app) backup. Throws IllegalArgumentException when it is not one. */
    fun import(text: String, defaults: UserSettings = UserSettings()): Imported {
        val root = try { Json.parseToJsonElement(text).obj() } catch (e: Exception) { null }
            ?: throw IllegalArgumentException("This file isn't an Editrove (or OpenWire) settings backup.")
        if (root["topics"] == null && root["rssFeeds"] == null && root["paywallRules"] == null) {
            throw IllegalArgumentException("This file isn't an Editrove (or OpenWire) settings backup.")
        }
        val notes = mutableListOf<String>()
        val defaultIds = TopicRegistry.defaultTopics().map { it.id }.toSet()

        val topics = root["topics"].obj()
        val ed = topics?.get("localEdition").obj()
        val edition = run {
            val city = ed?.get("city").str()?.trim().orEmpty()
            val region = ed?.get("region").str()?.trim().orEmpty()
            if (city.isEmpty() || region.isEmpty()) return@run defaults.edition
            val country = ed?.get("country").str()
                ?: if (region.lowercase() in setOf("british columbia", "bc", "ontario", "alberta", "quebec")) "CA" else "US"
            LocalEdition(city, region, TopicRegistry.country(country).code)
        }

        val items = topics?.get("items").arr().orEmpty().mapNotNull { it.obj() }
        val custom = items.filter { it["isCustom"].bool() == true }.mapNotNull { o ->
            val id = o["id"].str()?.trim()?.uppercase().orEmpty()
            val label = o["label"].str()?.trim().orEmpty()
            if (id.isEmpty() || label.isEmpty() || id in defaultIds) return@mapNotNull null
            val keywords = o["keywords"].strings().ifEmpty { TopicRegistry.parseCommaKeywords(o["query"].str()) }
            val url = o["url"].str()?.takeIf { it.startsWith("http") }
            Topic(id = id, label = label, liveUrl = url, liveOnly = true, keywords = keywords, sources = o["sources"].strings(), isCustom = true)
        }.distinctBy { it.id }
        val knownIds = defaultIds + custom.map { it.id }
        val hidden = items.filter { it["isVisible"].bool() == false || it["visible"].bool() == false }
            .mapNotNull { it["id"].str()?.let(::mapTopicId) }.filter { it in knownIds }.toSet()
        val order = topics?.get("order").strings().map(::mapTopicId).filter { it in knownIds }.distinct().takeIf { it.isNotEmpty() }
        val dropped = items.mapNotNull { it["id"].str()?.let(::mapTopicId) }.filter { it !in knownIds }
        if (dropped.isNotEmpty()) notes += "Skipped topics that don't exist in the Android app: ${dropped.distinct().joinToString()}."

        val rss = root["rssFeeds"].obj()
        val feeds = rss?.get("customFeeds").arr().orEmpty().mapNotNull { it.obj() }.mapNotNull { o ->
            val id = o["id"].str().orEmpty(); val name = o["name"].str().orEmpty(); val url = o["url"].str().orEmpty()
            if (id.isBlank() || name.isBlank() || runCatching { SourceLibrary.validateFeedUrl(url) }.isFailure) return@mapNotNull null
            FeedSource(
                id = id, name = name, url = url, domain = o["domain"].str() ?: SourceLibrary.domainOf(url),
                topics = o["topics"].strings().map(::mapTopicId), maxArticles = ((o["maxArticles"] as? JsonPrimitive)?.intOrNull ?: 8).coerceIn(7, 10),
                enabled = o["enabled"].bool() != false, category = "Custom", isCustom = true,
            )
        }.distinctBy { it.id }
        val disabled = rss?.get("disabledSources").strings().map { SourceLibrary.sourceKey(it) }.filter { it.isNotEmpty() }.toSet()

        val rules = root["paywallRules"].obj().orEmpty().mapNotNull { (domain, v) ->
            val o = v.obj() ?: return@mapNotNull null
            if (o["isCustom"].bool() != true) return@mapNotNull null
            val strategy = Strategy.fromWire(o["strategy"].str()) ?: return@mapNotNull null
            domain.lowercase() to DomainRule(domain.lowercase(), strategy, o["paywall"].str() ?: "custom", o["label"].str(), isCustom = true)
        }.toMap()

        val prefs = root["preferences"].obj()
        val settings = defaults.copy(
            edition = edition,
            customTopics = custom,
            hiddenTopics = hidden,
            topicOrder = order,
            customFeeds = feeds,
            disabledSources = disabled,
            sortMode = prefs?.get("sortMode").str()?.let { s -> SortMode.entries.firstOrNull { it.name == s } } ?: defaults.sortMode,
            notificationsEnabled = prefs?.get("notificationsEnabled").bool() ?: defaults.notificationsEnabled,
            notifyTopics = prefs?.get("notifyTopics").strings().map(::mapTopicId).filter { it in knownIds }.toSet()
                .takeIf { prefs?.get("notifyTopics") != null } ?: defaults.notifyTopics,
            backgroundRefresh = prefs?.get("backgroundRefresh").bool() ?: defaults.backgroundRefresh,
            disabledServices = prefs?.get("disabledServices").strings().mapNotNull { Strategy.fromWire(it) }.toSet()
                .takeIf { prefs?.get("disabledServices") != null } ?: defaults.disabledServices,
            unfoldedLayout = prefs?.get("unfoldedLayout").str()?.let { v -> news.openwire.domain.model.UnfoldedLayout.entries.firstOrNull { it.name == v } } ?: defaults.unfoldedLayout,
            autoCheckUpdates = prefs?.get("autoCheckUpdates").bool() ?: defaults.autoCheckUpdates,
            autoInstallUpdates = prefs?.get("autoInstallUpdates").bool() ?: defaults.autoInstallUpdates,
            personalize = prefs?.get("personalize").bool() ?: defaults.personalize,
            refreshMinutes = (prefs?.get("refreshMinutes") as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()?.let(::validRefresh) ?: defaults.refreshMinutes,
            breakingEnabled = prefs?.get("breakingEnabled").bool() ?: defaults.breakingEnabled,
            breakingOutlets = prefs?.get("breakingOutlets").strings().filter { k -> BreakingNews.OUTLETS.any { it.key == k } }.toSet()
                .takeIf { prefs?.get("breakingOutlets") != null } ?: defaults.breakingOutlets,
            briefing = prefs?.get("briefing").obj()?.let { b ->
                news.openwire.domain.model.BriefingPrefs(
                    voice = b["voice"].str(),
                    speed = ((b["speed"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: 1f).coerceIn(0.5f, 2.5f),
                    count = ((b["count"] as? JsonPrimitive)?.intOrNull ?: 8).coerceIn(1, 30),
                    detail = news.openwire.domain.model.BriefingDetail.entries.firstOrNull { it.name == b["detail"].str() } ?: news.openwire.domain.model.BriefingDetail.HEADLINES,
                    openingCue = ((b["openingCue"] as? JsonPrimitive)?.intOrNull ?: 1).coerceIn(0, 4),
                    stings = b["stings"].bool() ?: true,
                )
            } ?: defaults.briefing,
        )
        return Imported(settings, rules, notes)
    }
}
