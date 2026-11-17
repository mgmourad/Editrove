// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import news.openwire.i18n.tr
import news.openwire.R
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import news.openwire.data.remote.ArticleContent
import news.openwire.data.remote.ArticleTextExtractor
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Indigo
import news.openwire.ui.theme.NewsreaderFamily
import news.openwire.ui.theme.Slate900

private sealed interface BodyState {
    data object Loading : BodyState
    data class Loaded(val content: ArticleContent) : BodyState
    data object Failed : BodyState
}

/** Everything the reader needs besides the article itself. */
class ReaderActions(
    val router: ArticleRouter,
    val extractor: ArticleTextExtractor,
    val scope: CoroutineScope,
    val onToggleSaved: (Article) -> Unit,
    val onToggleLiked: (Article) -> Unit,
    /** Changes the article text size (kept for every article). */
    val onTextScale: (Float) -> Unit = {},
)

/** The reader's text size (1 = normal), from the settings. */
val LocalTextScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** A− / A+: smaller and larger article text. */
@Composable
fun TextSizeControl(onChange: (Float) -> Unit, background: Color = Glass.chipFill, border: Color? = null) {
    val g = Glass
    val scale = LocalTextScale.current
    Row(
        Modifier.clip(CircleShape).background(background).then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val canDown = scale > news.openwire.domain.model.TEXT_SCALES.first() + 0.01f
        val canUp = scale < news.openwire.domain.model.TEXT_SCALES.last() - 0.01f
        Box(
            Modifier.clickable(enabled = canDown) { onChange(news.openwire.domain.model.stepTextScale(scale, up = false)) }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp).height(22.dp),
            contentAlignment = Alignment.Center,
        ) { Text("A", style = serif(13.sp, FontWeight.Bold), color = g.textPrimary.copy(alpha = if (canDown) 1f else 0.35f)) }
        Box(Modifier.width(1.dp).height(16.dp).background(g.hairline))
        Box(
            Modifier.clickable(enabled = canUp) { onChange(news.openwire.domain.model.stepTextScale(scale, up = true)) }
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp).height(22.dp),
            contentAlignment = Alignment.Center,
        ) { Text("A", style = serif(19.sp, FontWeight.Bold), color = g.textPrimary.copy(alpha = if (canUp) 1f else 0.35f)) }
    }
}

/**
 * The detail pane (unfolded). Selecting a new story crossfades + slides it in,
 * like the prototype's `article-enter` animation.
 */
@Composable
fun ReaderPane(
    article: Article?,
    category: String,
    saved: Boolean,
    liked: Boolean,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = article,
        contentKey = { it?.id },
        transitionSpec = {
            (fadeIn(springTween(450)) + slideInVertically(springTween(450)) { 40 } + scaleIn(springTween(450), initialScale = 0.99f))
                .togetherWith(fadeOut(tween(150)))
        },
        modifier = modifier,
        label = "readerPane",
    ) { a ->
        if (a == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr(R.string.reader_select), style = sans(13.sp), color = Glass.textSecondary)
            }
        } else {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArticleBody(a, category, showMetaBar = true, saved = saved, liked = liked, actions = actions)
            }
        }
    }
}

/**
 * Cover-screen reader: a full-screen sheet that springs up from the bottom with
 * a "Back to Feed" pill and like/bookmark actions.
 */
