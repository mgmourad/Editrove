// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import java.text.DateFormat
import java.util.Date
import news.openwire.OpenWireApp
import news.openwire.R

/** Curated front page: lead photo, secondary headlines and a Bento of stories with bookmarks. */
class TopStoriesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { TopStories() }
    }
}

class TopStoriesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TopStoriesWidget()
}

@Composable
private fun TopStories() {
    val context = LocalContext.current
    val version = WidgetSignals.feedVersion
    val stories by produceState<List<WidgetStory>?>(null, version) { value = WidgetData.stories(context, "TOP", count = 7, px = 520, photos = 7) }
    val size = LocalSize.current
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_glass)).cornerRadius(W.SHELL)) {
        val list = stories
        when {
            list == null -> Loading()
            list.isEmpty() -> Empty("Open Editrove once to load Top Stories.")
            else -> when (SizeClass.of(size)) {
                SizeClass.COMPACT -> CompactLead(list.first())
                SizeClass.WIDE -> WideSplit(list, size.width)
                SizeClass.EXPANDED -> Bento(list, size.width, size.height, columns = 2)
                SizeClass.FOLD -> Bento(list, size.width, size.height, columns = 3)
            }
        }
    }
}

@Composable
internal fun Loading() {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Monogram(36.dp) }
}

@Composable
internal fun Empty(message: String) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp).clickable(actionStartActivity(WidgetData.openApp(LocalContext.current))), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Monogram(32.dp)
        Text(message, style = sans(12.sp), modifier = GlanceModifier.padding(top = 8.dp))
    }
}

private fun editionLabel(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) { in 4..11 -> "MORNING EDITION"; in 12..16 -> "AFTERNOON EDITION"; else -> "EVENING EDITION" }
}

/** Photo (or obsidian fallback) with the multi-stop editorial mask. */
@Composable
internal fun PhotoLayer(story: WidgetStory, radius: Dp) {
    val photo = story.photo
    if (photo != null) Image(ImageProvider(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize().cornerRadius(radius))
    else Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_photo_fallback)).cornerRadius(radius)) {}
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_scrim)).cornerRadius(radius)) {}
}

