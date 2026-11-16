// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
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
import news.openwire.R
import news.openwire.domain.model.Topic

/**
 * "Topic of Choice": a feed for one of the reader's topics, switched right in the widget
 * header (each widget remembers its own topic; the accent colour follows the topic).
 */
class TopicWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { TopicFeed() }
    }

    companion object { val TOPIC = stringPreferencesKey("topic") }
}

class TopicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopicWidget()
}

private data class TopicUi(val topic: Topic, val index: Int, val all: List<Topic>, val accent: Accent)

@Composable
private fun TopicFeed() {
    val context = LocalContext.current
    val all = WidgetData.switchableTopics(context)
    val chosen = currentState<Preferences>()[TopicWidget.TOPIC]
    val index = all.indexOfFirst { it.id == chosen }.takeIf { it >= 0 } ?: all.indexOfFirst { it.id == "AI" }.coerceAtLeast(0)
    val topic = all.getOrNull(index)
    val version = WidgetSignals.feedVersion
    val stories by produceState<List<WidgetStory>?>(null, topic?.id, version) {
        value = topic?.let { WidgetData.stories(context, it.id, count = 3, px = 420) }.orEmpty()
    }
    val size = LocalSize.current
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_glass)).cornerRadius(W.SHELL)) {
        if (topic == null) { Empty("Turn on a topic in Editrove to follow it here."); return@Box }
        val ui = TopicUi(topic, index, all, Accents.forTopic(topic.id, index))
        val list = stories
        when (SizeClass.of(size)) {
            SizeClass.COMPACT -> Compact(ui, list)
            SizeClass.WIDE -> Wide(ui, list, size.width)
            SizeClass.EXPANDED -> Digest(ui, list, size.width, columns = 1)
            SizeClass.FOLD -> Digest(ui, list, size.width, columns = 2)
        }
    }
}

private fun cycle(ui: TopicUi) = actionRunCallback<SelectTopicAction>(actionParametersOf(SelectTopicAction.TOPIC_ID to (ui.all.getOrNull((ui.index + 1) % ui.all.size)?.id ?: ui.topic.id)))
private fun select(t: Topic) = actionRunCallback<SelectTopicAction>(actionParametersOf(SelectTopicAction.TOPIC_ID to t.id))

/** The topic switcher pill (↻ cycles to the next topic). */
@Composable
private fun SwitcherPill(ui: TopicUi) {
    Pill("${shortLabel(ui.topic)}  ↻", ui.accent.fg, ui.accent.chip, cycle(ui), size = 10.sp)
}

/** A strip of topic pills (selected first, then the next ones that fit). */
@Composable
private fun PillBar(ui: TopicUi, width: Dp) {
    // As many pills as fit (mono 10sp ≈ 6.2dp per character + padding), selected first.
    val shown = mutableListOf<Int>()
    var used = 0f
    for (k in 0 until minOf(ui.all.size, 9)) {
        val i = (ui.index + k) % ui.all.size
        val w = shortLabel(ui.all[i]).length * 6.2f + (if (k == 0) 30f else 16f) + 6f
        if (k > 0 && used + w > width.value) break
        used += w; shown += i
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { k, i ->
            val t = ui.all[i]
            val acc = Accents.forTopic(t.id, i)
            if (k > 0) Spacer(GlanceModifier.width(6.dp))
            if (k == 0) Pill("${shortLabel(t)}  ↻", acc.fg, acc.chip, cycle(ui), size = 10.sp)
            else Pill(shortLabel(t), W.textSecondary, W.chip, select(t), size = 10.sp)
        }
    }
}

/** Pill text: the topic label, with long place names cut to their first part ("Washington, D.C." → "WASHINGTON"). */
private fun shortLabel(t: Topic): String = t.label.substringBefore(',').trim().uppercase().let { if (it.length > 14) it.take(13) + "…" else it }

@Composable
private fun Compact(ui: TopicUi, list: List<WidgetStory>?) {
    val ctx = LocalContext.current
    val lead = list?.firstOrNull()
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth()) { SwitcherPill(ui) }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            lead?.article?.title ?: if (list == null) "Loading ${ui.topic.label}…" else "No ${ui.topic.label} stories yet. Open Editrove to refresh.",
            style = serif(15.sp), maxLines = 4,
            modifier = lead?.let { GlanceModifier.clickable(actionStartActivity(WidgetData.openIntent(ctx, it.article))) } ?: GlanceModifier,
        )
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(W.divider)) {}
        Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(lead?.source ?: "EDITROVE", style = mono(10.sp, ui.accent.fg, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text(lead?.age ?: "", style = mono(10.sp, W.textMuted))
        }
    }
}

