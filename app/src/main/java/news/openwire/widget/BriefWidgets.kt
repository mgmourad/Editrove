// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import news.openwire.OpenWireApp
import news.openwire.R
import news.openwire.domain.engine.StoryIdentity
import news.openwire.domain.model.Article
import news.openwire.domain.model.BriefingDetail
import news.openwire.ui.home.BriefingService

/**
 * The audio-briefing widgets: **Daily Brief** (Top Stories) and **Topic of Choice** (one
 * topic picked when the widget is added). Both are the app's Audio Briefing card on the
 * home screen — same fonts (Newsreader, Inter, JetBrains Mono), colours and waveform —
 * with play/pause, ±15 s, previous/next story and speed. Plain RemoteViews layouts so the
 * app's fonts and the launcher-animated waveform work; buttons go straight to the
 * playback service.
 */
object BriefWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshing = mutableSetOf<Int>()

    const val ACTION_SPEED = "news.editrove.widget.SPEED"
    const val ACTION_REFRESH = "news.editrove.widget.REFRESH"
    private const val PREFS = "brief_widgets"

    // ---- Which topic each widget shows -----------------------------------------------------

    fun topicFor(context: Context, provider: Class<*>, id: Int): String =
        if (provider == AudioBriefWidgetReceiver::class.java) "TOP"
        else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("topic_$id", null) ?: "TOP"

    fun setTopic(context: Context, id: Int, topicId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("topic_$id", topicId).apply()

    fun forget(context: Context, ids: IntArray) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { ids.forEach { remove("topic_$it") } }.apply()

    // ---- Rendering --------------------------------------------------------------------------

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        for (cls in listOf(AudioBriefWidgetReceiver::class.java, TopicWidgetReceiver::class.java)) {
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(app, cls)) }.getOrNull() ?: continue
            ids.forEach { update(app, cls, it) }
        }
    }

    fun update(context: Context, provider: Class<*>, id: Int) {
        val app = context.applicationContext
        scope.launch {
            val topicId = topicFor(app, provider, id)
            val topic = WidgetData.topics(app).firstOrNull { it.id == topicId }
            val label = topic?.label ?: "Top Stories"
            val norm: (String) -> String = { (app as OpenWireApp).container.domainRegistry.normalizeDomain(it) }
            val prefs = (app as OpenWireApp).container.settingsRepository.settings.value.briefing
            val cached = withContext(Dispatchers.Default) { StoryIdentity.distinct(WidgetData.articles(app, topicId), prefs.count, norm) }
            val views = build(app, provider, id, topicId, label, cached, showRefresh = provider == TopicWidgetReceiver::class.java)
            runCatching { AppWidgetManager.getInstance(app).updateAppWidget(id, views) }
        }
    }

    private enum class Shape { SMALL, WIDE, TALL }

    /** One layout per size the launcher reports (portrait/landscape), each drawn at that width. */
    private fun build(context: Context, provider: Class<*>, id: Int, topicId: String, label: String, cached: List<Article>, showRefresh: Boolean): RemoteViews {
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        @Suppress("DEPRECATION")
        val sizes: List<SizeF> = (if (Build.VERSION.SDK_INT >= 31) opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES) else null)
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(SizeF(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400).toFloat().takeIf { it > 0 } ?: 400f, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200).toFloat().takeIf { it > 0 } ?: 200f))
        fun shapeOf(sz: SizeF) = when { sz.width < 250f -> Shape.SMALL; sz.height >= 300f -> Shape.TALL; else -> Shape.WIDE }
        if (Build.VERSION.SDK_INT < 31) return fill(context, provider, id, topicId, label, cached, showRefresh, shapeOf(sizes.first()), sizes.first())
        val map = LinkedHashMap<SizeF, RemoteViews>()
        sizes.distinct().take(2).forEach { sz -> map[sz] = fill(context, provider, id, topicId, label, cached, showRefresh, shapeOf(sz), sz) }
        return if (map.size == 1) map.values.first() else RemoteViews(map)
    }

    private fun fill(
        context: Context, provider: Class<*>, id: Int, topicId: String, label: String,
        cached: List<Article>, showRefresh: Boolean, shape: Shape, size: SizeF,
    ): RemoteViews {
        val app = context.applicationContext as OpenWireApp
        val player = app.container.briefingPlayer
        val prefs = app.container.settingsRepository.settings.value.briefing
        val mine = player.active && player.topicLabel == label
        val playing = mine && player.playing
        val stories = if (mine) player.chapters else cached
        val count = stories.size
        val compact = shape == Shape.SMALL
        val layout = when (shape) { Shape.SMALL -> R.layout.widget_brief_small; Shape.WIDE -> R.layout.widget_brief; Shape.TALL -> R.layout.widget_brief_tall }
        val v = RemoteViews(context.packageName, layout)
        val d = context.resources.displayMetrics.density
        val content = ((size.width - when (shape) { Shape.SMALL -> 28f; Shape.WIDE -> 32f; Shape.TALL -> 36f }) * d).toInt()
        // Short widgets drop the subtitle; the progress moves into the header label.
        val short = shape == Shape.WIDE && size.height < 235f
        fun text(viewId: Int, value: String, face: WidgetText.Face, sp: Float, width: Int, lines: Int = 1, lineHeight: Float? = null, tracking: Float = 0f) {
            v.setImageViewBitmap(viewId, WidgetText.render(context, value, face, sp, width, lines, lineHeight, tracking))
            v.setContentDescription(viewId, value)
        }

        // Header: label, refresh (Topic widget), detail · speed pill (tap cycles the speed).
        val speed = "%.2g".format(prefs.speed).trimEnd('0').trimEnd('.') + "×"
        val speedText = if (compact) speed else "${prefs.detail.label} · $speed"
        text(R.id.brief_speed_text, speedText, WidgetText.Face.SANS_MEDIUM, 11f, (160 * d).toInt())
        val headerSpace = content - ((if (showRefresh) 34 else 0) + 40 + speedText.length * 7) * d
        val headerBase = if (showRefresh) label.uppercase() else if (compact) "BRIEFING" else "AUDIO BRIEFING"
        val headerProgress = if ((short || compact) && mine && player.storyIndex in 0 until count) " · ${player.storyIndex + 1} OF $count" else ""
        text(R.id.brief_label, headerBase + headerProgress, WidgetText.Face.MONO_SEMIBOLD, 10f, headerSpace.toInt().coerceAtLeast((60 * d).toInt()), tracking = 0.16f)
        v.setOnClickPendingIntent(R.id.brief_speed, broadcast(context, provider, ACTION_SPEED, id))
        val busy = id in refreshing
        v.setViewVisibility(R.id.brief_refresh, if (showRefresh && !busy) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.brief_refreshing, if (showRefresh && busy) View.VISIBLE else View.GONE)
        if (showRefresh) v.setOnClickPendingIntent(R.id.brief_refresh, broadcast(context, provider, ACTION_REFRESH, id))

        // Title and subtitle, as in the app (Newsreader 22/26, Inter 12/18 at 60%).
        val title = when {
            count == 0 -> "$label briefing"
            prefs.detail == BriefingDetail.HEADLINES -> "$label in $count headlines"
            prefs.detail == BriefingDetail.SUMMARIES -> "$label: $count stories, summarised"
            else -> "$label: $count stories in full"
        }
        if (compact) text(R.id.brief_title, title, WidgetText.Face.SERIF_BOLD, 17f, content, lines = 2, lineHeight = 20f)
        else text(R.id.brief_title, title, WidgetText.Face.SERIF_BOLD, 22f, content, lines = 2, lineHeight = 26f)
        if (!compact) {
            val sub = when {
                mine && player.progress.isNotBlank() -> player.progress + if (playing) " · playing" else " · paused"
                count == 0 -> "Open Editrove once to load ${label.lowercase()}."
                else -> "Read by your on-device voice like a news broadcast — nothing leaves your phone."
            }
            // Short widgets keep the subtitle to one line so the controls never get squeezed.
            if (short) v.setViewVisibility(R.id.brief_sub, View.GONE)
            else text(R.id.brief_sub, sub, WidgetText.Face.SANS, 12f, content, lines = 2, lineHeight = 18f)
        }
        v.setOnClickPendingIntent(R.id.brief_title, PendingIntent.getActivity(
            context, id * 16 + 9, WidgetData.openApp(context, tab = "AUDIO", topic = topicId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ))

        // Play pill and transport (previous / −15 / +15 / next appear once it's this briefing).
        v.setImageViewResource(R.id.brief_play_icon, if (playing) R.drawable.bic_pause_white else R.drawable.bic_play_white)
        if (!compact) text(R.id.brief_play_text, if (playing) "Pause" else if (mine) "Resume" else "Play briefing", WidgetText.Face.SANS_SEMIBOLD, 14f, (160 * d).toInt())
        v.setContentDescription(R.id.brief_play, if (playing) "Pause briefing" else "Play briefing")
        v.setOnClickPendingIntent(R.id.brief_play, service(context, id, 1, BriefingService.ACTION_PLAY_TOPIC) { putTopic(topicId, label) })
        val transport = if (mine) View.VISIBLE else View.GONE
        v.setViewVisibility(R.id.brief_prev, transport)
        v.setViewVisibility(R.id.brief_next, transport)
        v.setOnClickPendingIntent(R.id.brief_prev, service(context, id, 2, BriefingService.ACTION_PREV))
        v.setOnClickPendingIntent(R.id.brief_next, service(context, id, 3, BriefingService.ACTION_NEXT))
        if (!compact) {
            v.setViewVisibility(R.id.brief_back, transport)
            v.setViewVisibility(R.id.brief_fwd, transport)
            if (mine) {
                v.setImageViewBitmap(R.id.brief_back, WidgetText.render(context, "−15", WidgetText.Face.MONO_SEMIBOLD, 12f, (40 * d).toInt()))
                v.setImageViewBitmap(R.id.brief_fwd, WidgetText.render(context, "+15", WidgetText.Face.MONO_SEMIBOLD, 12f, (40 * d).toInt()))
            }
            v.setOnClickPendingIntent(R.id.brief_back, service(context, id, 4, BriefingService.ACTION_SEEK) { putExtra(BriefingService.EXTRA_SECONDS, -15f) })
            v.setOnClickPendingIntent(R.id.brief_fwd, service(context, id, 5, BriefingService.ACTION_SEEK) { putExtra(BriefingService.EXTRA_SECONDS, 15f) })
        }
        // Waveform: animated by the launcher while playing (the app's own motion), still otherwise.
        v.setViewVisibility(R.id.brief_wave_live, if (playing) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.brief_wave_rest, if (mine && !playing) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.brief_wave_idle, if (!mine) View.VISIBLE else View.GONE)

        if (shape == Shape.TALL) {
            val now = if (mine) player.storyTitle else null
            v.setViewVisibility(R.id.brief_now, if (now != null) View.VISIBLE else View.GONE)
            if (now != null) text(R.id.brief_now, "Now: $now", WidgetText.Face.SANS_MEDIUM, 12f, content)
        }

        // 4x4: the stories in the briefing; tap one to hear it now.
        if (shape == Shape.TALL && Build.VERSION.SDK_INT >= 31) {
            text(R.id.brief_list_label, "IN THIS BRIEFING", WidgetText.Face.MONO_SEMIBOLD, 10f, content, tracking = 0.16f)
            val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            val rowWidth = content - (34 * d).toInt()
            stories.take(12).forEachIndexed { i, a ->
                val row = RemoteViews(context.packageName, R.layout.widget_brief_item)
                val current = mine && i == player.storyIndex
                val num = WidgetText.render(context, "%02d".format(i + 1), WidgetText.Face.MONO_SEMIBOLD, 11f, (26 * d).toInt())
                row.setImageViewBitmap(if (current) R.id.item_num_active else R.id.item_num, num)
                row.setViewVisibility(R.id.item_num, if (current) View.GONE else View.VISIBLE)
                row.setViewVisibility(R.id.item_num_active, if (current) View.VISIBLE else View.GONE)
                row.setImageViewBitmap(R.id.item_title, WidgetText.render(context, a.title, WidgetText.Face.SERIF_BOLD, 15f, rowWidth, maxLines = 2, lineHeightSp = 19f))
                row.setContentDescription(R.id.item_title, a.title)
                row.setImageViewBitmap(R.id.item_source, WidgetText.render(context, (a.source ?: a.domain ?: "").uppercase(), WidgetText.Face.MONO_MEDIUM, 10f, rowWidth, letterSpacingEm = 0.08f))
                row.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(BriefingService.EXTRA_INDEX, i))
                items.addItem(i.toLong(), row)
            }
            v.setRemoteAdapter(R.id.brief_list, items.build())
            v.setPendingIntentTemplate(R.id.brief_list, PendingIntent.getForegroundService(
                context, id * 16 + 10,
                Intent(context, BriefingService::class.java).setAction(BriefingService.ACTION_PLAY_AT).putTopic(topicId, label).setData(android.net.Uri.parse("editrove://widget/$id/list")),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
        }
        return v
    }

    private fun Intent.putTopic(topicId: String, label: String): Intent =
        putExtra(BriefingService.EXTRA_TOPIC, topicId).putExtra(BriefingService.EXTRA_LABEL, label)

    private fun service(context: Context, id: Int, slot: Int, action: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getForegroundService(
            context, id * 16 + slot,
            Intent(context, BriefingService::class.java).setAction(action).apply(extras).setData(android.net.Uri.parse("editrove://widget/$id/$slot")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, provider: Class<*>, action: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, id * 16 + if (action == ACTION_SPEED) 11 else 12,
            Intent(context, provider).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ---- Button handlers that don't need the playback service --------------------------------

    internal fun onAction(context: Context, provider: Class<*>, intent: Intent, done: () -> Unit) {
        val app = context.applicationContext as OpenWireApp
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_SPEED -> {
                // 1.0× → 1.25× → 1.5× → 1.0×, applied to the sentence being read.
                val repo = app.container.settingsRepository
                val now = repo.settings.value.briefing.speed
                val next = when { now < 1.2f -> 1.25f; now < 1.45f -> 1.5f; else -> 1.0f }
                repo.update { it.copy(briefing = it.briefing.copy(speed = next)) }
                scope.launch { app.container.briefingPlayer.applySpeedNow(); updateAll(app); done() }
            }
            ACTION_REFRESH -> {
                refreshing += id
                update(app, provider, id)
                scope.launch {
                    WidgetData.refreshIfStale(app, topicFor(app, provider, id), maxAgeMs = 0)
                    refreshing -= id
                    update(app, provider, id)
                    done()
                }
            }
            else -> done()
        }
    }
}

/** Base provider for the two briefing widgets. */
abstract class BriefWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { BriefWidgets.update(context, javaClass, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        BriefWidgets.update(context, javaClass, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) = BriefWidgets.forget(context, ids)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BriefWidgets.ACTION_SPEED || intent.action == BriefWidgets.ACTION_REFRESH) {
            val pending = goAsync()
            BriefWidgets.onAction(context, javaClass, intent) { pending.finish() }
        } else super.onReceive(context, intent)
    }
}

/** The Editrove Daily Brief: the Top Stories briefing. */
class AudioBriefWidgetReceiver : BriefWidgetProvider()

/** Topic of Choice: one topic, chosen when the widget is added. */
class TopicWidgetReceiver : BriefWidgetProvider()