@Composable
private fun CompactLead(s: WidgetStory) {
    val open = actionStartActivity(WidgetData.openIntent(LocalContext.current, s.article))
    Box(GlanceModifier.fillMaxSize().clickable(open)) {
        PhotoLayer(s, W.SHELL)
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Monogram(24.dp)
                Spacer(GlanceModifier.defaultWeight())
                Pill("TOP", W.onPhoto, W.photoChip)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(s.source, style = mono(9.sp, W.onPhoto, bold = true), maxLines = 1)
            Text(s.article.title, style = serif(15.sp, W.white), maxLines = 3, modifier = GlanceModifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun WideSplit(list: List<WidgetStory>, width: Dp) {
    val ctx = LocalContext.current
    val lead = list.first()
    val heroW = (width - 24.dp - 10.dp) * 0.52f
    Row(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Box(GlanceModifier.width(heroW).fillMaxHeight().cornerRadius(20.dp).clickable(actionStartActivity(WidgetData.openIntent(ctx, lead.article)))) {
            PhotoLayer(lead, 20.dp)
            Column(GlanceModifier.fillMaxSize().padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Monogram(20.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Pill("LEAD", W.white, W.photoChip)
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(lead.category.uppercase(), style = mono(9.sp, W.onPhoto, bold = true), maxLines = 1)
                Text(lead.article.title, style = serif(14.sp, W.white), maxLines = 3, modifier = GlanceModifier.padding(top = 2.dp))
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(editionLabel(), style = mono(10.sp, W.textSecondary, bold = true), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                Text("●", style = mono(9.sp, W.emerald))
            }
            list.drop(1).take(2).forEachIndexed { i, s ->
                if (i > 0) Spacer(GlanceModifier.height(6.dp))
                Column(
                    GlanceModifier.fillMaxWidth().defaultWeight().background(ImageProvider(R.drawable.widget_card)).cornerRadius(W.CARD).padding(10.dp)
                        .clickable(actionStartActivity(WidgetData.openIntent(ctx, s.article))),
                ) {
                    Text("${s.source} · ${s.age}", style = mono(9.sp, if (i == 0) W.amber else W.cyan, bold = true), maxLines = 1)
                    Text(s.article.title, style = serif(12.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Bento(list: List<WidgetStory>, width: Dp, height: Dp, columns: Int) {
    val ctx = LocalContext.current
    val updated = (ctx.applicationContext as OpenWireApp).container.feedState.lastUpdated("TOP")
    val lead = list.first()
    val body = height - 32.dp - 34.dp
    val heroH = (body * if (columns == 3) 0.5f else 0.42f).coerceIn(118.dp, 200.dp)
    val rows = if (body - heroH >= 230.dp) 2 else 1
    val cardH = (body - heroH - 8.dp * rows) / rows
    val titleLines = if (cardH < 128.dp) 2 else 3
    Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
        Row(GlanceModifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            Monogram(24.dp)
            Text("  Top Stories", style = serif(16.sp), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
            if (updated > 0) Pill("Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(updated))}", W.textSecondary, W.chip, size = 10.sp)
        }
        Spacer(GlanceModifier.height(8.dp))
        // Lead hero banner
        Box(GlanceModifier.fillMaxWidth().height(heroH).cornerRadius(20.dp).clickable(actionStartActivity(WidgetData.openIntent(ctx, lead.article)))) {
            PhotoLayer(lead, 20.dp)
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pill(lead.category.uppercase(), W.onPhoto, W.photoChip)
                    Spacer(GlanceModifier.defaultWeight())
                    BookmarkButton(lead.saved, bookmark(lead), tint = W.white, bg = W.photoChip)
                }
                Spacer(GlanceModifier.defaultWeight())
                Text("${lead.source} · ${lead.age}", style = mono(9.sp, W.onPhoto, bold = true), maxLines = 1)
                Text(lead.article.title, style = serif(17.sp, W.white), maxLines = if (heroH < 125.dp) 1 else 2, modifier = GlanceModifier.padding(top = 2.dp))
            }
        }
        val cards = list.drop(1).take(columns * rows)
        for (r in 0 until rows) {
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (c in 0 until columns) {
                    if (c > 0) Spacer(GlanceModifier.width(8.dp))
                    val s = cards.getOrNull(r * columns + c)
                    Box(GlanceModifier.defaultWeight().fillMaxHeight()) { if (s != null) StoryCard(s, titleLines, photoHeight = if (cardH >= 160.dp && s.photo != null) cardH - 104.dp else 0.dp, thumb = cardH >= 112.dp) }
                }
            }
        }
    }
}

/** Bento card: thumbnail, source · time, serif headline, bookmark. */
@Composable
private fun StoryCard(s: WidgetStory, titleLines: Int, photoHeight: Dp = 0.dp, thumb: Boolean = true) {
    val ctx = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_card)).cornerRadius(W.CARD).padding(8.dp)
            .clickable(actionStartActivity(WidgetData.openIntent(ctx, s.article))),
    ) {
        // Tall cards lead with the photo; short ones carry a thumbnail beside the source.
        if (photoHeight > 0.dp && s.photo != null) {
            Image(ImageProvider(s.photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxWidth().height(photoHeight).cornerRadius(12.dp))
            Spacer(GlanceModifier.height(6.dp))
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (s.photo != null && photoHeight == 0.dp && thumb) {
                Image(ImageProvider(s.photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.size(40.dp).cornerRadius(12.dp))
                Spacer(GlanceModifier.width(8.dp))
            }
            Column(GlanceModifier.defaultWeight()) {
                Text(s.source, style = mono(9.sp, W.cyan, bold = true), maxLines = 1)
                Text(s.age, style = mono(9.sp, W.textMuted), maxLines = 1)
            }
            BookmarkButton(s.saved, bookmark(s), diameter = 26.dp)
        }
        Text(s.article.title, style = serif(12.5.sp), maxLines = titleLines, modifier = GlanceModifier.padding(top = 6.dp))
    }
}

private fun bookmark(s: WidgetStory) = actionRunCallback<BookmarkAction>(actionParametersOf(BookmarkAction.ID to s.article.id, BookmarkAction.TOPIC to "TOP"))

/** Saves / unsaves a story straight from the widget (the app's Saved tab updates too). */
class BookmarkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        val topic = parameters[TOPIC] ?: "TOP"
        val article = WidgetData.findArticle(context, id, WidgetData.articles(context, topic)) ?: return
        (context.applicationContext as OpenWireApp).container.libraryStore.toggleSaved(article)
        WidgetSync.feedsChanged(context, delayMs = 0)
    }
    companion object {
        val ID = ActionParameters.Key<String>("id")
        val TOPIC = ActionParameters.Key<String>("topic")
    }
}
