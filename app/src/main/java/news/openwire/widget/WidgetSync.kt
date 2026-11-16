// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import news.openwire.ui.home.BriefingPlayer

/**
 * Snapshot state the widgets read. Glance recomposes a live widget session when these
 * change, so the play button, scrubber and stories update in place (no full re-inflate,
 * no flicker); [WidgetSync] also wakes sessions that have gone to sleep.
 */
object WidgetSignals {
    var audioTick by mutableIntStateOf(0)
        internal set
    var feedVersion by mutableIntStateOf(0)
        internal set
}

/** Keeps the home-screen widgets in step with the briefing player and the feed cache. */
object WidgetSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var audioPending: Job? = null
    private var feedPending: Job? = null

    /** Wires the player to the audio widget. Called once from the Application. */
    fun attach(context: Context, player: BriefingPlayer) {
        val app = context.applicationContext
        player.addListener { audioChanged(app) }
    }

    private suspend fun has(context: Context, widget: Class<out GlanceAppWidget>) =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(widget).isNotEmpty() }.getOrDefault(false)

    /** Player state changed: redraw now (coalesced), and keep the clock moving while it plays. */
    fun audioChanged(context: Context) {
        val app = context.applicationContext
        audioPending?.cancel()
        audioPending = scope.launch {
            delay(120)
            if (!has(app, AudioBriefWidget::class.java)) return@launch
            WidgetSignals.audioTick++
            AudioBriefWidget().updateAll(app)
            val player = (app as news.openwire.OpenWireApp).container.briefingPlayer
            if (player.playing && ticker?.isActive != true) {
                ticker = scope.launch {
                    // Elapsed/remaining and the waveform frame advance every couple of seconds.
                    while (isActive && player.playing) {
                        delay(2_000)
                        WidgetSignals.audioTick++
                        AudioBriefWidget().updateAll(app)
                    }
                }
            }
        }
    }

    /** A topic's cached stories changed (refresh, background refresh, bookmarks). */
    fun feedsChanged(context: Context, delayMs: Long = 1_500) {
        val app = context.applicationContext
        feedPending?.cancel()
        feedPending = scope.launch {
            delay(delayMs)
            WidgetSignals.feedVersion++
            if (has(app, TopStoriesWidget::class.java)) TopStoriesWidget().updateAll(app)
            if (has(app, TopicWidget::class.java)) TopicWidget().updateAll(app)
            if (has(app, AudioBriefWidget::class.java)) AudioBriefWidget().updateAll(app)
        }
    }
}
