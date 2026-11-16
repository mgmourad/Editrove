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
    )

    fun country(code: String?): Country = COUNTRIES.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: COUNTRIES.first()

    /** The default lineup for an edition (SPEC §6.1). */
    fun defaultTopics(edition: LocalEdition = LocalEdition.DEFAULT): List<Topic> {
        val c = country(edition.country)
        return listOf(
            Topic(id = "TOP", label = "Top Stories", snapshotFile = "news_top.json"),
            Topic(id = "LATEST", label = "Latest", snapshotFile = "news_latest.json"),
            Topic(id = "AI", label = "AI", snapshotFile = "news_ai.json"),
            Topic(id = NATIONAL_ID, label = c.nationalLabel, liveUrl = "https://news.google.com/rss?${c.query}", liveOnly = true),
            Topic(
                id = LOCAL_ID,
                label = edition.city,
                liveUrl = googleNewsSearchUrl("\"${edition.city.replace(",", "").trim()}\"", c),
                liveOnly = true,
            ),
            Topic(id = "BUSINESS", label = "Business", snapshotFile = "news_business.json"),
            Topic(id = "TECHNOLOGY", label = "Tech", snapshotFile = "news_technology.json"),
            Topic(id = "INTERNATIONAL", label = "International", snapshotFile = "news_world.json"),
            Topic(id = "SCIENCE", label = "Science", snapshotFile = "news_science.json"),
            Topic(id = "ENTERTAINMENT", label = "Entertainment", snapshotFile = "news_entertainment.json"),
            Topic(id = "SPORTS", label = "Sports", snapshotFile = "news_sports.json"),
            Topic(id = "POLITICS", label = "Politics", snapshotFile = "news_politics.json"),
            Topic(id = "FINANCE", label = "Finance", snapshotFile = "news_finance.json"),
            Topic(id = "LIFESTYLE", label = "Lifestyle", snapshotFile = "news_lifestyle.json"),
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
    ): List<Topic> {
        val defaults = defaultTopics(edition)
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

    val PRESET_SUGGESTIONS: List<Pair<String, String>> = listOf(
        "Health & Medicine" to "health, medicine, healthcare",
        "Crypto & Web3" to "cryptocurrency, bitcoin, ethereum",
        "Space & Astronomy" to "SpaceX, NASA, astronomy",
        "Gaming & Esports" to "video games, gaming, esports",
        "Climate & Energy" to "climate change, clean energy, solar",
        "Electric Vehicles" to "electric vehicles, EV, Tesla",
        "Economy & Markets" to "economy, stock market, inflation",
        "AI & Robotics" to "artificial intelligence, machine learning, robotics",
    )
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("[^A-Z0-9]")
private val RX_2 = Regex("^[\"'].*[\"']$")
private val RX_3 = Regex("\\b(OR|AND|NOT)\\b")
private val RX_4 = Regex("\\s+")
