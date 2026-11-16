// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import news.openwire.OpenWireApp
import news.openwire.R
import news.openwire.domain.model.Article

/**
 * "The Editrove Daily Brief" — the flagship audio widget. It drives the app's real
 * briefing player: play/pause, ±15 s, speed (1.0× / 1.25× / 1.5×), a tap-to-seek
 * scrubber and a chapter queue where tapping a story jumps straight to it.
 */
class AudioBriefWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { AudioBrief() }
    }
}

class AudioBriefWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AudioBriefWidget()
}

/** What the widget shows: the loaded briefing, or the Top Stories briefing that Play would start. */
private data class BriefUi(
    val active: Boolean,
    val playing: Boolean,
    val topic: String,
    val chapters: List<Article>,
    /** Chapter being read; -1 during the open. */
    val current: Int,
    val elapsed: Float,
    val total: Float,
    val speed: Float,
)

@Composable
private fun AudioBrief() {
    val context = LocalContext.current
    val app = context.applicationContext as OpenWireApp
    val player = app.container.briefingPlayer
    // Snapshot state: Glance recomposes when these change.
    @Suppress("UNUSED_VARIABLE") val tick = WidgetSignals.audioTick
    val feedVersion = WidgetSignals.feedVersion
    val settings by app.container.settingsRepository.settings.collectAsState()
    val prefs = settings.briefing
    val loaded by produceState<List<Article>?>(null, feedVersion) { value = WidgetData.articles(context, "TOP").take(prefs.count) }
    val upcoming = loaded.orEmpty()

    val ui = if (player.active) {
        val (elapsed, total) = player.timeline()
        BriefUi(true, player.playing, player.topicLabel, player.chapters, player.storyIndex, elapsed, total, prefs.speed)
    } else {
        val per = when (prefs.detail) {
            news.openwire.domain.model.BriefingDetail.HEADLINES -> 14f
            news.openwire.domain.model.BriefingDetail.SUMMARIES -> 42f
            news.openwire.domain.model.BriefingDetail.FULL -> 210f
        } / prefs.speed
        BriefUi(false, false, "Top Stories", upcoming, 0, 0f, upcoming.size * per + 20f, prefs.speed)
    }

    val size = LocalSize.current
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_glass)).cornerRadius(W.SHELL)) {
        if (!ui.active && loaded == null) { Loading(); return@Box }
        if (!ui.active && upcoming.isEmpty()) { Empty("Open Editrove once to load today's briefing."); return@Box }
        when (SizeClass.of(size)) {
            SizeClass.COMPACT -> Compact(ui)
            SizeClass.WIDE -> Wide(ui, size.width)
            SizeClass.EXPANDED -> Expanded(ui, size.width, size.height, split = false)
            SizeClass.FOLD -> Expanded(ui, size.width, size.height, split = true)
        }
    }
}

private fun chapterNumber(i: Int) = "%02d".format((i + 1).coerceAtLeast(1))
private fun BriefUi.title(): String = chapters.getOrNull(current.coerceAtLeast(0))?.title ?: "$topic briefing"
private fun BriefUi.fraction(): Float = if (total <= 0f) 0f else (elapsed / total).coerceIn(0f, 1f)
private fun speedLabel(s: Float) = when { s >= 1.45f -> "1.5×"; s >= 1.2f -> "1.25×"; else -> "1.0×" }

@Composable
private fun Compact(ui: BriefUi) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp).clickable(actionStartActivity(WidgetData.openApp(LocalContext.current, tab = "AUDIO")))) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Monogram(24.dp)
            Spacer(GlanceModifier.defaultWeight())
            Pill("BRIEF", W.amber, W.amberChip)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(if (ui.active && ui.current >= 0) "CHAPTER ${chapterNumber(ui.current)}" else if (ui.active) "THE OPEN" else "READY · ${ui.chapters.size} STORIES", style = mono(10.sp, W.textSecondary))
        Text(ui.title(), style = serif(14.sp), maxLines = 3, modifier = GlanceModifier.padding(top = 2.dp))
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(ui.playing, actionRunCallback<PlayPauseAction>(), diameter = 36.dp)
            Spacer(GlanceModifier.defaultWeight())
            Wave(bars = 10, width = 64.dp, height = 22.dp, ui = ui, gradientBars = true)
        }
    }
}

