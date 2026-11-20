// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeaturesTest {
    private val registry = DomainRegistry()
    private val norm: (String) -> String = { registry.normalizeDomain(it) }
    private fun a(id: String, title: String, domain: String = "$id.com", ageMin: Long = 30, coverage: Int? = null, now: Long = NOW) =
        Article(id = id, title = title, source = domain, domain = domain, originalUrl = "https://$domain/$id", publishedAt = now - ageMin * 60_000, coverage = coverage)

    // ---- Unique briefing stories ----

    @Test fun differentlyFramedVersionsOfOneStoryAreTheSame() {
        val cnn = a("cnn", "Trump is visiting China this week", "cnn.com")
        val cnbc = a("cnbc", "The reasons why President Xi is meeting with Trump", "cnbc.com")
        assertTrue(StoryIdentity.sameStory(cnn, cnbc, norm))
    }

    @Test fun differentTrumpStoriesStayDistinct() {
        val x = a("x", "Trump signs order on federal hiring at White House", "apnews.com")
        val y = a("y", "Trump says White House will veto farm bill", "politico.com")
        assertFalse(StoryIdentity.sameStory(x, y, norm))
    }

    @Test fun briefingPicksUniqueStoriesInOrder() {
        val list = listOf(
            a("1", "Trump is visiting China this week", "cnn.com"),
            a("2", "Fed holds rates steady as inflation cools", "reuters.com"),
            a("3", "Hurricane Delta makes landfall in Louisiana", "apnews.com"),
            a("4", "Powell signals the Fed may hold rates into spring", "wsj.com"),
            a("5", "Supreme Court hears major voting rights case", "npr.org"),
            a("6", "The reasons why President Xi is meeting with Trump", "cnbc.com"),
            a("7", "Nvidia earnings beat forecasts", "bloomberg.com"),
        )
        assertEquals(listOf("1", "2", "3", "5", "7"), StoryIdentity.distinct(list, 5, norm).map { it.id })
    }

    // ---- Topic presets & keyword mining ----

    @Test fun middleEastPresetIsSuggestedWithItsKeywords() {
        val first = TopicPresets.suggest("Middle E").first()
        assertEquals("Middle East", first.name)
        assertTrue(first.keywords.containsAll(listOf("Iran", "Israel", "Lebanon", "Yemen", "Saudi Arabia", "Egypt")))
        assertEquals("Middle East", TopicPresets.suggest("mideast").first().name)
        assertEquals("Formula 1", TopicPresets.suggest("f1").first().name)
        assertTrue(TopicPresets.suggest("x").isEmpty())
    }

    @Test fun keywordMinerFindsRecurringNames() {
        val titles = listOf(
            "Sudan army retakes Khartoum airport from RSF - Reuters",
            "RSF fighters withdraw from Khartoum as Sudan army advances - BBC",
            "UN warns of famine in Darfur as RSF siege continues - AP",
            "Darfur refugees flee to Chad amid RSF attacks - Al Jazeera",
            "Sudan peace talks resume in Jeddah - CNN",
        )
        val kw = KeywordMiner.fromHeadlines("Sudan", titles)
        assertTrue(kw.take(3).containsAll(listOf("RSF", "Khartoum", "Darfur")))
        assertFalse(kw.any { it.equals("Sudan", ignoreCase = true) })
    }

    // ---- Breaking news ----

    @Test fun breakingIsLabelledOrWidelyReportedAndFresh() {
        assertNotNull(BreakingNews.reason(a("b", "BREAKING: Earthquake strikes Tokyo", ageMin = 10), NOW))
        assertNotNull(BreakingNews.reason(a("w", "Gunfire reported at state capitol", ageMin = 20, coverage = 7), NOW))
        assertNull(BreakingNews.reason(a("o", "BREAKING: Old story", ageMin = 300), NOW))
        assertNull(BreakingNews.reason(a("n", "Recipe: perfect roast chicken", ageMin = 5, coverage = 1), NOW))
        assertEquals("Earthquake strikes Tokyo", BreakingNews.cleanTitle("BREAKING: Earthquake strikes Tokyo"))
    }

    @Test fun breakingQueryCoversChosenOutletsOnly() {
        val url = BreakingNews.queryUrl(setOf("ap", "bbc"))!!
        assertTrue(url.contains("site%3Aapnews.com") && url.contains("site%3Abbc.com"))
        assertFalse(url.contains("cnn.com"))
        assertNull(BreakingNews.queryUrl(emptySet()))
    }

    private companion object { const val NOW = 1_800_000_000_000L }

    @Test fun newSettingsSurviveExportAndImport() {
        val s = news.openwire.domain.model.UserSettings(refreshMinutes = 15, breakingEnabled = true, breakingOutlets = setOf("ap", "bbc"))
        val back = SettingsCodec.import(SettingsCodec.export(s, emptyMap(), TopicRegistry.defaultTopics(), "2026-11-16T00:00:00Z")).settings
        assertEquals(15, back.refreshMinutes)
        assertTrue(back.breakingEnabled)
        assertEquals(setOf("ap", "bbc"), back.breakingOutlets)
        assertEquals(60, validRefresh(50))
    }

    @Test fun storyCarriedByThreeNewsroomsAtOnceIsBreaking() {
        val list = listOf(
            a("ap", "Explosion reported at Port of Houston refinery", "apnews.com", ageMin = 12),
            a("rt", "Large explosion hits Houston refinery, port says", "reuters.com", ageMin = 18),
            a("cnn", "Houston refinery explosion: what we know", "cnn.com", ageMin = 25),
            a("x", "Senate schedules vote on farm bill", "npr.org", ageMin = 20),
        )
        val out = BreakingNews.detect(list, NOW, norm)
        assertEquals(1, out.size)
        assertTrue(out.first().title.contains("Houston"))
    }

    @Test fun followedSourcesNeedSeveralOutletsAndAlertsAreThrottled() {
        val clickbait = a("mf", "BREAKING: This stock could soar 300%", "fool.com", ageMin = 5)
        val wire = a("ap", "BREAKING: Earthquake strikes off Japan", "apnews.com", ageMin = 5)
        val trusted: (news.openwire.domain.model.Article) -> Boolean = { it.id == "ap" }
        val out = BreakingNews.detect(listOf(clickbait, wire), NOW, norm, trusted = trusted)
        assertEquals(listOf("ap"), out.map { it.id })
        // Within 20 minutes of the last alert only a major story gets through, two at most per run.
        val three = listOf(wire, a("b", "Storm hits coast", ageMin = 3), a("c", "Storm hits coast again", ageMin = 4))
        assertEquals(listOf("ap"), BreakingNews.throttle(three, NOW, NOW - 5 * 60_000, isMajor = { it.id == "ap" }).map { it.id })
        assertEquals(2, BreakingNews.throttle(three, NOW, NOW - 60 * 60_000, isMajor = { false }).size)
    }
}
