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

    private val rich = story.copy(
        subtitle = "The vote clears the way for the region's biggest transit expansion in decades",
        keyPoints = listOf(
            "The council voted 9 to 2 on Tuesday night after six hours of public comment.",
            "Construction on the first line is expected to begin in 2027.",
            "Opponents say the sales tax falls hardest on low-income residents.",
        ),
    )

    @Test fun headlinesReadTheHeadlineAndSubtitle() {
        val lines = BriefingScript.story(1, 5, rich, BriefingDetail.HEADLINES)
        assertEquals("Council approves transit plan.", lines[1])
        assertEquals("The vote clears the way for the region's biggest transit expansion in decades.", lines[2])
        assertTrue(lines.last().contains("Reuters"))
        assertTrue(lines.none { it.startsWith("Construction") }) // no key points in Headlines
    }

    @Test fun summariesReadSubtitleThenTheSummaryKeyPoints() {
        val lines = BriefingScript.story(1, 5, rich, BriefingDetail.SUMMARIES)
        assertEquals("Council approves transit plan.", lines[1])
        assertTrue(lines[2].startsWith("The vote clears the way"))
        assertTrue(lines.any { it.contains("Reuters") })
        assertEquals(rich.keyPoints, lines.takeLast(3))
    }

    @Test fun summariesWithoutSubtitleFallBackToExcerptAndStillReadKeyPoints() {
        val lines = BriefingScript.story(1, 5, rich.copy(subtitle = null), BriefingDetail.SUMMARIES)
        assertTrue(lines[2].startsWith("The plan adds"))
        assertEquals(rich.keyPoints, lines.takeLast(3))
    }

    @Test fun keyPointRepeatingTheSubtitleIsSkipped() {
        val s = rich.copy(keyPoints = listOf("The vote clears the way for the region's biggest transit expansion in decades.") + rich.keyPoints.drop(1))
        val lines = BriefingScript.story(1, 5, s, BriefingDetail.SUMMARIES)
        assertEquals(1, lines.count { it.startsWith("The vote clears") })
        assertEquals(rich.keyPoints.drop(1), lines.takeLast(2))
    }

    @Test fun longSubtitleIsTrimmedToTwoSentences() {
        val s = rich.copy(subtitle = "First sentence here today. Second sentence follows it. Third one is too much.")
        assertEquals("First sentence here today. Second sentence follows it.", BriefingScript.story(0, 3, s, BriefingDetail.HEADLINES)[2])
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
