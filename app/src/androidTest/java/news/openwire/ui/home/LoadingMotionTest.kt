// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.data.repository.RefreshEvent
import news.openwire.onboarding.OnboardingGate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pull to refresh below and above the line, and opening and closing a story (real app, real feed). */
@RunWith(AndroidJUnit4::class)
class LoadingMotionTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<OpenWireApp>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val events = CopyOnWriteArrayList<RefreshEvent>()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @Before fun start() {
        // Animations off: the app's own infinite motion (the live bar) stays still, so Compose can idle.
        val ui = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
            .forEach { ui.executeShellCommand("settings put global $it 0").close() }
        OnboardingGate.markDone(app); OnboardingGate.showing = false
        scope.launch { app.container.feedRepository.refreshEvents.collect { events += it } }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // The home screen never stops moving (the live bar): drive Compose's clock by hand.
        compose.mainClock.autoAdvance = false
        // The first load (and its own refresh) settles before we pull.
        settle(30_000) { runBlocking { app.container.feedRepository.cachedArticles("TOP") }.isNotEmpty() }
        settle(1500) { false }
        events.clear()
    }

    /** Advances frames (and real time) until [done], or for [ms]. */
    private fun settle(ms: Long, done: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (done()) return
            compose.mainClock.advanceTimeBy(32)
            Thread.sleep(16)
        }
    }

    @After fun stop() { scope.cancel(); scenario?.close() }

    private fun pull(distancePx: Float) = compose.onRoot().performTouchInput {
        val y0 = height * .30f
        swipeDown(startY = y0, endY = y0 + distancePx, durationMillis = 900)
    }.also { settle(200) { false } }

    private fun pulled() = events.any { it is RefreshEvent.Started && it.topicId == "TOP" }

    @Test fun pullBelowTheLineDoesNotRefresh() {
        val density = app.resources.displayMetrics.density
        pull(40f * density) // 40 dp of drag ≈ 32 dp of travel: short of the 72 dp line
        settle(2500) { pulled() }
        assertTrue("a short pull must not refresh", !pulled())
    }

    @Test fun pullPastTheLineRefreshesEverySource() {
        val density = app.resources.displayMetrics.density
        pull(260f * density) // ≈ 117 dp of travel: past the line
        settle(10_000) { pulled() }
        val started = events.filterIsInstance<RefreshEvent.Started>().first { it.topicId == "TOP" }
        // Every source reports in (or times out), then this refresh finishes.
        settle(30_000) { events.any { it is RefreshEvent.Finished && it.refreshId == started.refreshId } }
        val answered = events.count { (it is RefreshEvent.Returned || it is RefreshEvent.Failed) && it.refreshId == started.refreshId }
        assertTrue("sources answered: $answered of ${started.sources.size}", answered >= started.sources.size)
    }

    @Test fun openAndCloseAStory() {
        val lead = runBlocking { app.container.feedRepository.cachedArticles("TOP") }.first()
        settle(10_000) { compose.onAllNodesWithText(lead.title, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(lead.title, substring = true).onFirst().performClick()
        // Cover screen: the sheet with "Back to Feed"; unfolded: the story beside the feed.
        settle(10_000) {
            compose.onAllNodesWithText("Back to Feed").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(lead.title).fetchSemanticsNodes().size > 1
        }
        val sheet = compose.onAllNodesWithText("Back to Feed").fetchSemanticsNodes().isNotEmpty()
        if (sheet) {
            compose.onAllNodesWithText("Back to Feed").onFirst().performClick()
            settle(10_000) { compose.onAllNodesWithText("Back to Feed").fetchSemanticsNodes().isEmpty() }
            assertTrue("the sheet closes", compose.onAllNodesWithText("Back to Feed").fetchSemanticsNodes().isEmpty())
        }
    }
}
