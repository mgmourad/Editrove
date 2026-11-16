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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import news.openwire.ui.theme.Glass

/**
 * The Audio tab's briefing engine. It lives above the feed (not in a list item), so
 * scrolling never tears it down.
 *
 * Sound design: the opening cue starts at full volume and the anchor comes in a few
 * seconds later; from then on the cue sits under the voice (never louder than the
 * narrator) and fades out over the first half-minute. Between stories the transition
 * sting hits, then ducks as the next hand-off begins. Story text for Summaries and
 * Full articles is fetched one or two stories ahead.
 */
class BriefingPlayer(
    context: Context,
    private val prefs: () -> news.openwire.domain.model.BriefingPrefs,
    private val extractor: news.openwire.data.remote.ArticleTextExtractor?,
) {
    private val app = context.applicationContext
    var title by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    /** e.g. "Story 3 of 8". */
    var progress by mutableStateOf("")
        private set

    /** A briefing is loaded (playing or paused). */
    val active: Boolean get() = title != null

    /** Story being read (-1 = the open, == count = the sign-off). */
    var storyIndex by androidx.compose.runtime.mutableIntStateOf(-1)
        private set
    /** Headline of the story being read, for the lock screen. */
    var storyTitle by mutableStateOf<String?>(null)
        private set
    val storyCount: Int get() = stories.size

    /** Called on the main thread whenever playback state, position or story changes (media session). */
    var onChange: (() -> Unit)? = null
    private fun notifyChange() = onChange?.invoke()

    private sealed interface Seg {
        data class Speech(val text: String) : Seg
        data object Sting : Seg
        data class Story(val index: Int) : Seg
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var segs = mutableListOf<Seg>()
    /** Story index for each segment (open = -1, sign-off = stories.size), kept 1:1 with [segs]. */
    private var segStory = mutableListOf<Int>()
    private var storyStartedAt = 0L
    private var pos = 0
    private var session = 0
    private var stories: List<news.openwire.domain.model.Article> = emptyList()
    private var topic = ""
    private var detail = news.openwire.domain.model.BriefingDetail.HEADLINES
    private val content = HashMap<Int, kotlinx.coroutines.Deferred<news.openwire.data.remote.ArticleContent?>>()
    private var cue: android.media.MediaPlayer? = null
    private var sting: android.media.MediaPlayer? = null
    private var cueJob: kotlinx.coroutines.Job? = null
    private var stingJob: kotlinx.coroutines.Job? = null
    private var stepJob: kotlinx.coroutines.Job? = null
    private var narrationStarted = false

    private val audio = app.getSystemService(android.media.AudioManager::class.java)
    private val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(MEDIA)
        .setOnAudioFocusChangeListener { change -> if (change == android.media.AudioManager.AUDIOFOCUS_LOSS || change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { pause() } }
        .build()

    private val tts: TextToSpeech = TextToSpeech(app) { status -> ready = status == TextToSpeech.SUCCESS }.apply {
        setAudioAttributes(SPEECH)
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val (s, _, _) = parse(utteranceId) ?: return
                scope.launch { if (s == session) onSpeechStarted() }
            }
            override fun onDone(utteranceId: String?) {
                val (s, p, last) = parse(utteranceId) ?: return
                if (last) scope.launch { if (s == session && p == pos) advance() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val (s, p, _) = parse(utteranceId) ?: return
                scope.launch { if (s == session && p == pos) advance() }
            }
        })
    }

    /** utteranceId = "b:session:segment:last" */
    private fun parse(id: String?): Triple<Int, Int, Boolean>? = id?.split(':')?.takeIf { it.size == 4 && it[0] == "b" }?.let {
        val s = it[1].toIntOrNull() ?: return null
        val p = it[2].toIntOrNull() ?: return null
        Triple(s, p, it[3] == "1")
    }

    /** Starts a briefing for [topicLabel] from the given (already ordered) stories. */
    fun play(topicLabel: String, articles: List<news.openwire.domain.model.Article>) {
        stopAudio()
        val p = prefs()
        topic = topicLabel
        detail = p.detail
        stories = articles.take(p.count)
        title = "$topicLabel briefing"
        content.clear()
        val built = mutableListOf<Pair<Seg, Int>>()
        built += Seg.Speech(news.openwire.domain.engine.BriefingScript.intro(topicLabel, stories.size, detail, java.time.LocalDateTime.now())) to -1
        stories.indices.forEach { i ->
            if (i > 0 && p.stings) built += Seg.Sting to i
            built += Seg.Story(i) to i
        }
        built += Seg.Speech(news.openwire.domain.engine.BriefingScript.outro(topicLabel)) to stories.size
        segs = built.map { it.first }.toMutableList()
        segStory = built.map { it.second }.toMutableList()
        pos = 0
        narrationStarted = false
        prefetch(0); prefetch(1)
        audio?.requestAudioFocus(focus)
        playing = true
        session++
        markStory(0)
        BriefingService.start(app)
        val cueRes = CUES.getOrNull(p.openingCue - 1)
        if (cueRes != null) {
            cue = android.media.MediaPlayer.create(app, cueRes)?.apply { setVolume(1f, 1f); start() }
            val s = session
            stepJob = scope.launch { kotlinx.coroutines.delay(CUE_LEAD_MS); if (s == session) run() }
        } else run()
    }

    fun toggle() = if (playing) pause() else resume()

    fun pause() {
        if (!playing) return
        session++
        tts.stop()
        stepJob?.cancel()
        stingJob?.cancel(); sting?.release(); sting = null
        // The opening cue belongs to the open; it doesn't come back on resume.
        cueJob?.cancel(); cue?.release(); cue = null
        playing = false
        notifyChange()
    }

    fun resume() {
        if (segs.isEmpty() || playing) return
        audio?.requestAudioFocus(focus)
        playing = true
        session++
        // A sting is only a bridge — resume with the story it introduces.
        if (segs.getOrNull(pos) == Seg.Sting) pos++
        BriefingService.start(app)
        notifyChange()
        run()
    }

    /** Jump to the next story (through its transition sting). Past the last story: the sign-off. */
    fun skipNext() {
        if (segs.isEmpty()) return
        val next = segStory.indexOfFirst { it > storyIndex.coerceAtLeast(-1) }
        if (next < 0) { finish(); return }
        jumpTo(next)
    }

    /**
     * Restart the current story — or, within its first few seconds (or during the
     * open), go back to the previous one. Lands on the story itself, not its sting.
     */
    fun skipPrevious() {
        if (segs.isEmpty()) return
        val restart = storyIndex > 0 && android.os.SystemClock.elapsedRealtime() - storyStartedAt > RESTART_WINDOW_MS
        val target = if (restart || storyIndex <= 0) storyIndex.coerceAtLeast(-1) else storyIndex - 1
        val i = segs.indices.firstOrNull { segStory[it] == target && segs[it] != Seg.Sting } ?: 0
        jumpTo(i)
    }

    private fun jumpTo(i: Int) {
        session++
        tts.stop()
        stepJob?.cancel()
        stingJob?.cancel(); sting?.release(); sting = null
        pos = i
        markStory(i)
        if (playing) run() else notifyChange()
    }

    private fun markStory(i: Int) {
        val k = segStory.getOrNull(i) ?: return
        if (k != storyIndex) storyStartedAt = android.os.SystemClock.elapsedRealtime()
        storyIndex = k
        storyTitle = stories.getOrNull(k)?.title
        progress = when {
            k < 0 -> "Opening"
            k >= stories.size -> "Sign-off"
            else -> "Story ${k + 1} of ${stories.size}"
        }
        notifyChange()
    }

    /** Stops and unloads (closes the floating player). */
    fun stop() {
        stopAudio()
        title = null
        segs.clear(); segStory.clear()
        progress = ""
        storyTitle = null
        storyIndex = -1
        notifyChange()
    }

    private fun stopAudio() {
        session++
        tts.stop()
        stepJob?.cancel(); cueJob?.cancel(); stingJob?.cancel()
        cue?.release(); cue = null
        sting?.release(); sting = null
        content.values.forEach { it.cancel() }
        audio?.abandonAudioFocusRequest(focus)
        playing = false
    }

    private fun prefetch(i: Int) {
        if (detail == news.openwire.domain.model.BriefingDetail.HEADLINES || extractor == null || i !in stories.indices || content.containsKey(i)) return
        val url = stories[i].originalUrl ?: return
        content[i] = scope.async(kotlinx.coroutines.Dispatchers.IO) { runCatching { extractor.extract(url) }.getOrNull() }
    }

    private fun advance() {
        pos++
        markStory(pos)
        run()
    }

    private fun run() {
        if (!playing) return
        val seg = segs.getOrNull(pos) ?: run { finish(); return }
        val s = session
        when (seg) {
            is Seg.Speech -> speak(seg.text, s)
            Seg.Sting -> playSting(s)
            is Seg.Story -> {
                prefetch(seg.index + 1); prefetch(seg.index + 2)
                stepJob = scope.launch {
                    val a = stories[seg.index]
                    val c = content[seg.index]?.let { kotlinx.coroutines.withTimeoutOrNull(STORY_WAIT_MS) { it.await() } }
                    if (s != session) return@launch
                    val lines = news.openwire.domain.engine.BriefingScript.story(
                        seg.index, stories.size,
                        news.openwire.domain.engine.BriefingScript.Story(
                            title = a.title,
                            source = a.source,
                            summary = c?.description ?: c?.let { news.openwire.domain.engine.Summarizer.keyPoints(it.text, 2).joinToString(" ") } ?: a.summary,
                            body = c?.takeIf { !it.partial }?.text?.replace("\n\n", " "),
                        ),
                        detail,
                    )
                    segs[pos] = Seg.Speech(lines.joinToString(SENTENCE_BREAK))
                    run()
                }
            }
        }
    }

    private fun speak(text: String, s: Int) {
        val p = prefs()
        tts.setSpeechRate(p.speed)
        p.voice?.let { name -> runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull()?.let { tts.voice = it } }
        // Lines become separate utterances with short anchor pauses between them.
        val chunks = text.split(SENTENCE_BREAK).flatMap(::chunk).filter { it.isNotBlank() }
        chunks.forEachIndexed { k, c ->
            tts.speak(c, if (k == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "b:$s:$pos:0")
            tts.playSilentUtterance(if (k == chunks.lastIndex) 250 else 450, TextToSpeech.QUEUE_ADD, "b:$s:$pos:${if (k == chunks.lastIndex) 1 else 0}")
        }
        if (chunks.isEmpty()) advance()
    }

    /** Engine input is capped (~4000 chars): split long text on sentence boundaries. */
    private fun chunk(text: String): List<String> {
        val max = TextToSpeech.getMaxSpeechInputLength() - 200
        if (text.length <= max) return listOf(text)
        val out = mutableListOf(StringBuilder())
        text.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
            if (out.last().length + sentence.length > max) out.add(StringBuilder())
            out.last().append(sentence).append(' ')
        }
        return out.map { it.toString() }
    }

    private fun onSpeechStarted() {
        if (!narrationStarted) {
            narrationStarted = true
            // Duck the opening cue under the anchor, then let it fade away.
            cue?.let { player ->
                cueJob?.cancel()
                cueJob = scope.launch {
                    ramp(player, 1f, CUE_UNDER_VOICE, 1200)
                    ramp(player, CUE_UNDER_VOICE, 0f, CUE_FADE_MS)
                    player.release(); if (cue === player) cue = null
                }
            }
        }
        sting?.let { player ->
            stingJob?.cancel()
            stingJob = scope.launch { ramp(player, STING_UNDER_VOICE, 0f, 900); player.release(); if (sting === player) sting = null }
        }
    }

    private fun playSting(s: Int) {
        val player = android.media.MediaPlayer.create(app, R_TRANSITION) ?: return advance()
        sting?.release()
        sting = player
        player.setVolume(STING_VOLUME, STING_VOLUME)
        player.start()
        // Hand off to the next story while the sting is still ringing out.
        stepJob = scope.launch {
            kotlinx.coroutines.delay(STING_LEAD_MS)
            if (s != session) return@launch
            ramp(player, STING_VOLUME, STING_UNDER_VOICE, 300)
            advance()
        }
    }

    private suspend fun ramp(player: android.media.MediaPlayer, from: Float, to: Float, ms: Long) {
        val steps = (ms / 40).coerceAtLeast(1)
        for (i in 1..steps) {
            val v = from + (to - from) * i / steps
            runCatching { player.setVolume(v, v) }
            kotlinx.coroutines.delay(40)
        }
    }

    private fun finish() {
        stopAudio()
        segs.clear(); segStory.clear()
        progress = ""
        title = null
        storyTitle = null
        storyIndex = -1
        notifyChange()
    }

    fun shutdown() { stopAudio(); tts.shutdown() }

    companion object {
        private val MEDIA = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
        private val SPEECH = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val CUES = listOf(news.openwire.R.raw.news_opening_1, news.openwire.R.raw.news_opening_2, news.openwire.R.raw.news_opening_3, news.openwire.R.raw.news_opening_4)
        private val R_TRANSITION = news.openwire.R.raw.transition
        private const val SENTENCE_BREAK = "\u2029"
        /** How long the cue plays alone before the anchor comes in. */
        private const val CUE_LEAD_MS = 3800L
        /** Cue level under the narrator (the bundled cues are loudness-normalised below speech). */
        private const val CUE_UNDER_VOICE = 0.22f
        private const val CUE_FADE_MS = 28_000L
        private const val STING_VOLUME = 0.8f
        private const val STING_UNDER_VOICE = 0.25f
        private const val STING_LEAD_MS = 1600L
        private const val STORY_WAIT_MS = 7000L
        private const val RESTART_WINDOW_MS = 4000L
    }
}

val LocalBriefingPlayer = staticCompositionLocalOf<BriefingPlayer?> { null }

/** The app-wide briefing player (outlives screens so it keeps playing in the background). */
@Composable
fun rememberBriefingPlayer(): BriefingPlayer {
    val context = LocalContext.current
    return remember { (context.applicationContext as news.openwire.OpenWireApp).container.briefingPlayer }
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
        SkipButton(Icons.Filled.SkipPrevious, "Previous story") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); player.skipPrevious() }
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
        SkipButton(Icons.Filled.SkipNext, "Next story") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); player.skipNext() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(("AUDIO BRIEFING  ·  " + player.progress.uppercase()).trimEnd(' ', '·'), style = mono(9.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, maxLines = 1)
            Text(
                player.storyTitle ?: listOf(player.title.orEmpty(), player.progress).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
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


@Composable
internal fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Glass.textPrimary, modifier = Modifier.size(20.dp)) }
}