@Composable
private fun Wide(ui: BriefUi, width: Dp) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Monogram(24.dp)
            Text("  THE EDITROVE DAILY BRIEF", style = mono(11.sp, W.textSecondary, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Pill(speedLabel(ui.speed), W.text, W.chip, actionRunCallback<SpeedAction>(), size = 10.sp)
            Text("  -${clock(ui.total - ui.elapsed)}", style = mono(10.sp, W.textSecondary))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    if (ui.active && ui.current >= 0) "NOW PLAYING · ${chapterNumber(ui.current)} OF ${"%02d".format(ui.chapters.size)}" else if (ui.active) "NOW PLAYING · THE OPEN" else "${ui.topic.uppercase()} · ${ui.chapters.size} STORIES",
                    style = mono(10.sp, W.amber, bold = true), maxLines = 1,
                )
                Text(ui.title(), style = serif(17.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp, bottom = 8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(clock(ui.elapsed), style = mono(10.sp, W.textSecondary))
                    Spacer(GlanceModifier.width(8.dp))
                    Scrubber(ui, width = width - 32.dp - 140.dp - 48.dp)
                }
            }
            Spacer(GlanceModifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundControl("-15", actionRunCallback<SkipAction>(actionParametersOf(SkipAction.SECONDS to -15f)))
                Spacer(GlanceModifier.width(4.dp))
                PlayButton(ui.playing, actionRunCallback<PlayPauseAction>(), diameter = 44.dp)
                Spacer(GlanceModifier.width(4.dp))
                RoundControl("+15", actionRunCallback<SkipAction>(actionParametersOf(SkipAction.SECONDS to 15f)))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Expanded(ui: BriefUi, width: Dp, height: Dp, split: Boolean) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Monogram(26.dp)
            Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                Text("The Editrove Daily Brief", style = sans(13.sp, W.text, bold = true), maxLines = 1)
                Text("${ui.topic} · ${ui.chapters.size} stories · on-device voice", style = mono(10.sp, W.textMuted), maxLines = 1)
            }
            Pill(speedLabel(ui.speed), W.text, W.chip, actionRunCallback<SpeedAction>(), size = 11.sp)
        }
        Spacer(GlanceModifier.height(8.dp))
        val inner = width - 36.dp
        if (split) {
            val left = inner * 0.46f
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                Box(GlanceModifier.width(left).fillMaxHeight()) { Console(ui, left - 12.dp, compactWave = height < 340.dp) }
                Box(GlanceModifier.width(1.dp).fillMaxHeight().background(W.divider)) {}
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 16.dp)) { Queue(ui) }
            }
        } else {
            Console(ui, inner, compactWave = height < 380.dp)
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(W.divider)) {}
            Spacer(GlanceModifier.height(8.dp))
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) { Queue(ui) }
        }
    }
}

/** The active player: chapter, waveform, scrubber, transport. */
@Composable
private fun Console(ui: BriefUi, width: Dp, compactWave: Boolean) {
    Column(GlanceModifier.fillMaxWidth().padding(end = 4.dp)) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                if (ui.active && ui.current >= 0) "CHAPTER ${chapterNumber(ui.current)} · ${"%02d".format(ui.chapters.size)}" else if (ui.active) "THE OPEN" else "READY TO PLAY",
                style = mono(10.sp, W.amber, bold = true), modifier = GlanceModifier.defaultWeight(), maxLines = 1,
            )
            val head0 = ui.chapters.getOrNull(ui.current.coerceAtLeast(0))
            Text(
                listOfNotNull(if (ui.playing) "● LIVE" else if (ui.active) "PAUSED" else null, (head0?.source ?: head0?.domain)?.uppercase()).joinToString(" · "),
                style = mono(10.sp, W.textMuted), maxLines = 1,
            )
        }
        val head = ui.chapters.getOrNull(ui.current.coerceAtLeast(0))
        Text(
            if (head != null) "${chapterNumber(ui.current)} · ${head.title}" else "${ui.topic} briefing",
            style = serif(17.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 3.dp),
        )
        Spacer(GlanceModifier.height(6.dp))
        Box(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card)).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Wave(bars = 36, width = width - 20.dp, height = if (compactWave) 20.dp else 28.dp, ui = ui)
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(clock(ui.elapsed), style = mono(10.sp, W.textSecondary))
            Spacer(GlanceModifier.width(8.dp))
            Scrubber(ui, width = width - 100.dp)
            Spacer(GlanceModifier.width(8.dp))
            Text("-${clock(ui.total - ui.elapsed)}", style = mono(10.sp, W.textSecondary))
        }
        Spacer(GlanceModifier.height(2.dp))
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Pill("-15s", W.text, W.chip, actionRunCallback<SkipAction>(actionParametersOf(SkipAction.SECONDS to -15f)), size = 11.sp)
            Spacer(GlanceModifier.width(14.dp))
            PlayButton(ui.playing, actionRunCallback<PlayPauseAction>(), diameter = 40.dp)
            Spacer(GlanceModifier.width(14.dp))
            Pill("+15s", W.text, W.chip, actionRunCallback<SkipAction>(actionParametersOf(SkipAction.SECONDS to 15f)), size = 11.sp)
        }
        Spacer(GlanceModifier.height(6.dp))
    }
}