@Composable
fun ReaderSheet(
    article: Article,
    category: String,
    saved: Boolean,
    liked: Boolean,
    actions: ReaderActions,
    onClose: () -> Unit,
) {
    val g = Glass
    val haze = rememberHazeState()
    val headerHeight = 61.dp
    Box(Modifier.fillMaxSize().background(g.deviceBg)) {
        // Scrolling body is the blur source; the top bar floats over it as frosted glass.
        Column(
            Modifier.fillMaxSize().hazeSource(haze).background(g.deviceBg).verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = headerHeight + 20.dp, bottom = 64.dp),
        ) {
            ArticleBody(article, category, showMetaBar = false, saved = saved, liked = liked, actions = actions)
        }
        // Faded glass behind the header (no rule), matching the feed's top and bottom fades.
        StatusBarGlass(haze, extra = headerHeight + 18.dp)
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(headerHeight - 1.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(g.chipFill)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = g.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr(R.string.reader_back), style = sans(12.sp, FontWeight.SemiBold), color = g.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                TextSizeControl(actions.onTextScale)
                Spacer(Modifier.width(8.dp))
                SpringToggle(liked, ToggleKind.LIKE, { actions.onToggleLiked(article) }, background = g.chipFill, padding = 8.dp)
                Spacer(Modifier.width(8.dp))
                ShareButton(article, background = g.chipFill, padding = 8.dp)
                Spacer(Modifier.width(8.dp))
                SpringToggle(saved, ToggleKind.BOOKMARK, { actions.onToggleSaved(article) }, background = g.chipFill, padding = 8.dp)
            }
        }
    }
}

