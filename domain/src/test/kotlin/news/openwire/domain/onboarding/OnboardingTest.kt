// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.onboarding

import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.BriefingSlot
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.Presenter
import news.openwire.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {
    private val catalog = listOf(
        FeedSource("a", "A", "https://a.test/rss", topics = listOf("TOP")),
        FeedSource("b", "B", "https://b.test/rss", topics = listOf("TOP", "TECHNOLOGY")),
        FeedSource("c", "C", "https://c.test/rss", topics = listOf("TECHNOLOGY")),
        FeedSource("d", "D", "https://d.test/rss", topics = listOf("SPORTS")),
    )
    private val ctx = OnboardingContext(
        lineup = { p -> TopicRegistry.defaultTopics(p?.toEdition() ?: LocalEdition.DEFAULT, "en").map { it.id } },
        sourcesFor = { id -> catalog.filter { id in it.topics }.map { it.url } },
    )
    private val cairo = Place("Cairo", "Cairo", "EG")

    private fun run(vararg events: OnboardingEvent, from: OnboardingState = OnboardingState()) =
        events.fold(from) { s, e -> Onboarding.reduce(s, e, ctx) }

    @Test fun forwardPathWithGuards() {
        var s = run(OnboardingEvent.Next)
        assertEquals(Step.EDITION, s.step)
        // No edition yet: Next does nothing.
        assertEquals(Step.EDITION, run(OnboardingEvent.Next, from = s).step)
        s = run(OnboardingEvent.TypeCity, OnboardingEvent.ChooseEdition(cairo), OnboardingEvent.Next, from = s)
        assertEquals(Step.TOPICS, s.step)
        assertEquals(listOf("TOP", TopicRegistry.LOCAL_ID), s.choices.topics)
        // Two topics: below the minimum of three.
        assertEquals(Step.TOPICS, run(OnboardingEvent.Next, from = s).step)
        s = run(OnboardingEvent.ToggleTopic("TECHNOLOGY"), OnboardingEvent.Next, from = s)
        assertEquals(Step.SOURCES, s.step)
        // Every catalog source of the chosen topics starts selected (the app's default).
        assertEquals(setOf("https://a.test/rss", "https://b.test/rss", "https://c.test/rss"), s.choices.sources)
        s = run(OnboardingEvent.Next, OnboardingEvent.SetPresenter(Presenter.MODERN), OnboardingEvent.Next, from = s)
        assertEquals(Step.FINISHING, s.step)
        assertEquals(Step.DONE, run(OnboardingEvent.Finished, from = s).step)
    }

    @Test fun backKeepsChoicesAndReseedingDoesNotUndoRemovals() {
        var s = run(OnboardingEvent.Next, OnboardingEvent.TypeCity, OnboardingEvent.ChooseEdition(cairo), OnboardingEvent.Next,
            OnboardingEvent.ToggleTopic("TECHNOLOGY"), OnboardingEvent.Next, OnboardingEvent.ToggleSource("https://c.test/rss"))
        s = run(OnboardingEvent.Back, from = s)
        assertEquals(Step.TOPICS, s.step)
        s = run(OnboardingEvent.ToggleTopic("SPORTS"), OnboardingEvent.Next, from = s)
        // Sports is new: its source joins. Tech's removed source stays removed.
        assertTrue("https://d.test/rss" in s.choices.sources)
        assertFalse("https://c.test/rss" in s.choices.sources)
        assertEquals(cairo, s.choices.edition)
    }

    @Test fun lateLocationResultIsIgnored() {
        var s = run(OnboardingEvent.Next, OnboardingEvent.UseLocation)
        assertEquals(EditionMode.LOCATING, s.editionMode)
        s = run(OnboardingEvent.Back, from = s) // gave up waiting
        s = run(OnboardingEvent.LocationFound(cairo), from = s)
        assertNull(s.choices.edition)
        val failed = run(OnboardingEvent.Next, OnboardingEvent.UseLocation, OnboardingEvent.LocationFailed(LocationProblem.DENIED))
        assertEquals(EditionMode.TYPING, failed.editionMode)
        assertEquals(LocationProblem.DENIED, failed.problem)
    }

    @Test fun keywordTopicsAreCappedAtThree() {
        var s = run(OnboardingEvent.Next, OnboardingEvent.TypeCity, OnboardingEvent.ChooseEdition(cairo), OnboardingEvent.Next)
        listOf("NFL", "Formula 1", "Tennis", "Golf").forEach { s = run(OnboardingEvent.AddKeywordTopic(KeywordTopic(it, emptyList())), from = s) }
        assertEquals(3, s.choices.keywordTopics.size)
        assertTrue("kw:Tennis" in s.choices.topics)
        assertFalse("kw:Golf" in s.choices.topics)
    }

    @Test fun skipSavesTodaysDefaultsButKeepsWhatWasChosen() {
        val early = run(OnboardingEvent.Skip)
        assertEquals(Step.FINISHING, early.step)
        assertEquals(Place.of(LocalEdition.DEFAULT), early.choices.edition)
        assertEquals(ctx.lineup(early.choices.edition), early.choices.topics)
        assertEquals(catalog.map { it.url }.toSet(), early.choices.sources)
        assertEquals(Presenter.ANCHOR, early.choices.presenter)
        assertTrue(early.choices.slots.isEmpty())
        val late = run(OnboardingEvent.Next, OnboardingEvent.TypeCity, OnboardingEvent.ChooseEdition(cairo), OnboardingEvent.Skip)
        assertEquals(cairo, late.choices.edition)
    }

    @Test fun savedSettingsMatchTheChoices() {
        val c = Choices(
            edition = cairo, topics = listOf("TOP", TopicRegistry.LOCAL_ID, "TECHNOLOGY", "kw:NFL"),
            keywordTopics = listOf(KeywordTopic("NFL", listOf("NFL", "football"))),
            sources = setOf("https://a.test/rss", "https://b.test/rss"),
            feeds = listOf(AddedFeed("Mine", "https://mine.test/feed")),
            presenter = Presenter.CORRESPONDENT, slots = setOf(BriefingSlot.Kind.EVENING),
        )
        val out = Onboarding.apply(UserSettings(), c, catalog, 1_000L)
        assertEquals("Cairo", out.edition.city)
        assertEquals(1, out.customTopics.size)
        val nfl = out.customTopics.single()
        assertEquals(listOf("NFL", "football"), nfl.keywords)
        assertFalse(nfl.id in out.hiddenTopics)
        assertTrue("SPORTS" in out.hiddenTopics && "BUSINESS" in out.hiddenTopics)
        assertFalse("TOP" in out.hiddenTopics)
        // Tech's unselected source is switched off; Sports' (not a chosen topic) is left alone.
        assertEquals(setOf("https://c.test/rss"), out.disabledSources)
        assertTrue(out.customFeeds.any { it.url == "https://mine.test/feed" })
        assertEquals(Presenter.CORRESPONDENT, out.briefing.presenter)
        assertEquals(listOf(false, false, true), out.briefing.slots.map { it.enabled })
        // Running it again starts from these settings.
        val again = Onboarding.choicesFrom(out, catalog)
        assertEquals(Place("Cairo", "Cairo", "EG"), again.edition)
        assertTrue(nfl.id in again.topics && "TECHNOLOGY" in again.topics && "SPORTS" !in again.topics)
        assertEquals(setOf(BriefingSlot.Kind.EVENING), again.slots)
    }

    @Test fun existingUsersAreNotOnboarded() {
        assertFalse(Onboarding.isExistingUser(UserSettings(), updatedInstall = false))
        assertTrue(Onboarding.isExistingUser(UserSettings(), updatedInstall = true))
        assertTrue(Onboarding.isExistingUser(UserSettings(hiddenTopics = setOf("SPORTS")), updatedInstall = false))
    }

    @Test fun stateSurvivesProcessDeath() {
        val s = run(OnboardingEvent.Next, OnboardingEvent.TypeCity, OnboardingEvent.ChooseEdition(cairo), OnboardingEvent.Next)
        assertEquals(s, Onboarding.decode(Onboarding.encode(s)))
    }
}
