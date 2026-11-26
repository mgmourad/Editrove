// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.foryou

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForYouTest {
    private fun c(id: String, title: String, topic: String = "TOP", domain: String = "x.com") = ForYou.Candidate(topic, Article(id, title, domain = domain, publishedAt = 0L))

    @Test fun picksStoriesLikeTheLovedOnes() {
        val loved = listOf(ForYou.Loved("Nvidia unveils new AI chips for data centers", "theverge.com"), ForYou.Loved("OpenAI releases new reasoning model", null))
        val pool = listOf(
            c("1", "Nvidia shares jump after AI chip demand surges"),
            c("2", "Local bakery wins regional bread award"),
            c("3", "OpenAI and Microsoft expand data center deal"),
            c("4", "Nvidia unveils new AI chips for data centres"), // the loved story itself, elsewhere
        )
        val out = ForYou.recommend(loved, pool, now = 0L).map { it.id }
        assertTrue("1" in out && "3" in out)
        assertTrue("2" !in out)
        assertTrue("4" !in out)
    }

    @Test fun nothingLovedNothingRecommended() {
        assertEquals(emptyList<Article>(), ForYou.recommend(emptyList(), listOf(c("1", "Anything at all happening today"))))
    }
}
