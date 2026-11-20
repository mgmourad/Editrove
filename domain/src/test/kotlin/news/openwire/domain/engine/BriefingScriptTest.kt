// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.time.LocalDateTime
import news.openwire.domain.model.BriefingDetail
import news.openwire.domain.model.Presenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingScriptTest {
    private val story = BriefingScript.Story("Council approves transit plan", "Reuters", summary = "The plan adds three light rail lines by 2030 and is funded by a regional sales tax.", body = "x".repeat(10))

    @Test fun anchorIntroIsDatedTimeAwareAndCountsStories() {
        val s = BriefingScript.intro("Top Stories", 8, BriefingDetail.HEADLINES, LocalDateTime.of(2026, 11, 16, 8, 0))
        assertEquals("Good morning. Here's what you need to know as the day begins. It's Monday, November 16, and this is your Top Stories briefing from Editrove, with the top 8 headlines.", s)
        // Every other time, a classic open instead of the time of day.
        assertTrue(BriefingScript.intro("Top Stories", 8, BriefingDetail.HEADLINES, LocalDateTime.of(2026, 11, 16, 8, 0), seed = 1).startsWith("Good morning. Here are the latest headlines."))
    }

    @Test fun introsFitPresenterLengthAndWeekend() {
        val mon = LocalDateTime.of(2026, 11, 16, 13, 0)
        assertEquals("Here are today's top headlines. Your Top Stories briefing: a quick look at the top 3 headlines.",
            BriefingScript.intro("Top Stories", 3, BriefingDetail.HEADLINES, mon, presenter = Presenter.CORRESPONDENT))
        assertTrue(BriefingScript.intro("Top Stories", 8, BriefingDetail.SUMMARIES, mon, presenter = Presenter.MODERN).startsWith("Here's what's happening right now."))
        val sat = LocalDateTime.of(2026, 11, 21, 10, 0)
        assertTrue(BriefingScript.intro("Top Stories", 8, BriefingDetail.HEADLINES, sat, presenter = Presenter.CONVERSATIONAL).contains("weekend news update"))
        assertEquals(BriefingScript.Slot.NIGHT, BriefingScript.slot(LocalDateTime.of(2026, 11, 16, 23, 30)))
    }

    @Test fun handOffsFollowWhatIsTrueOfTheStory() {
        val local = story.copy(scope = BriefingScript.Scope.LOCAL)
        val world = story.copy(scope = BriefingScript.Scope.INTERNATIONAL)
        assertEquals("We begin with breaking news.", BriefingScript.lead(0, 5, BriefingDetail.HEADLINES, story = story.copy(breaking = true)))
        assertTrue(BriefingScript.lead(2, 5, BriefingDetail.HEADLINES, story = story.copy(relatedToPrevious = true), previous = story) in
            listOf("In a related development.", "On a related note.", "Staying with that story.", "Also on this subject."))
        assertTrue(BriefingScript.lead(2, 5, BriefingDetail.HEADLINES, story = world, previous = local) in
            listOf("Turning now to developments overseas.", "On the international front.", "Elsewhere around the world.", "Now to developments around the world."))
        assertEquals("In business news.", BriefingScript.lead(2, 5, BriefingDetail.HEADLINES, story = story.copy(category = BriefingScript.Category.BUSINESS), previous = story.copy(category = BriefingScript.Category.SPORTS)))
        // Same scope, same section: a neutral hand-off, never a "related" claim.
        assertTrue(BriefingScript.lead(2, 5, BriefingDetail.HEADLINES, story = local, previous = local) !in listOf("In a related development.", "On a related note."))
    }

    @Test fun mergedCoverageIsNamedNotReread() {
        val lines = BriefingScript.story(1, 5, story.copy(alsoReportedBy = listOf("AP", "BBC News", "CNN")), BriefingDetail.HEADLINES)
        assertEquals("The story is also being covered by AP and BBC News.", lines.last())
    }

    @Test fun everyLanguageHasEveryPresenter() {
        val now = LocalDateTime.of(2026, 11, 16, 20, 0)
        for (lang in listOf("en", "ar", "fr", "es", "de")) for (p in Presenter.entries) {
            assertTrue(BriefingScript.intro("X", 5, BriefingDetail.SUMMARIES, now, lang, p).isNotBlank())
            assertTrue(BriefingScript.outro("X", lang, p, now, breaking = true).isNotBlank())
            (0 until 5).forEach { i -> assertTrue(BriefingScript.story(i, 5, story.copy(scope = BriefingScript.Scope.values()[i % 3]), BriefingDetail.HEADLINES, lang, p, story).isNotEmpty()) }
        }
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
