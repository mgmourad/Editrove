// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URLEncoder
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.Topic

/**
 * Topic catalog, ordering/visibility, custom topics and editions. Port of
 * js/topicRegistry.js. The World topic is labeled "International" and the
 * default edition is US / Washington, D.C. (ADR-0005).
 */
object TopicRegistry {

    /** Stable ids of the two edition-driven live topics. */
    const val NATIONAL_ID = "US"
    const val LOCAL_ID = "WASHINGTON_DC"

    /** A Google News locale for an edition country. */
    data class Country(val code: String, val name: String, val nationalLabel: String, val hl: String, val ceid: String) {
        val query: String get() = "hl=$hl&gl=$code&ceid=$ceid"
        /** The news language ("ar", "fr", …). */
        val language: String get() = hl.substringBefore('-')
    }

    val COUNTRIES: List<Country> = listOf(
        Country("US", "United States", "US", "en-US", "US:en"),
        Country("GB", "United Kingdom", "UK", "en-GB", "GB:en"),
        Country("CA", "Canada", "Canada", "en-CA", "CA:en"),
        Country("AU", "Australia", "Australia", "en-AU", "AU:en"),
        Country("IE", "Ireland", "Ireland", "en-IE", "IE:en"),
        Country("NZ", "New Zealand", "New Zealand", "en-NZ", "NZ:en"),
        Country("IN", "India", "India", "en-IN", "IN:en"),
        Country("SG", "Singapore", "Singapore", "en-SG", "SG:en"),
        Country("ZA", "South Africa", "South Africa", "en-ZA", "ZA:en"),
        Country("AE", "United Arab Emirates", "UAE", "en-AE", "AE:en"),
        Country("DE", "Germany", "Deutschland", "de", "DE:de"),
        Country("FR", "France", "France", "fr", "FR:fr"),
        Country("ES", "Spain", "España", "es", "ES:es"),
        Country("IT", "Italy", "Italia", "it", "IT:it"),
        Country("MX", "Mexico", "México", "es-419", "MX:es-419"),
        Country("BR", "Brazil", "Brasil", "pt-BR", "BR:pt-419"),
        Country("JP", "Japan", "日本", "ja", "JP:ja"),
        Country("EG", "Egypt", "مصر", "ar", "EG:ar"),
        Country("SA", "Saudi Arabia", "السعودية", "ar", "SA:ar"),
        Country("LB", "Lebanon", "لبنان", "ar", "LB:ar"),
        Country("MA", "Morocco", "Maroc", "fr", "MA:fr"),
        Country("BE", "Belgium", "Belgique", "fr", "BE:fr"),
        Country("CH", "Switzerland", "Schweiz", "de", "CH:de"),
        Country("AT", "Austria", "Österreich", "de", "AT:de"),
        Country("AR", "Argentina", "Argentina", "es-419", "AR:es-419"),
        Country("CO", "Colombia", "Colombia", "es-419", "CO:es-419"),
        Country("CL", "Chile", "Chile", "es-419", "CL:es-419"),
    )

    fun country(code: String?): Country = COUNTRIES.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: COUNTRIES.first()

    /** The default lineup for an edition (SPEC §6.1). */
    fun defaultTopics(edition: LocalEdition = LocalEdition.DEFAULT, appLanguage: String = Languages.effective(null)): List<Topic> {
        val c = country(edition.country)
        val lang = Languages.of(appLanguage)?.code ?: "en"
        fun name(id: String) = Languages.topicName(id, lang) ?: id
        // Every topic is built on the phone from Google News (in the edition's language) plus
        // the publisher feeds; the published snapshot is only a first paint and a fallback.
        // Topic feeds read in the app's language: an edition in another language (Cairo while the
        // app is in English) keeps its own national and city tabs, the rest stay readable.
        val f = if (c.language == lang) c else COUNTRIES.firstOrNull { it.language == lang } ?: COUNTRIES.first()
        fun section(name: String) = "https://news.google.com/rss/headlines/section/topic/$name?${f.query}"
        fun search(q: String) = googleNewsSearchUrl(q, f)
        val top = "https://news.google.com/rss?${f.query}"
        return listOf(
            Topic(id = "TOP", label = name("TOP"), snapshotFile = "news_top.json", liveUrl = top),
            Topic(id = "LATEST", label = name("LATEST"), snapshotFile = "news_latest.json", liveUrl = top),
            Topic(id = "AI", label = name("AI"), snapshotFile = "news_ai.json", liveUrl = search(Languages.query("AI", f.language))),
            Topic(id = NATIONAL_ID, label = Languages.countryName(c.code, lang), liveUrl = "https://news.google.com/rss/headlines/section/topic/NATION?${c.query}", liveOnly = true),
            Topic(
                id = LOCAL_ID,
                label = edition.city,
                liveUrl = googleNewsSearchUrl("\"${edition.city.replace(",", "").trim()}\"", c),
                liveOnly = true,
            ),
            Topic(id = "BUSINESS", label = name("BUSINESS"), snapshotFile = "news_business.json", liveUrl = section("BUSINESS")),
            Topic(id = "TECHNOLOGY", label = name("TECHNOLOGY"), snapshotFile = "news_technology.json", liveUrl = section("TECHNOLOGY")),
            Topic(id = "INTERNATIONAL", label = name("INTERNATIONAL"), snapshotFile = "news_world.json", liveUrl = section("WORLD")),
            Topic(id = "SCIENCE", label = name("SCIENCE"), snapshotFile = "news_science.json", liveUrl = section("SCIENCE")),
            Topic(id = "ENTERTAINMENT", label = name("ENTERTAINMENT"), snapshotFile = "news_entertainment.json", liveUrl = section("ENTERTAINMENT")),
            Topic(id = "SPORTS", label = name("SPORTS"), snapshotFile = "news_sports.json", liveUrl = section("SPORTS")),
            Topic(id = "POLITICS", label = name("POLITICS"), snapshotFile = "news_politics.json", liveUrl = search(Languages.query("POLITICS", f.language))),
            Topic(id = "FINANCE", label = name("FINANCE"), snapshotFile = "news_finance.json", liveUrl = search(Languages.query("FINANCE", f.language))),
            Topic(id = "LIFESTYLE", label = name("LIFESTYLE"), snapshotFile = "news_lifestyle.json", liveUrl = section("HEALTH")),
        )
    }

