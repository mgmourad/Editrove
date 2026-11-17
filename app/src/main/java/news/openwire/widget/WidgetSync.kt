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
    /** Topics a widget is refreshing right now (spinner on the refresh button). */
    var refreshing by androidx.compose.runtime.mutableStateOf(emptySet<String>())
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

    /** Player state changed: redraw the briefing widgets (coalesced). */
    fun audioChanged(context: Context) {
        val app = context.applicationContext
        audioPending?.cancel()
        audioPending = scope.launch {
            delay(120)
            WidgetSignals.audioTick++
            BriefWidgets.updateAll(app)
            if (has(app, TopicNewsWidget::class.java)) TopicNewsWidget().updateAll(app)
        }
    }

    /** Fetches a topic in the background if its cache is stale (e.g. a widget was just set to it). */
    fun refreshTopic(context: Context, topicId: String) {
        val app = context.applicationContext
        scope.launch {
            WidgetData.refreshIfStale(app, topicId)
            WidgetSignals.feedVersion++
            TopicNewsWidget().updateAll(app)
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
            BriefWidgets.updateAll(app)
        }
    }
}
