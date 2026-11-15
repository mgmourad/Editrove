// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsProcessorTest {

    private fun article(id: String = "1", title: String = "T", url: String? = null, publishedAt: Long? = null) =
        Article(id = id, title = title, originalUrl = url, publishedAt = publishedAt)

    @Test
    fun normalizeTitleStripsPublisherSuffixAndLowercases() {
        assertEquals("breaking tech development", NewsProcessor.normalizeTitleForComparison("Breaking Tech Development - The Guardian"))
        assertEquals("already clean", NewsProcessor.normalizeTitleForComparison("  Already   Clean  "))
        assertEquals("", NewsProcessor.normalizeTitleForComparison(""))
    }

    @Test
    fun titleSimilarityIsHighForNearIdenticalAndLowForUnrelated() {
        assertTrue(NewsProcessor.titleSimilarity("Breaking news today", "Breaking news today!") >= 0.68)
        assertEquals(1.0, NewsProcessor.titleSimilarity("Same exact headline", "Same exact headline"), 0.0)
        assertTrue(NewsProcessor.titleSimilarity("Weather forecast update", "Championship final result") < 0.68)
    }

    @Test
    fun isDuplicateMatchesByNormalizedUrl() {
        assertTrue(
            NewsProcessor.isDuplicate(
                article(url = "https://x.com/a"),
                article(url = "https://x.com/a/?utm=1"),
            ),
        )
    }

    @Test
    fun isDuplicateMatchesByHighTitleSimilarity() {
        assertTrue(
            NewsProcessor.isDuplicate(
                article(title = "Breaking news today"),
                article(title = "Breaking news today!"),
            ),
        )
        assertFalse(
            NewsProcessor.isDuplicate(
                article(title = "Weather forecast update"),
                article(title = "Championship final result"),
            ),
        )
    }

    @Test
    fun deduplicateCollapsesDuplicatesKeepingFirst() {
        val list = listOf(
            article(id = "a", title = "Breaking news today"),
            article(id = "b", title = "Breaking news today!"),
            article(id = "c", title = "Championship final result"),
        )
        val result = NewsProcessor.deduplicate(list)
        assertEquals(2, result.size)
        assertEquals("a", result[0].id)
        assertEquals("c", result[1].id)
    }

    @Test
    fun sortByDateOrdersNewestAndOldest() {
        val articles = listOf(
            article(id = "1", publishedAt = 10_000L),
            article(id = "2", publishedAt = 30_000L),
            article(id = "3", publishedAt = 20_000L),
        )
        assertEquals(listOf("2", "3", "1"), NewsProcessor.sortByDate(articles, SortDirection.NEWEST).map { it.id })
        assertEquals(listOf("1", "3", "2"), NewsProcessor.sortByDate(articles, SortDirection.OLDEST).map { it.id })
    }

    @Test
    fun parseDateHandlesIsoRfc1123AndAbbreviations() {
        assertNotNull(NewsProcessor.parseDate("2026-09-14T10:00:00Z"))
        assertNotNull(NewsProcessor.parseDate("Wed, 16 Sep 2026 23:05:16 GMT"))
        assertNotNull(NewsProcessor.parseDate("Wed, 16 Sep 2026 18:39:36 EDT"))
        // EDT == UTC-4, so 18:39:36 EDT must equal 22:39:36 GMT.
        assertEquals(
            NewsProcessor.parseDate("Wed, 16 Sep 2026 22:39:36 GMT"),
            NewsProcessor.parseDate("Wed, 16 Sep 2026 18:39:36 EDT"),
        )
        assertNull(NewsProcessor.parseDate("garbage"))
        assertNull(NewsProcessor.parseDate(null))
    }

    @Test
    fun formatRelativeTimeFormatsIntervals() {
        val now = 1_800_000_000_000L
        assertEquals("Just now", NewsProcessor.formatRelativeTime(now - 20_000, now))
        assertEquals("5m ago", NewsProcessor.formatRelativeTime(now - 5 * 60_000, now))
        assertEquals("3h ago", NewsProcessor.formatRelativeTime(now - 3 * 3_600_000, now))
        assertEquals("Yesterday", NewsProcessor.formatRelativeTime(now - 25 * 3_600_000, now))
        assertEquals("3d ago", NewsProcessor.formatRelativeTime(now - 3 * 86_400_000, now))
        assertEquals("", NewsProcessor.formatRelativeTime(null, now))
    }
}
