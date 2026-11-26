// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCatalogTest {
    private val now = 1_000_000_000_000L
    private fun v(id: String, kind: VideoChannel.Kind, ch: String, ageH: Int) = ShortVideo(id, id, ch, null, now - ageH * 3_600_000L, kind)

    @Test fun topStoriesMixesEveryFlavourAndOtherTabsKeepToTheirs() {
        val all = listOf(
            v("n1", VideoChannel.Kind.NEWS, "ABC", 1), v("n2", VideoChannel.Kind.NEWS, "ABC", 2), v("n3", VideoChannel.Kind.NEWS, "BBC", 3),
            v("s1", VideoChannel.Kind.SPORTS, "ESPN", 1), v("t1", VideoChannel.Kind.TECH, "Verge", 1),
            v("b1", VideoChannel.Kind.BUSINESS, "CNBC", 1), v("e1", VideoChannel.Kind.ENTERTAINMENT, "E!", 1),
            v("old", VideoChannel.Kind.NEWS, "CNN", 200),
        )
        val top = VideoCatalog.mix("TOP", all, now)
        assertEquals(listOf("n1", "s1", "t1", "b1", "e1"), top.take(5).map { it.id })
        assertTrue("old" !in top.map { it.id })
        // No channel twice in a row when another is available.
        assertTrue(top.zipWithNext().none { (a, b) -> a.channel == b.channel })
        assertEquals(listOf("s1"), VideoCatalog.mix("SPORTS", all, now).map { it.id })
    }

    @Test fun everyTabHasChannels() {
        listOf("TOP", "SPORTS", "TECHNOLOGY", "BUSINESS", "ENTERTAINMENT", "SCIENCE", "POLITICS", "WASHINGTON_DC").forEach {
            assertTrue(it, VideoCatalog.channelsFor(it).isNotEmpty())
        }
    }
}
