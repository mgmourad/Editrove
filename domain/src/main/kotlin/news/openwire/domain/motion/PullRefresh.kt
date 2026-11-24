// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.motion

import kotlin.math.exp
import kotlin.math.ln

/** Pull-to-refresh physics ("the rule and the wire"): all values in dp. */
object PullPhysics {
    /** The furthest the content can move (the asymptote). */
    const val MAX = 140f
    /** Resistance length: the higher, the softer. */
    const val SOFTNESS = 160f
    /** Where the rule reaches both edges and release refreshes. */
    const val READY = 72f
    /** Where the band holds while refreshing. */
    const val HOLD = 64f

    /** offset = 140 × (1 − e^(−drag / 160)): heavier the further you drag. */
    fun offset(drag: Float): Float = if (drag <= 0f) 0f else MAX * (1f - exp(-drag / SOFTNESS))

    /** The drag that gives [offset] (inverse of [offset]). */
    fun dragFor(offset: Float): Float = if (offset <= 0f) 0f else -SOFTNESS * ln(1f - (offset / MAX).coerceAtMost(.999f))

    /** 0 at rest, 1 at the ready line. */
    fun progress(offset: Float): Float = (offset / READY).coerceIn(0f, 1f)
}

enum class PullPhase { IDLE, PULLING, READY, REFRESHING, FINISHING }

/** One pull: the raw drag, its phase, and whether this step crossed into ready (the one haptic tick). */
data class PullState(val phase: PullPhase = PullPhase.IDLE, val drag: Float = 0f, val tick: Boolean = false) {
    val offset: Float get() = PullPhysics.offset(drag)
}

sealed interface PullEvent {
    /** A finger moved by [delta] dp (positive = down). */
    data class Drag(val delta: Float) : PullEvent
    data object Release : PullEvent
    /** A refresh began some other way (the status line's refresh, or one already running). */
    data object RefreshStarted : PullEvent
    /** Every source has answered or timed out. */
    data object RefreshDone : PullEvent
    /** The finish motion ended and the band is closed. */
    data object Settled : PullEvent
}

object PullMachine {
    fun reduce(s: PullState, e: PullEvent): PullState = when (e) {
        is PullEvent.Drag -> when (s.phase) {
            PullPhase.IDLE, PullPhase.PULLING, PullPhase.READY -> {
                val drag = (s.drag + e.delta).coerceAtLeast(0f)
                val ready = PullPhysics.offset(drag) >= PullPhysics.READY
                val phase = when { drag == 0f -> PullPhase.IDLE; ready -> PullPhase.READY; else -> PullPhase.PULLING }
                // One tick per crossing into ready; backing out of it is silent.
                PullState(phase, drag, tick = phase == PullPhase.READY && s.phase != PullPhase.READY)
            }
            else -> s.copy(tick = false)
        }
        PullEvent.Release -> when (s.phase) {
            PullPhase.READY -> PullState(PullPhase.REFRESHING, PullPhysics.dragFor(PullPhysics.HOLD))
            PullPhase.PULLING -> PullState() // below the line: cancelled
            else -> s.copy(tick = false)
        }
        PullEvent.RefreshStarted -> if (s.phase == PullPhase.REFRESHING || s.phase == PullPhase.FINISHING) s.copy(tick = false)
            else PullState(PullPhase.REFRESHING, PullPhysics.dragFor(PullPhysics.HOLD))
        PullEvent.RefreshDone -> if (s.phase == PullPhase.REFRESHING) s.copy(phase = PullPhase.FINISHING, tick = false) else s.copy(tick = false)
        PullEvent.Settled -> if (s.phase == PullPhase.FINISHING) PullState() else s.copy(tick = false)
    }
}

/** Stories that arrived since the previous refresh. */
object NewSince {
    /**
     * The ids in [fresh] that weren't in [previous]. Nothing is "new" on a first load
     * (no previous copy) or when every story changed (a different topic or edition).
     */
    fun newIds(previous: Collection<String>, fresh: List<String>): List<String> {
        if (previous.isEmpty()) return emptyList()
        val old = previous.toHashSet()
        val added = fresh.filter { it !in old }
        return if (added.size == fresh.size) emptyList() else added
    }
}
