// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.time.LocalDateTime
import news.openwire.domain.model.BriefingDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingScriptTest {
    private val story = BriefingScript.Story("Council approves transit plan", "Reuters", summary = "The plan adds three light rail lines by 2030 and is funded by a regional sales tax.", body = "x".repeat(10))

    @Test fun introIsDatedAndCountsStories() {
        val s = BriefingScript.intro("Top Stories", 8, BriefingDetail.HEADLINES, LocalDateTime.of(2026, 11, 15, 8, 0))
        assertEquals("Good morning. It's Sunday, November 15, and this is your Top Stories briefing from Editrove. Here are the top 8 headlines.", s)
    }

    @Test fun handOffsVaryAndEndWithFinally() {
        assertEquals("Our top story.", BriefingScript.lead(0, 5, BriefingDetail.HEADLINES))
        assertNotEquals(BriefingScript.lead(1, 5, BriefingDetail.HEADLINES), BriefingScript.lead(2, 5, BriefingDetail.HEADLINES))
        assertTrue(BriefingScript.lead(4, 5, BriefingDetail.SUMMARIES).contains("inally") || BriefingScript.lead(4, 5, BriefingDetail.SUMMARIES).contains("last"))
    }

    @Test fun summariesAttributeAndHandOffIntoDetails() {
        val lines = BriefingScript.story(1, 5, story, BriefingDetail.SUMMARIES)
        assertEquals("Council approves transit plan.", lines[1])
        assertTrue(lines.any { it.contains("Reuters") })
        assertTrue(lines.last().startsWith("The plan adds"))
    }

    @Test fun fullFallsBackToSummaryWhenBodyMissing() {
        val lines = BriefingScript.story(0, 3, story, BriefingDetail.FULL)
        assertTrue(lines.contains(story.summary))
        assertEquals("That report from Reuters.", lines.last())
    }

    @Test fun cleansUnspeakableText() {
        assertEquals("Read more. Photo", BriefingScript.clean("Read more https://x.y/z | Photo [Getty Images]"))
    }
}