/** "Up Next in Briefing": tap any chapter to jump there. */
@Composable
private fun Queue(ui: BriefUi) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text("UP NEXT IN BRIEFING", style = mono(10.sp, W.textSecondary, bold = true), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            Text("Tap to jump", style = mono(10.sp, W.textMuted))
        }
        // The chapter playing comes first, then what's next; earlier chapters follow.
        val start = if (ui.active) ui.current.coerceIn(0, (ui.chapters.size - 1).coerceAtLeast(0)) else 0
        val order = (ui.chapters.indices.drop(start) + ui.chapters.indices.take(start)).map { it to ui.chapters[it] }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(order, itemId = { it.first.toLong() }) { (i, a) ->
                val current = ui.active && i == ui.current
                Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Row(
                        GlanceModifier.fillMaxWidth()
                            .background(ImageProvider(if (current) R.drawable.widget_card_active else R.drawable.widget_card))
                            .cornerRadius(16.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .clickable(actionRunCallback<ChapterAction>(actionParametersOf(ChapterAction.INDEX to i))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(chapterNumber(i), style = mono(11.sp, if (current) W.amber else W.textMuted, bold = true))
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(a.title, style = serif(13.sp, bold = current), maxLines = 1)
                            Text((a.source ?: a.domain ?: "").uppercase(), style = mono(9.sp, W.textMuted), maxLines = 1)
                        }
                        if (current) Text(if (ui.playing) "  ▶" else "  ❚❚", style = mono(10.sp, W.amber, bold = true))
                    }
                }
            }
        }
    }
}

@Composable
private fun Wave(bars: Int, width: Dp, height: Dp, ui: BriefUi, gradientBars: Boolean = false) {
    val d = LocalContext.current.resources.displayMetrics.density
    val bmp = WidgetArt.waveform(bars, (width.value * d).toInt(), (height.value * d).toInt(), ui.fraction(), ui.playing, (ui.elapsed / 2).toInt(), gradientBars)
    Image(ImageProvider(bmp), contentDescription = null, modifier = GlanceModifier.width(width).height(height))
}

/** Progress bar with ten tap zones: tapping a zone seeks there. */
@Composable
private fun Scrubber(ui: BriefUi, width: Dp) {
    val ctx = LocalContext.current
    val d = ctx.resources.displayMetrics.density
    val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val w = width.coerceAtLeast(40.dp)
    Box(GlanceModifier.width(w).height(18.dp), contentAlignment = Alignment.Center) {
        Image(ImageProvider(WidgetArt.progress((w.value * d).toInt(), (5 * d).toInt(), ui.fraction(), night)), contentDescription = "Briefing progress", contentScale = ContentScale.FillBounds, modifier = GlanceModifier.width(w).height(5.dp))
        Row(GlanceModifier.fillMaxSize()) {
            for (z in 0 until 10) {
                Box(GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<SeekAction>(actionParametersOf(SeekAction.FRACTION to (z + 0.5f) / 10f)))) {}
            }
        }
    }
}

// ---- Actions ---------------------------------------------------------------------------

private fun player(context: Context) = (context.applicationContext as OpenWireApp).container.briefingPlayer

/** Starts the Top Stories briefing from the cache when nothing is loaded. */
private suspend fun ensureLoaded(context: Context): Boolean {
    val p = player(context)
    if (p.active) return true
    val stories = WidgetData.articles(context, "TOP")
    if (stories.isEmpty()) return false
    withContext(Dispatchers.Main) { p.play("Top Stories", stories) }
    return true
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val p = player(context)
        if (!p.active) {
            if (!ensureLoaded(context)) context.startActivity(WidgetData.openApp(context, tab = "AUDIO"))
        } else withContext(Dispatchers.Main) { p.toggle() }
        WidgetSync.audioChanged(context)
    }
}

class SkipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!player(context).active) return
        val s = parameters[SECONDS] ?: return
        withContext(Dispatchers.Main) { player(context).seekBy(s) }
        WidgetSync.audioChanged(context)
    }
    companion object { val SECONDS = ActionParameters.Key<Float>("seconds") }
}

class SeekAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!player(context).active) return
        val f = parameters[FRACTION] ?: return
        withContext(Dispatchers.Main) { player(context).seekToFraction(f) }
        WidgetSync.audioChanged(context)
    }
    companion object { val FRACTION = ActionParameters.Key<Float>("fraction") }
}

class ChapterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val k = parameters[INDEX] ?: return
        if (!ensureLoaded(context)) return
        withContext(Dispatchers.Main) { player(context).jumpToStory(k) }
        WidgetSync.audioChanged(context)
    }
    companion object { val INDEX = ActionParameters.Key<Int>("index") }
}

/** Cycles 1.0× → 1.25× → 1.5× and applies it to the sentence being read. */
class SpeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = (context.applicationContext as OpenWireApp).container.settingsRepository
        val now = repo.settings.value.briefing.speed
        val next = when { now < 1.2f -> 1.25f; now < 1.45f -> 1.5f; else -> 1.0f }
        repo.update { it.copy(briefing = it.briefing.copy(speed = next)) }
        withContext(Dispatchers.Main) { player(context).applySpeedNow() }
        WidgetSync.audioChanged(context)
    }
}
