// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRankerTest {
    private val registry = DomainRegistry()
    private val norm: (String) -> String = { registry.normalizeDomain(it) }
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun a(id: String, title: String, domain: String, ageH: Double, url: String = "https://$domain/news/$id", rank: Int? = null, img: Boolean = true, coverage: Int? = null) =
        Article(
            id = id, title = title, source = domain, domain = domain, originalUrl = url,
            publishedAt = now - (ageH * hour).toLong(), imageUrl = if (img) "https://$domain/$id.jpg" else null,
            editorialRank = rank, coverage = coverage,
        )

    private fun ctx(topic: String = "TOP", all: List<Article> = emptyList()) =
        FeedRanker.Context(topic, now, coverageOf = FeedRanker.coverageIndex(all, norm), normalizeDomain = norm)

    @Test
    fun freshSportsStoryDoesNotDisplaceTheLeadOnTheFrontPage() {
        val feed = listOf(
            a("1", "Iran vows retaliation after strike on Tehran facility", "apnews.com", 3.0, rank = 0, coverage = 5),
            a("2", "Trump heads to Asia for trade summit", "politico.com", 4.0, rank = 1, coverage = 5),
            a("3", "Montreal Alouettes win Grey Cup in overtime thriller", "globalnews.ca", 0.2, url = "https://globalnews.ca/sports/cfl-grey-cup", rank = 6),
        )
        val ranked = FeedRanker.rank(feed, ctx(all = feed))
        assertEquals("1", ranked.first().id)
        assertNotEquals("3", ranked[1].id)
    }

    @Test
    fun widelyCoveredStoryOutranksASingleOutletExclusive() {
        val lead = a("solo", "Local bakery wins regional pastry award", "example-blog.com", 1.0, rank = 0)
        val big = a("big", "Senate passes sweeping budget bill after overnight vote", "reuters.com", 2.0, rank = 3)
        val echoes = listOf("cnn.com", "nytimes.com", "npr.org", "thehill.com", "axios.com").mapIndexed { i, d ->
            a("e$i", "Senate passes sweeping budget bill in overnight vote", d, 2.5, rank = 10 + i)
        }
        val all = listOf(lead, big) + echoes
        val ranked = FeedRanker.rank(listOf(lead, big), ctx(all = all))
        assertEquals("big", ranked.first().id)
    }

    @Test
    fun coverageCountsDistinctOutletsNotRepeats() {
        val x = a("x1", "Earthquake strikes northern Japan coast", "reuters.com", 1.0)
        val all = listOf(
            x,
            a("x2", "Powerful earthquake strikes northern Japan coast", "bbc.com", 1.0),
            a("x3", "Earthquake strikes northern Japan coast, tsunami warning", "bbc.com", 1.0),
            a("y", "Stocks rally as tech earnings beat forecasts", "cnbc.com", 1.0),
        )
        assertEquals(2, FeedRanker.coverageIndex(all, norm)(x))
    }

    @Test
    fun latestStaysChronological() {
        val feed = listOf(a("old", "Iran war update", "apnews.com", 5.0, rank = 0), a("new", "Grey Cup final score", "espn.com", 0.1, rank = 9))
        assertEquals(listOf("new", "old"), FeedRanker.rank(feed, ctx("LATEST")).map { it.id })
    }

    @Test
    fun sportsTopicIsNotPenalisedForSport() {
        val feed = listOf(
            a("g", "Grey Cup: Alouettes beat Roughriders", "espn.com", 1.0, url = "https://espn.com/cfl/story", rank = 0),
            a("o", "League announces new broadcast deal", "espn.com", 1.0, rank = 1),
        )
        assertEquals("g", FeedRanker.rank(feed, ctx("SPORTS")).first().id)
    }

    @Test
    fun leadStoryHasAPhotoWhenOneIsNearTheTop() {
        val feed = listOf(
            a("noimg", "President signs executive order on tariffs", "apnews.com", 1.0, rank = 0, img = false),
            a("img", "Congress debates tariff response", "nytimes.com", 1.5, rank = 1),
        )
        assertEquals("img", FeedRanker.rank(feed, ctx()).first().id)
    }

    @Test
    fun nearDuplicatesAreSpreadApart() {
        val feed = listOf(
            a("a", "Hurricane Delta makes landfall in Louisiana", "apnews.com", 1.0, rank = 0),
            a("b", "Hurricane Delta makes landfall in Louisiana as Category 3", "cnn.com", 1.0, rank = 1),
            a("c", "Supreme Court hears major voting rights case", "npr.org", 1.5, rank = 2),
        )
        val ids = FeedRanker.rank(feed, ctx(all = feed)).map { it.id }
        assertEquals("c", ids[1])
    }

    @Test
    fun everywhereMergesTopicsAndDropsRepeats() {
        val iran = a("t0", "Iran vows retaliation after strike", "apnews.com", 2.0)
        val feeds = mapOf(
            "TOP" to listOf(iran, a("t1", "Fed holds rates steady", "reuters.com", 3.0)),
            "INTERNATIONAL" to listOf(iran.copy(id = "i0"), a("i1", "Gaza ceasefire talks resume", "aljazeera.com", 4.0)),
            "SPORTS" to listOf(a("s0", "Grey Cup final score", "espn.com", 0.1, url = "https://espn.com/cfl/x")),
        )
        val out = FeedRanker.everywhere(feeds, now, norm)
        assertEquals(1, out.count { it.title.startsWith("Iran") })
        assertEquals("Iran vows retaliation after strike", out.first().title)
        assertTrue(out.any { it.id == "s0" })
    }
}
