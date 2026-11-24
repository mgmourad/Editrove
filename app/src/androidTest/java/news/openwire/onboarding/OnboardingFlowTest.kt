// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Presenter
import news.openwire.domain.model.UserSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run setup, end to end on a device (English, phone language): the main path and Skip.
 * Each test starts from default settings with setup on screen.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<OpenWireApp>()
    private val settings get() = app.container.settingsRepository.settings.value
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun start() {
        // Animations off (the system setting setup honours): no ambient motion, instant camera cuts.
        val ui = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
            .forEach { ui.executeShellCommand("settings put global $it 0").close() }
        app.container.settingsRepository.replace(UserSettings())
        OnboardingGate.rerun = false
        OnboardingGate.showing = true
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun stop() { scenario?.close() }

    private fun tap(text: String) {
        val n = compose.onNodeWithText(text, substring = true)
        runCatching { n.performScrollTo() } // only inside the scrolling panel
        n.performClick()
    }

    private fun awaitHome() = compose.waitUntil(15_000) { !OnboardingGate.showing }

    @Test fun mainPathSavesEveryChoice() {
        tap("Start setup")
        tap("Type a city")
        compose.onNodeWithContentDescription("City or country").performTextInput("Chi")
        tap("Chicago")
        tap("Use this edition")
        // Two topics start on; a third is the minimum.
        compose.onAllNodesWithText("Tech").onFirst().also { runCatching { it.performScrollTo() } }.performClick()
        tap("Continue")
        // Sources: every catalog source of the chosen topics starts selected.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Which outlets do you trust?").fetchSemanticsNodes().isNotEmpty() }
        tap("Continue")
        tap("Correspondent")
        // No briefing time: no notification prompt.
        tap("Morning")
        tap("Build my front page")
        awaitHome()

        val s = settings
        assertEquals("Chicago", s.edition.city)
        assertFalse("TECHNOLOGY" in s.hiddenTopics)
        assertFalse(TopicRegistry.LOCAL_ID in s.hiddenTopics)
        assertTrue("SPORTS" in s.hiddenTopics)
        assertEquals(Presenter.CORRESPONDENT, s.briefing.presenter)
        assertTrue(s.briefing.slots.none { it.enabled })
        assertTrue(s.disabledSources.isEmpty())
    }

    @Test fun skipSavesTodaysDefaults() {
        tap("Skip")
        awaitHome()
        val s = settings
        assertEquals(UserSettings().edition, s.edition)
        assertTrue(s.hiddenTopics.isEmpty())
        assertTrue(s.disabledSources.isEmpty())
        assertEquals(Presenter.ANCHOR, s.briefing.presenter)
        assertTrue(s.briefing.slots.none { it.enabled })
    }
}
