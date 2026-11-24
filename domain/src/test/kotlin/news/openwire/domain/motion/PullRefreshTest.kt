// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshTest {
    private fun run(vararg e: PullEvent, from: PullState = PullState()) = e.fold(from) { s, x -> PullMachine.reduce(s, x) }
    private val toReady = PullPhysics.dragFor(PullPhysics.READY) + 1f

    @Test fun resistanceFollowsTheCurve() {
        assertEquals(0f, PullPhysics.offset(0f), 0f)
        assertEquals(140f * (1 - kotlin.math.exp(-1f)), PullPhysics.offset(160f), .01f)
        assertTrue(PullPhysics.offset(600f) < 140f && PullPhysics.offset(10_000f) <= 140f)
        assertEquals(64f, PullPhysics.offset(PullPhysics.dragFor(64f)), .01f)
    }

    @Test fun fullCycle() {
        var s = run(PullEvent.Drag(40f))
        assertEquals(PullPhase.PULLING, s.phase)
        s = run(PullEvent.Drag(toReady), from = s)
        assertEquals(PullPhase.READY, s.phase)
        assertTrue(s.tick)
        s = run(PullEvent.Release, from = s)
        assertEquals(PullPhase.REFRESHING, s.phase)
        assertEquals(PullPhysics.HOLD, s.offset, .01f)
        s = run(PullEvent.RefreshDone, from = s)
        assertEquals(PullPhase.FINISHING, s.phase)
        s = run(PullEvent.Settled, from = s)
        assertEquals(PullState(), s)
    }

    @Test fun oneTickPerCrossingAndBackingOutIsSilent() {
        var s = run(PullEvent.Drag(toReady))
        assertTrue(s.tick)
        s = run(PullEvent.Drag(5f), from = s)
        assertFalse(s.tick) // still ready: no second tick
        s = run(PullEvent.Drag(-60f), from = s)
        assertEquals(PullPhase.PULLING, s.phase)
        assertFalse(s.tick) // undoing ready doesn't tick
        s = run(PullEvent.Drag(60f), from = s)
        assertTrue(s.tick) // a new crossing does
    }

    @Test fun releaseBelowTheLineCancels() {
        val s = run(PullEvent.Drag(50f), PullEvent.Release)
        assertEquals(PullState(), s)
        assertEquals(PullState(), run(PullEvent.Drag(30f), PullEvent.Drag(-80f)))
    }

    @Test fun dragsDuringARefreshDoNothing() {
        val s = run(PullEvent.RefreshStarted, PullEvent.Drag(200f), PullEvent.Release)
        assertEquals(PullPhase.REFRESHING, s.phase)
    }

    @Test fun newSince() {
        assertEquals(emptyList<String>(), NewSince.newIds(emptyList(), listOf("a", "b")))
        assertEquals(listOf("x", "y"), NewSince.newIds(listOf("a", "b"), listOf("x", "a", "y", "b")))
        assertEquals(emptyList<String>(), NewSince.newIds(listOf("a"), listOf("a")))
        // A different topic entirely: nothing is called "new".
        assertEquals(emptyList<String>(), NewSince.newIds(listOf("a"), listOf("p", "q")))
        assertEquals(NewSince.key("Fed holds rates — again"), NewSince.key("Fed Holds Rates, Again"))
    }
}