/** Shared editorial article layout (prototype `buildArticleHTML`). */
@Composable
internal fun ArticleBody(
    article: Article,
    category: String,
    showMetaBar: Boolean,
    saved: Boolean,
    liked: Boolean,
    actions: ReaderActions,
    /** False when the page already shows the photo as a large header (Full view). */
    showFeatureImage: Boolean = true,
) {
    val g = Glass
    val url = article.originalUrl ?: article.targetUrl ?: ""
    val state by produceState<BodyState>(BodyState.Loading, article.id) {
        value = if (url.isEmpty()) BodyState.Failed
        else actions.extractor.extract(url)?.let { BodyState.Loaded(it) } ?: BodyState.Failed
    }
    // A photo the reader found is remembered, so the story's card shows it next time.
    val images = LocalImageResolver.current
    LaunchedEffect(state) { (state as? BodyState.Loaded)?.content?.imageUrl?.let { images?.remember(article.originalUrl, it) } }
    val content = (state as? BodyState.Loaded)?.content

    // The whole article (headline, standfirst, summary, body) follows the chosen text size.
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * LocalTextScale.current),
    ) {
    Column(Modifier.widthIn(max = 672.dp).fillMaxWidth()) {
        if (showMetaBar) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(CircleShape).background(g.accent.copy(alpha = 0.15f))
                        .border(1.dp, g.accent.copy(alpha = 0.30f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text(category.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = g.accentSoft) }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${article.publication()} • ${NewsProcessor.formatRelativeTime(article.publishedAt)}",
                    style = mono(12.sp, tracking = 0.sp),
                    color = g.textPrimary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextSizeControl(actions.onTextScale, border = g.hairline)
                Spacer(Modifier.width(8.dp))
                SpringToggle(liked, ToggleKind.LIKE, { actions.onToggleLiked(article) }, background = g.chipFill, border = g.hairline, padding = 8.dp)
                Spacer(Modifier.width(8.dp))
                ShareButton(article, background = g.chipFill, border = g.hairline, padding = 8.dp)
                Spacer(Modifier.width(8.dp))
                SpringToggle(saved, ToggleKind.BOOKMARK, { actions.onToggleSaved(article) }, background = g.chipFill, border = g.hairline, padding = 8.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(g.hairline))
            Spacer(Modifier.height(20.dp))
        }

        // Reader-service pill: tells you how this story is unlocked and opens that page.
        val context = LocalContext.current
        val service = remember(article.id) { actions.router.serviceFor(article) }
        if (service != null) {
            Row(
                Modifier.padding(bottom = 12.dp).clip(CircleShape)
                    .background(g.indigo.copy(alpha = 0.14f)).border(1.dp, g.indigo.copy(alpha = 0.32f), CircleShape)
                    .clickable { actions.router.open(actions.scope, context, article) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr(R.string.reader_read_on, service.name), style = mono(10.sp, FontWeight.SemiBold, 0.06.em), color = g.indigo)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = g.indigo, modifier = Modifier.size(12.dp))
            }
        }

        // Editorial serif headline
        Text(article.title.ifBlank { content?.title?.takeIf { it.isNotBlank() } ?: article.source ?: article.domain ?: tr(R.string.reader_article) }, style = serif(28.sp, FontWeight.Bold, lineHeight = 32.sp, tracking = (-0.01).em), color = g.textPrimary)
        Spacer(Modifier.height(12.dp))

        // Story grouping: who else is running this story
        article.coverageLine(4)?.let {
            Text(it, style = sans(12.sp, FontWeight.Medium), color = g.accentSoft)
            Spacer(Modifier.height(12.dp))
        }

        // Deck / standfirst — the publisher's own description, when we have it
        content?.description?.let {
            Text(it, style = sans(15.sp, lineHeight = 24.sp), color = g.textPrimary.copy(alpha = 0.75f))
            Spacer(Modifier.height(20.dp))
        }

        // Byline
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Indigo, g.accent)))
                    .padding(1.5.dp).clip(CircleShape).background(Slate900),
                contentAlignment = Alignment.Center,
            ) { SourceLogo(article.domain, article.source, size = 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    content?.author ?: article.source ?: article.domain ?: "",
                    style = sans(12.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (content?.author != null) (article.source ?: article.domain ?: "") else (article.domain ?: ""),
                    style = sans(11.sp), color = g.textPrimary.copy(alpha = 0.6f), maxLines = 1,
                )
            }
            Box(
                Modifier.clip(CircleShape).background(g.chipFill.copy(alpha = g.chipFill.alpha / 2))
                    .border(1.dp, g.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    content?.let { tr(R.string.reader_minutes, it.readMinutes) } ?: tr(R.string.reader_minutes_unknown),
                    style = mono(11.sp, tracking = 0.sp), color = g.textPrimary.copy(alpha = 0.6f),
                )
            }
        }

        // Liquid Glass narrated-briefing player
        AudioBriefingCard(article, content)
        Spacer(Modifier.height(16.dp))

        // On-device summary: the article's own key sentences.
        val keyPoints = remember(content) { content?.let { news.openwire.domain.engine.Summarizer.keyPoints(it.text, 3) }.orEmpty() }
        androidx.compose.animation.AnimatedVisibility(keyPoints.isNotEmpty(), enter = androidx.compose.animation.expandVertically(springTween(420)) + androidx.compose.animation.fadeIn()) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.reader_summary), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                    Text(tr(R.string.reader_key_points), style = sans(10.sp), color = g.textSecondary)
                }
                keyPoints.forEach { point ->
                    Row(Modifier.padding(top = 10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(g.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(point, style = sans(13.5.sp, lineHeight = 21.sp), color = g.textPrimary.copy(alpha = 0.92f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Feature image (16:9, 24dp radius) with caption
        article.imageUrl?.takeIf { showFeatureImage }?.let { img ->
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .sharedArticleImage(article.id, hasImage = true, cornerRadius = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            ) { ArticleImage(article.copy(imageUrl = img), Modifier.fillMaxSize(), logoSize = 72.dp) }
            Text(
                tr(R.string.reader_photo, article.publication()),
                style = mono(10.sp, tracking = 0.05.em),
                color = g.textPrimary.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }

        when (val s = state) {
            BodyState.Loading -> BodySkeleton()
            BodyState.Failed -> Text(
                if (service != null) tr(R.string.reader_blocked_service, service.name)
                else tr(R.string.reader_failed),
                style = sans(14.sp, lineHeight = 22.sp),
                color = g.textSecondary,
            )
            is BodyState.Loaded -> {
                s.content.pullQuote?.let { quote ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(g.accent))
                        Spacer(Modifier.width(16.dp))
                        Text(
                            quote,
                            style = serif(20.sp, FontWeight.Normal, lineHeight = 26.sp).copy(fontStyle = FontStyle.Italic),
                            color = g.textPrimary.copy(alpha = 0.95f),
                        )
                    }
                }
                s.content.paragraphs.forEachIndexed { i, para ->
                    // Drop caps are a Latin-type tradition; Arabic (and Hebrew) text opens plainly.
                    if (i == 0 && !RX_RTL.containsMatchIn(para.take(40))) DropCapParagraph(para) else BodyParagraph(para)
                    Spacer(Modifier.height(16.dp))
                }
                if (s.content.partial) {
                    Text(
                        tr(R.string.reader_preview),
                        style = sans(13.sp, lineHeight = 20.sp), color = g.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (s.content.via != news.openwire.data.remote.TextSource.PUBLISHER) {
                    val viaUrl = s.content.viaUrl
                    Text(
                        tr(R.string.reader_text_via, s.content.via.label),
                        style = mono(10.sp, tracking = 0.06.em), color = g.textPrimary.copy(alpha = 0.55f),
                        modifier = Modifier.clip(CircleShape).clickable(enabled = viaUrl != null) {
                            viaUrl?.let { androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(it)) }
                        }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        GlassCard(onClick = {
            actions.router.open(actions.scope, context, article) {
                android.widget.Toast.makeText(
                    context,
                    tr(R.string.reader_locked),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }, contentPadding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    service?.let { tr(R.string.reader_read_on, it.name) } ?: tr(R.string.reader_continue, article.source ?: tr(R.string.reader_publisher_site)),
                    style = sans(13.sp, FontWeight.SemiBold), color = g.accentSoft, modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = g.accentSoft, modifier = Modifier.size(16.dp))
            }
        }
    }
    }
}

private val bodyStyle = sans(14.5.sp, lineHeight = 25.sp)

@Composable
private fun BodyParagraph(text: String) {
    Text(text, style = bodyStyle, color = Glass.textPrimary.copy(alpha = 0.9f))
}

/**
 * First paragraph with a floated rose drop cap spanning two lines: the opening
 * lines wrap beside the cap, the remainder flows full-width beneath it.
 */
@Composable
private fun DropCapParagraph(text: String) {
    val g = Glass
    if (text.length < 2) { BodyParagraph(text); return }
    var split by remember(text) { mutableIntStateOf(-1) }
    Column {
        Row {
            Text(
                text.take(1),
                style = serif(48.sp, FontWeight.Bold, lineHeight = 44.sp),
                color = g.accent,
                modifier = Modifier.padding(end = 10.dp, top = 2.dp),
            )
            Text(
                text.drop(1),
                style = bodyStyle,
                color = g.textPrimary.copy(alpha = 0.9f),
                maxLines = 2,
                overflow = TextOverflow.Clip,
                onTextLayout = { layout ->
                    split = if (layout.lineCount >= 2 && layout.hasVisualOverflow) layout.getLineEnd(1, visibleEnd = false) else Int.MAX_VALUE
                },
            )
        }
        if (split in 0 until text.length - 1) {
            Text(text.drop(1).substring(split).trimStart(), style = bodyStyle, color = g.textPrimary.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun BodySkeleton() {
    val t = rememberInfiniteTransition(label = "bodySk")
    val a by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(1f, 0.95f, 0.98f, 0.7f, 0f, 1f, 0.92f, 0.6f).forEach { w ->
            if (w == 0f) Spacer(Modifier.height(6.dp))
            else Box(Modifier.fillMaxWidth(w).height(12.dp).clip(CircleShape).background(Glass.textPrimary.copy(alpha = a * 0.25f)))
        }
    }
}

// ---- Narrated briefing (on-device TTS) ---------------------------------------------

private val WaveHeights = listOf(35, 65, 90, 50, 80, 100, 60, 40, 85, 70, 95, 45, 75, 60, 85, 50, 70, 90, 40, 65)

@Composable
private fun AudioBriefingCard(article: Article, content: ArticleContent?) {
    val g = Glass
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val narrator = rememberNarrator(context)
    var playing by remember(article.id) { mutableStateOf(false) }
    LaunchedEffect(article.id) { narrator.stop() }
    DisposableEffect(narrator) { narrator.onDone = { playing = false }; onDispose { narrator.onDone = null } }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.glowShadow(g.accent, 14.dp).size(40.dp).clip(CircleShape).background(g.accent)
                    .clickable(enabled = narrator.ready) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (playing) narrator.stop() else narrator.speak(buildNarration(article, content))
                        playing = !playing
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = tr(if (playing) R.string.cd_pause_briefing else R.string.briefing_play),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr(R.string.reader_listen),
                        style = sans(11.sp, FontWeight.SemiBold), color = g.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Text(
                        content?.narrationSeconds?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--",
                        style = mono(10.sp, tracking = 0.sp), color = g.textPrimary.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Waveform(playing)
            }
        }
    }
}

private fun buildNarration(article: Article, content: ArticleContent?): String = buildString {
    append(article.title).append(". ")
    article.source?.let { append(tr(R.string.narration_from, it)).append(" ") }
    content?.description?.let { append(it).append(" ") }
    content?.text?.let { append(it) }
}

/** `wavePulse`: bars breathe between 35% and 100% height, staggered 70ms apart. */
@Composable
internal fun Waveform(playing: Boolean, modifier: Modifier = Modifier) {
    val g = Glass
    val t = rememberInfiniteTransition(label = "wave")
    // As many 4dp bars (3dp gaps) as fit, spread edge to edge across the container.
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.height(16.dp).fillMaxWidth()) {
        val count = ((maxWidth.value + 3f) / 7f).toInt().coerceAtLeast(8)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(count) { i ->
                val h = WaveHeights[(i * 7 + i / WaveHeights.size) % WaveHeights.size]
                val pulse by t.animateFloat(
                    0.35f, 1f,
                    infiniteRepeatable(tween(450), RepeatMode.Reverse, StartOffset((i * 70) % 900)),
                    label = "bar$i",
                )
                Box(
                    Modifier.width(4.dp).fillMaxHeight(h / 100f)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleY = if (playing) pulse else 1f
                        }
                        .clip(CircleShape).background(g.accent.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

/** Thin wrapper over Android TextToSpeech that chunks long text (engine input is capped). */
internal class Narrator(context: Context) {
    private val appContext = context.applicationContext
    var ready by mutableStateOf(false)
        private set
    var onDone: (() -> Unit)? = null
    private var lastId: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == lastId) onDone?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
    }

    fun speak(text: String) {
        val prefs = (appContext as? news.openwire.OpenWireApp)?.container?.settingsRepository?.settings?.value?.briefing
        val voice = prefs?.voice?.let { name -> runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull() }
        if (voice != null) tts.voice = voice
        else tts.language = Locale.getDefault().takeIf { tts.isLanguageAvailable(it) >= 0 } ?: Locale.US
        tts.setSpeechRate(prefs?.speed ?: 1f)
        val max = TextToSpeech.getMaxSpeechInputLength() - 100
        val chunks = text.split(RX_1).fold(mutableListOf(StringBuilder())) { acc, s ->
            if (acc.last().length + s.length > max) acc.add(StringBuilder())
            acc.last().append(s).append(' ')
            acc
        }
        chunks.forEachIndexed { i, c ->
            val id = "openwire-$i"
            lastId = id
            tts.speak(c.toString(), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}

@Composable
internal fun rememberNarrator(context: Context): Narrator {
    val narrator = remember { Narrator(context) }
    DisposableEffect(narrator) { onDispose { narrator.shutdown() } }
    return narrator
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("(?<=[.!?])\\s+")

private val RX_RTL = Regex("[\\u0590-\\u08FF]")
