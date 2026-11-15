// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import news.openwire.domain.model.FeedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from tests/feedWeaver.test.mjs. */
class FeedWeaverTest {
    private val registry = DomainRegistry()
    private val norm: (String) -> String = { registry.normalizeDomain(it) }

    @Test
    fun normalizeArticleUrlStripsTrackingAndTrailingSlash() {
        assertEquals(
            "https://cbc.ca/news/world/story-123",
            FeedWeaver.normalizeArticleUrl("https://www.cbc.ca/news/world/story-123/?utm_source=rss&utm_medium=feed&ref=google"),
        )
    }

    @Test
    fun titleSimilarityIgnoresPublisherSuffix() {
        val s = FeedWeaver.calculateTitleSimilarity(
            "Bank of Canada cuts interest rates by 25 basis points - CBC News",
            "Bank of Canada Cuts Interest Rates by 25 Basis Points | Financial Post",
        )
        assertTrue("got $s", s >= 0.8)
        assertTrue(
            FeedWeaver.calculateTitleSimilarity(
                "Trudeau announces new funding for housing in Vancouver",
                "NASA unveils stunning new deep space galaxy image",
            ) < 0.2,
        )
    }

    @Test
    fun duplicatesByUrlOrHeadline() {
        val a = Article("1", "Earthquake strikes off coast of Japan", originalUrl = "https://www.bbc.com/news/world-12345?utm_source=rss")
        val b = Article("2", "Earthquake strikes off the coast of Japan - BBC News", originalUrl = "https://bbc.com/news/world-12345")
        assertTrue(FeedWeaver.isDuplicate(a, b, norm))
    }

    private fun art(id: String, domain: String) = Article(id, "Story $id about ${id.hashCode()} things", domain = domain, originalUrl = "https://$domain/$id")
    private fun src(name: String, max: Int) = FeedSource(id = name, name = name, url = "https://$name/rss", maxArticles = max)

    @Test
    fun keepsLeadStoriesAndIncludesSecondary() {
        val primary = (1..6).map { art("p$it", "site$it.com") }
        val woven = FeedWeaver.weave(primary, listOf(FeedWeaver.Group(src("bbc", 3), listOf(art("s1", "bbc.co.uk")))), norm)
        assertEquals("p1", woven[0].id)
        assertEquals("p2", woven[1].id)
        assertTrue(woven.any { it.id == "s1" })
    }

    @Test
    fun enforcesFairShareCap() {
        val primary = (1..10).map { art("p$it", "site$it.com") }
        val bbc = (1..6).map { art("b$it", "bbc.co.uk") }
        val woven = FeedWeaver.weave(primary, listOf(FeedWeaver.Group(src("bbc", 2), bbc)), norm)
        assertEquals(2, woven.count { it.domain == "bbc.co.uk" })
    }

    @Test
    fun neverThreeConsecutiveFromOneDomain() {
        val primary = (1..8).map { art("p$it", "same.com") }
        val other = (1..8).map { art("o$it", "other.com") }
        val woven = FeedWeaver.weave(primary, listOf(FeedWeaver.Group(src("other", 8), other)), norm, FeedWeaver.Options(leadAnchorCount = 0))
        for (i in 2 until woven.size) {
            val d = woven[i].domain
            assertFalse("3 in a row at $i", woven[i - 1].domain == d && woven[i - 2].domain == d && woven.subList(i, woven.size).any { it.domain != d })
        }
    }

    @Test
    fun enrichesLeadAnchorFromSamePublisherFeed() {
        val google = Article("hero1", "Three dead in Los Angeles helicopter crash - BBC", domain = "bbc.co.uk", originalUrl = "https://news.google.com/rss/articles/CBMi12345", imageUrl = "")
        val direct = Article(
            "bbc1", "Three killed after LA news helicopter crashes while covering deadly bus collision", domain = "bbc.co.uk",
            originalUrl = "https://www.bbc.co.uk/news/articles/cqx2zpj4y525o",
            imageUrl = "https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/2cc2/live/2332d760.jpg",
        )
        assertTrue(FeedWeaver.isDuplicate(google, direct, norm))
        val woven = FeedWeaver.weave(listOf(google), listOf(FeedWeaver.Group(src("BBC", 5), listOf(direct))), norm, FeedWeaver.Options(leadAnchorCount = 1))
        assertEquals("hero1", woven[0].id)
        assertEquals(direct.imageUrl, woven[0].imageUrl)
        assertEquals(direct.originalUrl, woven[0].originalUrl)
    }
}