@Composable
private fun Wide(ui: TopicUi, list: List<WidgetStory>?, width: Dp) {
    val ctx = LocalContext.current
    val lead = list?.firstOrNull()
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { PillBar(ui, width - 60.dp) }
            Text("LIVE", style = mono(10.sp, W.textMuted, bold = true))
        }
        Spacer(GlanceModifier.defaultWeight())
        if (lead == null) Text(if (list == null) "Loading…" else "No stories yet.", style = sans(12.sp))
        else Row(
            GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_card)).cornerRadius(W.CARD).padding(10.dp)
                .clickable(actionStartActivity(WidgetData.openIntent(ctx, lead.article))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumb(lead, 76.dp)
            Column(GlanceModifier.defaultWeight()) {
                Text("${lead.source} · ${lead.age}", style = mono(10.sp, ui.accent.fg, bold = true), maxLines = 1)
                Text(lead.article.title, style = serif(14.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp))
                list.getOrNull(1)?.let { Text("Next: ${it.article.title}", style = sans(11.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp)) }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun Digest(ui: TopicUi, list: List<WidgetStory>?, width: Dp, columns: Int) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { PillBar(ui, width - 70.dp) }
            Monogram(24.dp)
        }
        Spacer(GlanceModifier.height(10.dp))
        when {
            list == null -> Text("Loading ${ui.topic.label}…", style = sans(12.sp))
            list.isEmpty() -> Text("No ${ui.topic.label} stories yet. Open Editrove to refresh.", style = sans(12.sp))
            columns == 1 -> Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                list.take(3).forEachIndexed { i, s ->
                    if (i > 0) Spacer(GlanceModifier.height(8.dp))
                    Box(GlanceModifier.fillMaxWidth().defaultWeight()) { DigestRow(ui, s) }
                }
            }
            else -> {
                // 2-column Bento: lead feature (1.15fr) beside two stacked stories (1fr).
                val leadW = (width - 32.dp - 10.dp) * (1.15f / 2.15f)
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Box(GlanceModifier.width(leadW).fillMaxHeight()) { Feature(ui, list.first()) }
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        list.drop(1).take(2).forEachIndexed { i, s ->
                            if (i > 0) Spacer(GlanceModifier.height(8.dp))
                            Box(GlanceModifier.fillMaxWidth().defaultWeight()) { DigestRow(ui, s) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DigestRow(ui: TopicUi, s: WidgetStory) {
    val ctx = LocalContext.current
    Row(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_card)).cornerRadius(W.CARD).padding(10.dp)
            .clickable(actionStartActivity(WidgetData.openIntent(ctx, s.article))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(s, 56.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text("${s.source} · ${s.age}", style = mono(9.sp, ui.accent.fg, bold = true), maxLines = 1)
            Text(s.article.title, style = serif(13.sp), maxLines = 3, modifier = GlanceModifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Feature(ui: TopicUi, s: WidgetStory) {
    val ctx = LocalContext.current
    Box(GlanceModifier.fillMaxSize().cornerRadius(W.CARD).clickable(actionStartActivity(WidgetData.openIntent(ctx, s.article)))) {
        PhotoLayer(s, W.CARD)
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Pill(ui.topic.label.uppercase(), W.white, W.photoChip)
            Spacer(GlanceModifier.defaultWeight())
            Text("${s.source} · ${s.age}", style = mono(9.sp, W.onPhoto, bold = true), maxLines = 1)
            Text(s.article.title, style = serif(16.sp, W.white), maxLines = 3, modifier = GlanceModifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Thumb(s: WidgetStory, size: Dp) {
    val photo = s.photo ?: return
    Image(ImageProvider(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.size(size).cornerRadius(W.PHOTO))
    Spacer(GlanceModifier.width(10.dp))
}

/** Switches this widget's topic. */
class SelectTopicAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TOPIC_ID] ?: return
        updateAppWidgetState(context, glanceId) { it[TopicWidget.TOPIC] = id }
        TopicWidget().update(context, glanceId)
    }
    companion object { val TOPIC_ID = ActionParameters.Key<String>("topicId") }
}
