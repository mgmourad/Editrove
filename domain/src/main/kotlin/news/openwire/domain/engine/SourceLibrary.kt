// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.Topic

/**
 * The RSS source library: built-in catalog + user feeds, enable/disable, topic
 * assignment and grouping. Port of js/feedRegistry.js and js/sourceGroups.js.
 */
object SourceLibrary {

    val GOOGLE_NEWS = FeedSource(
        id = "google_news", name = "Google News", url = "https://news.google.com/rss", domain = "news.google.com",
        category = "News", kind = "aggregator", isPrimary = true,
    )

    private val CATEGORY_ORDER = listOf("News", "Politics", "Business", "Finance", "Science", "Technology", "Lifestyle", "Entertainment", "Sports", "Custom")

    /** Legacy PWA topic ids → Android ids. */
    val TOPIC_ALIASES = mapOf("WORLD" to "INTERNATIONAL")

    /** Preference key: the feed URL, or "google_news" for the aggregator. */
    fun sourceKey(source: FeedSource): String = if (source.isPrimary) "google_news" else sourceKey(source.url)

    fun sourceKey(url: String?): String {
        if (url.isNullOrBlank()) return ""
        if (url == "google_news") return url
        return try {
            val u = URI(url.trim())
            if (u.host == "news.google.com") "google_news" else u.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun isEnabled(source: FeedSource, disabled: Set<String>): Boolean = source.enabled && sourceKey(source) !in disabled

    /** Custom feeds first (user overrides win), then the catalog, deduped by URL. */
    fun allSources(catalog: List<FeedSource>, custom: List<FeedSource>): List<FeedSource> {
        val seen = HashSet<String>()
        return (custom.map { it.copy(isCustom = true) } + catalog.map { it.copy(isCustom = false) }).filter { it.url.isNotEmpty() && seen.add(it.url) }
    }

    /** Enabled direct feeds woven into a topic: its assigned sources, else feeds tagged with the topic id. */
    fun feedsForTopic(topic: Topic, catalog: List<FeedSource>, custom: List<FeedSource>, disabled: Set<String>): List<FeedSource> {
        val all = allSources(catalog, custom)
        val picked = if (topic.sources.isNotEmpty()) {
            val byId = all.associateBy { it.id }
            topic.sources.mapNotNull { byId[it] }
        } else {
            all.filter { s -> s.topics.any { it.equals(topic.id, ignoreCase = true) } }
        }
        return picked.filter { !it.isPrimary && isEnabled(it, disabled) }
    }

    /** True when the user changed what feeds into a built-in topic (so its published snapshot no longer applies). */
    fun isCustomized(topic: Topic, catalog: List<FeedSource>, custom: List<FeedSource>, disabled: Set<String>): Boolean {
        if (topic.isCustom || topic.sources.isNotEmpty()) return true
        if (custom.any { f -> f.topics.any { it.equals(topic.id, ignoreCase = true) } }) return true
        if ("google_news" in disabled) return true
        return catalog.any { f -> f.topics.any { it.equals(topic.id, ignoreCase = true) } && sourceKey(f) in disabled }
    }

    fun validateFeedUrl(url: String) {
        val ok = try {
            URI(url.trim()).let { (it.scheme == "http" || it.scheme == "https") && !it.host.isNullOrEmpty() }
        } catch (e: Exception) {
            false
        }
        require(ok) { "Please enter an RSS or Atom feed URL starting with http:// or https://." }
    }

    fun domainOf(url: String): String = try {
        URI(url).host?.lowercase()?.removePrefix("www.").orEmpty()
    } catch (e: Exception) {
        ""
    }

    /**
     * Adds (or merges into) a user feed. Quota is clamped to 7..10 as in the PWA.
     * Returns the updated custom list.
     */
    fun addCustomFeed(
        custom: List<FeedSource>,
        name: String,
        url: String,
        topics: List<String>,
        maxArticles: Int = 8,
        nowMillis: Long,
    ): List<FeedSource> {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        require(cleanName.isNotEmpty() && cleanUrl.isNotEmpty()) { "Feed name and URL are required." }
        validateFeedUrl(cleanUrl)
        val quota = maxArticles.coerceIn(7, 10)
        val upper = topics.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        val existing = custom.indexOfFirst { it.url == cleanUrl }
        if (existing >= 0) {
            val e = custom[existing]
            return custom.toMutableList().also {
                it[existing] = e.copy(name = cleanName, domain = domainOf(cleanUrl).ifEmpty { e.domain }, topics = (e.topics + upper).distinct(), maxArticles = quota, enabled = true, isCustom = true)
            }
        }
        val slug = cleanName.lowercase().replace(RX_1, "_").take(16)
        return custom + FeedSource(
            id = "feed_custom_${slug}_${nowMillis.toString(36)}",
            name = cleanName, url = cleanUrl, domain = domainOf(cleanUrl), topics = upper,
            maxArticles = quota, enabled = true, category = "Custom", isCustom = true,
        )
    }

    fun sourceCategory(s: FeedSource): String = s.category ?: if (s.isCustom) "Custom" else when (s.topics.firstOrNull()) {
        "BUSINESS" -> "Business"; "FINANCE" -> "Finance"; "POLITICS" -> "Politics"; "SCIENCE" -> "Science"
        "TECHNOLOGY" -> "Technology"; "LIFESTYLE" -> "Lifestyle"; "ENTERTAINMENT" -> "Entertainment"; "SPORTS" -> "Sports"
        else -> "News"
    }

    data class SourceGroup(val category: String, val aggregators: List<FeedSource>, val publishers: List<FeedSource>) {
        val all: List<FeedSource> get() = aggregators + publishers
    }

    /** Category › (aggregators | individual feeds) groups, filtered by free text. */
    fun groupSources(sources: List<FeedSource>, filter: String = ""): List<SourceGroup> {
        val q = filter.lowercase().trim()
        val groups = LinkedHashMap<String, Pair<MutableList<FeedSource>, MutableList<FeedSource>>>()
        for (s in sources) {
            val cat = sourceCategory(s)
            val agg = s.kind == "aggregator"
            if (q.isNotEmpty() && listOf(s.name, s.domain, s.url, cat, if (agg) "news aggregators" else "individual news feeds")
                    .none { it.lowercase().contains(q) }
            ) continue
            val g = groups.getOrPut(cat) { mutableListOf<FeedSource>() to mutableListOf() }
            (if (agg) g.first else g.second).add(s)
        }
        return groups.map { (cat, g) -> SourceGroup(cat, g.first.sortedBy { it.name.lowercase() }, g.second.sortedBy { it.name.lowercase() }) }
            .sortedWith(compareBy<SourceGroup> { CATEGORY_ORDER.indexOf(it.category).let { i -> if (i < 0) 100 else i } }.thenBy { it.category })
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("[^a-z0-9]")
