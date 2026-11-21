// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import news.openwire.domain.engine.FeedDiscovery
import news.openwire.domain.engine.TopicRegistry

/**
 * The news media of a country, by section: who Google News carries today in that country's
 * front page and its World, Business, Technology, Entertainment, Sports, Science and Health
 * sections. Each outlet can be added as a source for that section (its stories, through
 * Google News). Only the sections' public feeds are read; nothing about the reader is sent.
 */
class SourceDirectory(private val rss: GoogleNewsRssSource = GoogleNewsRssSource()) {

    /** A section of the news, and the Editrove topic its outlets join. */
    enum class Section(val googleId: String?, val topicId: String) {
        GENERAL(null, TopicRegistry.NATIONAL_ID),
        WORLD("WORLD", "INTERNATIONAL"),
        BUSINESS("BUSINESS", "BUSINESS"),
        TECHNOLOGY("TECHNOLOGY", "TECHNOLOGY"),
        ENTERTAINMENT("ENTERTAINMENT", "ENTERTAINMENT"),
        SPORTS("SPORTS", "SPORTS"),
        SCIENCE("SCIENCE", "SCIENCE"),
        HEALTH("HEALTH", "LIFESTYLE"),
    }

    /** An outlet in a section: its name, site, and how many of today's section stories it carried. */
    data class Outlet(val name: String, val domain: String, val section: Section, val stories: Int, val country: String, val sample: String?)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<Outlet>>()

    private fun url(c: TopicRegistry.Country, s: Section) =
        if (s.googleId == null) "https://news.google.com/rss?${c.query}"
        else "https://news.google.com/rss/headlines/section/topic/${s.googleId}?${c.query}"

    /** Outlets for [countries] in [sections], most active first within each section. */
    suspend fun outlets(countries: List<TopicRegistry.Country>, sections: List<Section>): Map<Section, List<Outlet>> = coroutineScope {
        val jobs = sections.flatMap { s -> countries.map { c -> s to async { fetch(c, s) } } }
        val bySection = jobs.groupBy({ it.first }, { it.second })
        sections.associateWith { s ->
            bySection[s].orEmpty().awaitAll().flatten()
                // One entry per outlet: by site, and by name (bbc.com and bbc.co.uk are one BBC).
                .groupBy { it.name.trim().lowercase() }
                .map { (_, list) -> list.maxBy { it.stories }.copy(stories = list.sumOf { it.stories }) }
                .sortedByDescending { it.stories }
                .take(30)
        }.filterValues { it.isNotEmpty() }
    }

    private suspend fun fetch(c: TopicRegistry.Country, s: Section): List<Outlet> {
        val key = "${c.code}:${s.name}"
        cache[key]?.let { return it }
        val items = withTimeoutOrNull(12_000) { runCatching { rss.fetchFeed(url(c, s)).items }.getOrNull() }.orEmpty()
        val out = items.mapNotNull { item ->
            val d = item.source?.let { FeedDiscovery.domainOf(it) } ?: return@mapNotNull null
            if (d.endsWith("google.com") || d.endsWith("youtube.com")) return@mapNotNull null
            Triple(d, item.sourceName ?: d, item.title.substringBeforeLast(" - "))
        }.groupBy { it.first }.map { (d, list) -> Outlet(list.first().second, d, s, list.size, c.code, list.first().third) }
        if (out.isNotEmpty()) cache[key] = out
        return out
    }

    /**
     * The feed an outlet is added with: its stories through Google News in its country — for a
     * section, only those about that section ("site:kooora.com رياضة OR مباراة …").
     */
    fun feedUrl(o: Outlet): String {
        val c = TopicRegistry.country(o.country)
        val words = SECTION_WORDS[o.section]?.get(c.hl.substringBefore('-'))?.let { " ($it)" }.orEmpty()
        return TopicRegistry.googleNewsSearchUrl("site:${o.domain}$words when:3d", c)
    }

    private companion object {
        val SECTION_WORDS: Map<Section, Map<String, String>> = mapOf(
            Section.WORLD to mapOf("en" to "world OR international", "ar" to "دولي OR العالم", "fr" to "monde OR international", "es" to "mundo OR internacional", "de" to "Welt OR international"),
            Section.BUSINESS to mapOf("en" to "business OR economy OR markets", "ar" to "اقتصاد OR أعمال OR بورصة", "fr" to "économie OR entreprises OR bourse", "es" to "economía OR empresas OR bolsa", "de" to "Wirtschaft OR Unternehmen OR Börse"),
            Section.TECHNOLOGY to mapOf("en" to "technology OR tech OR AI", "ar" to "تكنولوجيا OR تقنية OR هواتف", "fr" to "technologie OR tech OR numérique", "es" to "tecnología OR tech OR digital", "de" to "Technologie OR Tech OR digital"),
            Section.ENTERTAINMENT to mapOf("en" to "entertainment OR film OR music OR celebrity", "ar" to "فن OR سينما OR مسلسل OR نجوم", "fr" to "cinéma OR musique OR culture", "es" to "cine OR música OR famosos", "de" to "Film OR Musik OR Kultur"),
            Section.SPORTS to mapOf("en" to "sports OR football OR match", "ar" to "رياضة OR مباراة OR الدوري", "fr" to "sport OR football OR match", "es" to "deportes OR fútbol OR partido", "de" to "Sport OR Fußball OR Spiel"),
            Section.SCIENCE to mapOf("en" to "science OR research OR space", "ar" to "علوم OR دراسة OR فضاء", "fr" to "science OR recherche OR espace", "es" to "ciencia OR investigación OR espacio", "de" to "Wissenschaft OR Forschung OR Weltraum"),
            Section.HEALTH to mapOf("en" to "health OR medical OR hospital", "ar" to "صحة OR طب OR مستشفى", "fr" to "santé OR médecine OR hôpital", "es" to "salud OR medicina OR hospital", "de" to "Gesundheit OR Medizin OR Klinik"),
        )
    }
}