    /** Snapshot-backed topics only (excludes live-only topics). */
    fun snapshotTopics(): List<Topic> = defaultTopics().filter { !it.liveOnly }

    /**
     * Defaults + custom topics with visibility and the saved order applied.
     * A damaged preference never leaves the app without a visible topic.
     */
    fun buildTopics(
        edition: LocalEdition,
        custom: List<Topic>,
        hidden: Set<String>,
        order: List<String>?,
        language: String = "",
    ): List<Topic> {
        val defaults = defaultTopics(edition, Languages.effective(language))
        val ids = defaults.map { it.id }.toMutableSet()
        val all = (defaults + custom.filter { ids.add(it.id) }.map { it.copy(isCustom = true) })
            .map { it.copy(isVisible = it.id !in hidden) }
        val sorted = if (order.isNullOrEmpty()) all else {
            val pos = order.withIndex().associate { it.value to it.index }
            all.sortedBy { pos[it.id] ?: (9999 + all.indexOf(it)) }
        }
        return if (sorted.isNotEmpty() && sorted.none { it.isVisible }) {
            listOf(sorted.first().copy(isVisible = true)) + sorted.drop(1)
        } else sorted
    }

    /** Moves a topic one step up/down; returns the new id order. */
    fun move(currentOrder: List<String>, id: String, up: Boolean): List<String> {
        val i = currentOrder.indexOf(id)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in currentOrder.indices) return currentOrder
        return currentOrder.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    /** "artificial intelligence, OpenAI" → ["artificial intelligence", "OpenAI"]. */
    fun parseCommaKeywords(input: String?): List<String> =
        input.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Phrases are quoted, operators grouped, terms OR-ed — a Google News query. */
    fun formatKeywordsQuery(keywords: List<String>): String = keywords.filter { it.isNotBlank() }.joinToString(" OR ") { term ->
        when {
            RX_2.matches(term) -> term
            RX_3.containsMatchIn(term) -> "($term)"
            RX_4.containsMatchIn(term) -> "\"$term\""
            else -> term
        }
    }

    fun googleNewsSearchUrl(query: String, country: Country = COUNTRIES.first()): String {
        val q = query.trim()
        if (q.isEmpty()) return "https://news.google.com/rss?${country.query}"
        if (q.startsWith("http://") || q.startsWith("https://")) return q
        return "https://news.google.com/rss/search?q=${URLEncoder.encode(q, "UTF-8")}&${country.query}"
    }

    /** Builds a custom topic from keywords and/or assigned source ids (label is the fallback query). */
    fun newCustomTopic(
        label: String,
        keywords: List<String>,
        sources: List<String>,
        edition: LocalEdition,
        nowMillis: Long,
    ): Topic {
        val clean = label.trim()
        require(clean.isNotEmpty()) { "Topic name is required." }
        val slug = clean.uppercase().replace(RX_1, "_").take(16)
        val country = country(edition.country)
        val liveUrl = when {
            keywords.isNotEmpty() -> googleNewsSearchUrl(formatKeywordsQuery(keywords), country)
            sources.isNotEmpty() -> null
            else -> googleNewsSearchUrl(clean, country)
        }
        return Topic(
            id = "CUSTOM_${slug}_${nowMillis.toString().takeLast(4)}",
            label = clean,
            liveUrl = liveUrl,
            liveOnly = true,
            keywords = keywords,
            sources = sources,
            isCustom = true,
        )
    }

}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("[^A-Z0-9]")
private val RX_2 = Regex("^[\"'].*[\"']$")
private val RX_3 = Regex("\\b(OR|AND|NOT)\\b")
private val RX_4 = Regex("\\s+")
