// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicGraphTest {
    private fun h(t: String, s: String, d: String) = TopicGraph.Headline(t, s, d)

    @Test fun termsSharedAcrossKeywordsRankFirstAndChosenOnesAreNotRepeated() {
        val seeds = mapOf(
            "Smartphones" to listOf(
                h("Samsung unveils Galaxy S27 with new Exynos chip", "GSMArena", "gsmarena.com"),
                h("Apple iPhone 18 leak shows foldable design", "The Verge", "theverge.com"),
                h("Google Pixel 11 review: Tensor G6 impresses", "GSMArena", "gsmarena.com"),
                h("Samsung Galaxy foldable sales climb in Korea", "Reuters", "reuters.com"),
            ),
            "Samsung" to listOf(
                h("Samsung Galaxy S27 Ultra price leaks ahead of Unpacked", "GSMArena", "gsmarena.com"),
                h("Samsung Electronics profit jumps on memory chips", "Reuters", "reuters.com"),
                h("Galaxy S27 camera tested against Pixel 11", "The Verge", "theverge.com"),
            ),
        )
        val s = TopicGraph.suggest(seeds, listOf("Smartphones", "Samsung"))
        assertEquals("Galaxy S27", s.keywords.first())
        assertFalse(s.keywords.any { it.equals("Samsung", true) })
        assertEquals("gsmarena.com", s.outlets.first().domain)
        assertTrue(s.outlets.first().hits >= 3)
    }

    @Test fun arabicHeadlinesGiveContentWords() {
        val t = KeywordMiner.terms("الأهلي يفوز على الزمالك في نهائي الكأس - اليوم السابع", emptySet())
        assertTrue("الأهلي" in t && "الزمالك" in t)
        assertFalse("على" in t)
    }
}
