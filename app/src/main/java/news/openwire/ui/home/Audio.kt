// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import java.util.Locale
import news.openwire.ui.theme.Glass

/**
 * The Audio tab's briefing player. It lives above the feed (not inside a list
 * item), so scrolling never tears it down; pause/resume continue from the
 * current sentence.
 */
class BriefingPlayer(context: Context) {
    var title by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set

    /** A briefing is loaded (playing or paused). */
    val active: Boolean get() = title != null

    private var chunks: List<String> = emptyList()
    private var index by mutableIntStateOf(0)
    /** Bumps on every new play() so stale engine callbacks are ignored. */
    private var session = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                parse(utteranceId)?.let { (s, i) -> if (s == session) index = i }
            }
            override fun onDone(utteranceId: String?) {
                val (s, i) = parse(utteranceId) ?: return
                if (s == session && i == chunks.lastIndex) { playing = false; index = 0 }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (parse(utteranceId)?.first == session) playing = false
            }
        })
    }

    private fun parse(id: String?): Pair<Int, Int>? = id?.split(':')?.takeIf { it.size == 3 }?.let { it[1].toIntOrNull()?.let { s -> it[2].toIntOrNull()?.let { i -> s to i } } }

    /** Starts (or restarts) a briefing. */
    fun play(title: String, text: String) {
        this.title = title
        chunks = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        index = 0
        speakFrom(0)
    }

    fun toggle() = if (playing) pause() else resume()

    fun pause() {
        session++
        tts.stop()
        playing = false
    }

    fun resume() {
        if (chunks.isEmpty()) return
        speakFrom(index.coerceIn(0, chunks.lastIndex))
    }

    /** Stops and unloads (closes the floating player). */
    fun stop() {
        pause()
        title = null
        chunks = emptyList()
        index = 0
    }

    private fun speakFrom(start: Int) {
        if (!ready) return
        session++
        tts.language = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= 0 } ?: Locale.US
        chunks.drop(start).forEachIndexed { k, c ->
            tts.speak(c, if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "ew:$session:${start + k}")
        }
        playing = true
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}

val LocalBriefingPlayer = staticCompositionLocalOf<BriefingPlayer?> { null }

@Composable
fun rememberBriefingPlayer(): BriefingPlayer {
    val context = LocalContext.current
    val player = remember { BriefingPlayer(context) }
    DisposableEffect(player) { onDispose { player.shutdown() } }
    return player
}

/**
 * Floating mini-player shown while a briefing is loaded and its card is off
 * screen: play/pause, a live waveform, the briefing title and close.
 */
@Composable
fun FloatingPlayerPill(player: BriefingPlayer, haze: HazeState?, modifier: Modifier = Modifier) {
    val g = Glass
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .then(
                if (haze != null) Modifier.glassSurface(CircleShape, fill = Color.Transparent, elevation = 18.dp).frostedGlass(haze)
                else Modifier.glassSurface(CircleShape, fill = g.glassElevated, elevation = 18.dp),
            )
            .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.glowShadow(g.accent, 10.dp).size(40.dp).clip(CircleShape).background(g.accent)
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); player.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (player.playing) "Pause briefing" else "Play briefing",
                tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("AUDIO BRIEFING", style = mono(9.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft)
            Text(player.title.orEmpty(), style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.height(16.dp)) { Waveform(player.playing) }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable { player.stop() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, contentDescription = "Close player", tint = g.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}
