// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import news.openwire.i18n.tr
import news.openwire.R
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
import kotlinx.coroutines.flow.first
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
    private val normalizeDomain: (String) -> String = { it },
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
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()
    /** Extra observers (home-screen widgets); called on the main thread like [onChange]. */
    fun addListener(l: () -> Unit) { listeners += l }
    private fun notifyChange() { onChange?.invoke(); listeners.forEach { it() } }

    /** Topic the loaded briefing is for (e.g. "Top Stories"). */
    val topicLabel: String get() = topic
    /** The stories in the loaded briefing, in order. */
    val chapters: List<news.openwire.domain.model.Article> get() = stories

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

    // Position inside the segment being spoken: its lines, the line being read and when it began.
    private var lines: List<String> = emptyList()
    /** Lines that continue a paragraph (short pause after the previous one, not an anchor pause). */
    private var softLines: Set<Int> = emptySet()
    private var lineIndex = 0
    private var lineStartedAt = 0L
    /** Line to resume from after a pause or seek. */
    private var resumeLine = 0

    private val audio = app.getSystemService(android.media.AudioManager::class.java)
    private val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(MEDIA)
        .setOnAudioFocusChangeListener { change -> if (change == android.media.AudioManager.AUDIOFOCUS_LOSS || change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) scope.launch { pause() } }
        .build()

    /** The language the current briefing is read in. */
    private var lang: String = "en"

    // Google's engine when installed: it holds the default narrator and downloadable voices
    // in every language (the phone's default may be another maker's engine).
    /** The engine in use (Google's, Editrove's own voice, or the phone's default); swapped when the language needs another. */
    private var engineName: String? = news.openwire.i18n.VoiceSetup.engine(app)
    private var tts: TextToSpeech = makeTts(engineName)

    /** Picks the engine for [language] before speaking (a Huawei tablet reads Arabic with Editrove's own voice). */
    private fun useEngineFor(language: String) {
        val want = news.openwire.i18n.VoiceSetup.engine(app, language)
        if (want == engineName) return
        runCatching { tts.stop(); tts.shutdown() }
        ready = false
        engineName = want
        tts = makeTts(want)
    }

    private fun makeTts(engine: String?): TextToSpeech = TextToSpeech(app, { status -> ready = status == TextToSpeech.SUCCESS }, engine).apply {
        setAudioAttributes(SPEECH)
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val u = parse(utteranceId) ?: return
                scope.launch {
                    if (u.session != session) return@launch
                    onSpeechStarted()
                    if (u.pos == pos && !u.silence) { lineIndex = u.line; lineStartedAt = android.os.SystemClock.elapsedRealtime(); notifyChange() }
                }
            }
            override fun onDone(utteranceId: String?) {
                val u = parse(utteranceId) ?: return
                if (u.last) scope.launch { if (u.session == session && u.pos == pos) advance() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val u = parse(utteranceId) ?: return
                scope.launch { if (u.session == session && u.pos == pos) advance() }
            }
        })
    }

    private data class Utterance(val session: Int, val pos: Int, val line: Int, val silence: Boolean, val last: Boolean)

    /** utteranceId = "b:session:segment:line:kind" (kind: s = speech, p = pause, e = final pause). */
    private fun parse(id: String?): Utterance? = id?.split(':')?.takeIf { it.size == 5 && it[0] == "b" }?.let {
        Utterance(it[1].toIntOrNull() ?: return null, it[2].toIntOrNull() ?: return null, it[3].toIntOrNull() ?: return null, it[4] != "s", it[4] == "e")
    }

    /** Starts a briefing for [topicLabel] from the given (already ordered) stories. */
    fun play(topicLabel: String, articles: List<news.openwire.domain.model.Article>) {
        stopAudio()
        val p = prefs()
        topic = topicLabel
        detail = p.detail
        // Every story in a briefing is a different story: no second outlet's take on one already read.
        stories = news.openwire.domain.engine.StoryIdentity.distinct(articles, p.count, normalizeDomain)
        // The anchor speaks the stories' language: Editrove's own, unless the stories are in another
        // script (an Arabic national tab while the app is in English).
        lang = news.openwire.domain.engine.Languages.effective(null).let { app ->
            if (app != "ar" && stories.count { RX_ARABIC.containsMatchIn(it.title) } * 2 > stories.size) "ar" else app
        }
        useEngineFor(lang)
        title = tr(R.string.briefing_title, topicLabel)
        content.clear()
        val built = mutableListOf<Pair<Seg, Int>>()
        built += Seg.Speech(news.openwire.domain.engine.BriefingScript.intro(topicLabel, stories.size, detail, java.time.LocalDateTime.now(), lang)) to -1
        stories.indices.forEach { i ->
            if (i > 0 && p.stings) built += Seg.Sting to i
            built += Seg.Story(i) to i
        }
        built += Seg.Speech(news.openwire.domain.engine.BriefingScript.outro(topicLabel, lang)) to stories.size
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

    /** Waits (up to [ms]) for the text-to-speech engine after a cold start. */
    suspend fun awaitReady(ms: Long = 6_000) {
        kotlinx.coroutines.withTimeoutOrNull(ms) { androidx.compose.runtime.snapshotFlow { ready }.first { it } }
    }

    fun toggle() = if (playing) pause() else resume()

    fun pause() {
        if (!playing) return
        resumeLine = if (segs.getOrNull(pos) is Seg.Speech) lineIndex else 0
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

    private fun jumpTo(i: Int, line: Int = 0) {
        resumeLine = line
        lineIndex = line
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
            k < 0 -> tr(R.string.briefing_opening)
            k >= stories.size -> tr(R.string.briefing_signoff)
            else -> tr(R.string.briefing_story_of, k + 1, stories.size)
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
        // Every level fetches the article: Headlines reads its subtitle too.
        if (extractor == null || i !in stories.indices || content.containsKey(i)) return
        val url = stories[i].originalUrl ?: return
        content[i] = scope.async(kotlinx.coroutines.Dispatchers.IO) { runCatching { extractor.extract(url) }.getOrNull() }
    }

    private fun advance() {
        resumeLine = 0; lineIndex = 0
        pos++
        markStory(pos)
        run()
    }

    private fun run() {
        if (!playing) return
        val seg = segs.getOrNull(pos) ?: run { finish(); return }
        val s = session
        when (seg) {
            is Seg.Speech -> { val from = resumeLine; resumeLine = 0; speak(seg.text, s, from) }
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
                            summary = a.summary,
                            body = c?.takeIf { !it.partial }?.text?.replace("\n\n", " "),
                            subtitle = c?.description,
                            // Same key points as the Summary box in the reader.
                            keyPoints = if (detail == news.openwire.domain.model.BriefingDetail.SUMMARIES) {
                                c?.let { news.openwire.domain.engine.Summarizer.keyPoints(it.text, 3) }.orEmpty()
                            } else emptyList(),
                        ),
                        detail,
                        lang,
                    )
                    segs[pos] = Seg.Speech(lines.joinToString(SENTENCE_BREAK))
                    run()
                }
            }
        }
    }

    private fun speak(text: String, s: Int, from: Int = 0) {
        // A freshly swapped engine starts up first.
        if (!ready) { scope.launch { awaitReady(); if (s == session) speakNow(text, s, from) }; return }
        speakNow(text, s, from)
    }

    private fun speakNow(text: String, s: Int, from: Int = 0) {
        val p = prefs()
        tts.setSpeechRate(p.speed)
        val all = runCatching { tts.voices }.getOrNull().orEmpty()
        (p.voice?.let { name -> all.firstOrNull { it.name == name && it.locale.language == lang } } ?: BriefingVoices.preferred(all, lang))
            ?.let { tts.voice = it } ?: run { tts.language = Locale(lang) }
        // Lines become separate utterances with short anchor pauses between them.
        val parsed = parseLines(text)
        val chunks = parsed.map { it.first }
        lines = chunks
        softLines = parsed.indices.filter { parsed[it].second }.toSet()
        val start = from.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        lineIndex = start
        lineStartedAt = android.os.SystemClock.elapsedRealtime()
        for (k in start until chunks.size) {
            tts.speak(chunks[k], if (k == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "b:$s:$pos:$k:s")
            val gap = when { k == chunks.lastIndex -> 250L; (k + 1) in softLines -> 90L; else -> 450L }
            tts.playSilentUtterance(gap, TextToSpeech.QUEUE_ADD, "b:$s:$pos:$k:${if (k == chunks.lastIndex) "e" else "p"}")
        }
        if (start >= chunks.size) advance()
    }

    private fun linesOf(text: String) = parseLines(text).map { it.first }

    /**
     * Script lines, with long passages (full articles) cut into groups of sentences so
     * seeking moves by roughly a sentence or two. second = continues the previous line.
     */
    private fun parseLines(text: String): List<Pair<String, Boolean>> = text.split(SENTENCE_BREAK).flatMap { line ->
        if (line.length <= 320) listOf(line to false)
        else {
            val groups = mutableListOf(StringBuilder())
            line.split(RX_1).forEach { sentence ->
                if (groups.last().isNotEmpty() && groups.last().length + sentence.length > 280) groups.add(StringBuilder())
                groups.last().append(sentence).append(' ')
            }
            groups.mapIndexed { i, g -> g.toString().trim() to (i > 0) }
        }
    }.filter { it.first.isNotBlank() }

    // ---- Timeline (for the widget's scrubber, ±15 s and elapsed / remaining) ---------------

    private fun wordsPerSecond() = 2.55f * prefs().speed.coerceIn(0.5f, 2.5f)
    private fun lineSeconds(line: String) = line.split(' ').count { it.isNotBlank() } / wordsPerSecond() + 0.45f

    /** Estimated length of segment [i] in seconds; stories not written yet use a typical length. */
    private fun segmentSeconds(i: Int): Float = when (val seg = segs.getOrNull(i)) {
        is Seg.Speech -> (if (i == pos && lines.isNotEmpty()) lines else linesOf(seg.text)).sumOf { lineSeconds(it).toDouble() }.toFloat()
        Seg.Sting -> STING_LEAD_MS / 1000f
        is Seg.Story -> when (detail) {
            news.openwire.domain.model.BriefingDetail.HEADLINES -> 14f
            news.openwire.domain.model.BriefingDetail.SUMMARIES -> 42f
            news.openwire.domain.model.BriefingDetail.FULL -> 210f
        } / prefs().speed.coerceIn(0.5f, 2.5f)
        null -> 0f
    }

    /** Seconds into the current segment. */
    private fun withinSegment(): Float {
        if (segs.getOrNull(pos) !is Seg.Speech || lines.isEmpty()) return 0f
        val k = (if (playing) lineIndex else resumeLine).coerceIn(0, lines.size)
        val before = lines.take(k).sumOf { lineSeconds(it).toDouble() }.toFloat()
        val into = if (playing) ((android.os.SystemClock.elapsedRealtime() - lineStartedAt) / 1000f).coerceIn(0f, lines.getOrNull(k)?.let(::lineSeconds) ?: 0f) else 0f
        return before + into
    }

    /** Estimated (elapsed, total) seconds of the whole briefing. */
    fun timeline(): Pair<Float, Float> {
        if (segs.isEmpty()) return 0f to 0f
        val total = segs.indices.sumOf { segmentSeconds(it).toDouble() }.toFloat()
        val elapsed = (0 until pos).sumOf { segmentSeconds(it).toDouble() }.toFloat() + withinSegment()
        return elapsed.coerceAtMost(total) to total
    }

    /** Moves [seconds] back or forward, by whole sentences, crossing into neighbouring stories as needed. */
    fun seekBy(seconds: Float) {
        if (segs.isEmpty()) return
        val (elapsed, _) = timeline()
        seekTo(elapsed + seconds)
    }

    /** Jumps to a point of the briefing given as a fraction of its estimated length. */
    fun seekToFraction(f: Float) {
        val (_, total) = timeline()
        seekTo(f.coerceIn(0f, 1f) * total)
    }

    private fun seekTo(target: Float) {
        if (segs.isEmpty()) return
        var t = target.coerceAtLeast(0f)
        var i = 0
        while (i < segs.size) {
            val len = segmentSeconds(i)
            if (t < len || i == segs.lastIndex) break
            t -= len; i++
        }
        if (i >= segs.size) { finish(); return }
        // A sting is a bridge: land on the story it introduces.
        if (segs[i] == Seg.Sting) { i++; t = 0f }
        val seg = segs.getOrNull(i) ?: run { finish(); return }
        val segLines = if (seg is Seg.Speech) (if (i == pos && lines.isNotEmpty()) lines else linesOf(seg.text)) else emptyList()
        var line = 0
        var acc = 0f
        while (line < segLines.size - 1 && acc + lineSeconds(segLines[line]) <= t) { acc += lineSeconds(segLines[line]); line++ }
        if (i != pos) markStory(i)
        jumpTo(i, line)
    }

    /** Starts story [k] of the loaded briefing right away. */
    fun jumpToStory(k: Int) {
        if (segs.isEmpty()) return
        val i = segs.indices.firstOrNull { segStory[it] == k && segs[it] != Seg.Sting } ?: return
        markStory(i)
        jumpTo(i)
        if (!playing) resume()
    }

    /** Re-reads the current sentence so a new speed takes effect immediately. */
    fun applySpeedNow() {
        if (playing && segs.getOrNull(pos) is Seg.Speech) jumpTo(pos, lineIndex) else notifyChange()
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
        val CUES = listOf(
            news.openwire.R.raw.news_opening_1, news.openwire.R.raw.news_opening_2, news.openwire.R.raw.news_opening_3, news.openwire.R.raw.news_opening_4,
            news.openwire.R.raw.news_opening_5, news.openwire.R.raw.news_opening_6, news.openwire.R.raw.news_opening_7, news.openwire.R.raw.news_opening_8,
            news.openwire.R.raw.news_opening_9, news.openwire.R.raw.news_opening_10, news.openwire.R.raw.news_opening_11,
        )
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
        SkipButton(Icons.Filled.SkipPrevious, tr(R.string.previous_story)) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); player.skipPrevious() }
        Box(
            Modifier.glowShadow(g.accent, 10.dp).size(40.dp).clip(CircleShape).background(g.accent)
                .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); player.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = tr(if (player.playing) R.string.cd_pause_briefing else R.string.briefing_play),
                tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
        SkipButton(Icons.Filled.SkipNext, tr(R.string.next_story)) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); player.skipNext() }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text((tr(R.string.audio_briefing_kicker) + "  ·  " + player.progress.uppercase()).trimEnd(' ', '·'), style = mono(9.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, maxLines = 1)
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
        ) { Icon(Icons.Filled.Close, contentDescription = tr(R.string.cd_close_player), tint = g.textSecondary, modifier = Modifier.size(18.dp)) }
    }
}


@Composable
internal fun SkipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = Glass.textPrimary, modifier = Modifier.size(20.dp)) }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("(?<=[.!?])\\s+")
private val RX_ARABIC = Regex("[\\u0600-\\u06FF]")

/**
 * The default narrator: "English (United States) · IOM" (Google's en-us-x-iom), on-device
 * first, then its network version, then another installed US English voice; else the
 * phone's own default.
 */
object BriefingVoices {
    const val DEFAULT_TAG = "IOM"

    fun preferred(voices: Collection<android.speech.tts.Voice>, lang: String = "en"): android.speech.tts.Voice? {
        val usable = voices.filter { !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
        if (lang != "en") {
            // Another language: its best installed voice, on-device first.
            val own = usable.filter { it.locale.language == lang }
            return own.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality } ?: own.maxByOrNull { it.quality }
        }
        return usable.firstOrNull { it.name.equals("en-us-x-iom-local", true) }
            ?: usable.firstOrNull { it.name.startsWith("en-us-x-iom", true) }
            ?: usable.filter { it.locale.language == "en" && it.locale.country == "US" && !it.isNetworkConnectionRequired }
                .maxByOrNull { it.quality }
    }
}
