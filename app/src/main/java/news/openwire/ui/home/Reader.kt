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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    /** Reads a post the story embeds (X, through its official oEmbed). */
    val loadPost: suspend (String) -> news.openwire.domain.opinion.SocialPost? = { null },
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
    val scroll = rememberScrollState()
    var loading by remember(article.id) { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(g.deviceBg)) {
        // Scrolling body is the blur source; the top bar floats over it as frosted glass.
        Column(
            Modifier.fillMaxSize().hazeSource(haze).background(g.deviceBg).verticalScroll(scroll)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = headerHeight + 20.dp, bottom = 64.dp),
        ) {
            ArticleBody(article, category, showMetaBar = false, saved = saved, liked = liked, actions = actions, onLoading = { loading = it })
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
            // Loading: a comet along the top edge; then how far you've read.
            CometLine(loading, progress = { if (scroll.maxValue > 0) scroll.value / scroll.maxValue.toFloat() else 0f })
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
    /** Told whether the article text is still loading (the sheet's top-edge comet). */
    onLoading: ((Boolean) -> Unit)? = null,
    /** Told the page's content once it loads (Full view draws the photo header itself). */
    onContent: ((news.openwire.data.remote.ArticleContent?) -> Unit)? = null,
) {
    val g = Glass
    val url = article.originalUrl ?: article.targetUrl ?: ""
    // Retry starts the article over; "other outlets" reads the same story from other newsrooms.
    var attempt by remember(article.id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val appContext = LocalContext.current.applicationContext
    val state by produceState<BodyState>(BodyState.Loading, article.id, attempt) {
        value = BodyState.Loading
        value = if (url.isEmpty()) BodyState.Failed
        else actions.extractor.extract(url, article.title)?.let { BodyState.Loaded(it) } ?: BodyState.Failed
    }
    // The same story from other newsrooms: fetched only when the reader asks, then kept.
    var othersRequested by remember(article.id) { mutableStateOf(false) }
    var useOthers by remember(article.id) { mutableStateOf(false) }
    var noOthers by remember(article.id) { mutableStateOf(false) }
    val others by produceState<OthersState>(OthersState.Idle, article.id, othersRequested) {
        if (!othersRequested) return@produceState
        value = OthersState.Loading
        val country = (appContext as? news.openwire.OpenWireApp)?.container?.settingsRepository?.settings?.value?.edition?.country ?: "US"
        val list = runCatching { actions.extractor.otherReports(article.title, url.ifEmpty { null }, country) }.getOrDefault(emptyList())
        value = if (list.isEmpty()) OthersState.Empty else OthersState.Ready(list)
    }
    LaunchedEffect(others) { if (others == OthersState.Empty && useOthers) { useOthers = false; noOthers = true } }
    val openOthers = { othersRequested = true; useOthers = true; noOthers = false }
    // After a while, offer a way out (the article keeps loading meanwhile, and appears if it arrives).
    var slow by remember(article.id, attempt) { mutableStateOf(false) }
    LaunchedEffect(article.id, attempt) { kotlinx.coroutines.delay(SLOW_ARTICLE_MS); slow = true }
    // A photo the reader found is remembered, so the story's card shows it next time.
    val images = LocalImageResolver.current
    LaunchedEffect(state) { (state as? BodyState.Loaded)?.content?.imageUrl?.let { images?.remember(article.originalUrl, it) } }
    val content = (state as? BodyState.Loaded)?.content
    LaunchedEffect(state) { onLoading?.invoke(state is BodyState.Loading); onContent?.invoke(content) }
    // The page is in place (the sheet finished opening): the headline and kicker set now.
    val settled = rememberReaderSettled()
    val reduce = news.openwire.ui.theme.rememberReduceMotion()

    // The whole article (headline, standfirst, summary, body) follows the chosen text size.
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * LocalTextScale.current),
    ) {
    var columnWidthDp by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val densityNow = androidx.compose.ui.platform.LocalDensity.current
    Column(Modifier.widthIn(max = 672.dp).fillMaxWidth().onSizeChanged { columnWidthDp = with(densityNow) { it.width.toDp().value.toInt() } }) {
        // The story's main photo leads the page (the card's photo grows into it).
        if (showFeatureImage) HeroPhoto(article, content, loading = state is BodyState.Loading, aspect = 16f / 9f, radius = 24.dp, captionGap = 20.dp)
        if (showMetaBar) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).cascadeIn(0, settled), verticalAlignment = Alignment.CenterVertically) {
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(g.hairline).cascadeIn(0, settled))
            Spacer(Modifier.height(20.dp))
        }

        // Reader-service pill: tells you how this story is unlocked and opens that page.
        val context = LocalContext.current
        val service = remember(article.id) { actions.router.serviceFor(article) }
        Row(Modifier.fillMaxWidth().cascadeIn(1, settled), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).padding(bottom = 12.dp),
        ) {
        if (service != null) {
            Row(
                Modifier.clip(CircleShape)
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
        }
        // Without the top bar (folded): how long ago, across from the reader-service pill.
        if (!showMetaBar) NewsProcessor.formatRelativeTime(article.publishedAt).takeIf { it.isNotBlank() }?.let {
            Text(it, style = mono(11.sp, tracking = 0.04.em), color = g.textPrimary.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
        }
        }

        // Editorial serif headline
        SettingHeadline(
            article.title.ifBlank { content?.title?.takeIf { it.isNotBlank() } ?: article.source ?: article.domain ?: tr(R.string.reader_article) },
            start = settled, size = 28.sp, lineHeight = 32.sp, color = g.textPrimary, modifier = Modifier.fillMaxWidth().cascadeIn(2, settled),
        )
        Spacer(Modifier.height(12.dp))

        // Story grouping: who else is running this story
        article.coverageLine(4)?.let {
            Text(it, style = sans(12.sp, FontWeight.Medium), color = g.accentSoft, modifier = Modifier.cascadeIn(3, settled))
            Spacer(Modifier.height(12.dp))
        }

        // Deck / standfirst — the publisher's own description, when we have it
        content?.description?.let {
            Text(it, style = sans(15.sp, lineHeight = 24.sp).reading(), color = g.textPrimary.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth().cascadeIn(4, settled))
            Spacer(Modifier.height(20.dp))
        }

        // Byline (on a narrow screen, the other-outlets pill sits just under the source instead).
        // Narrow column (the cover screen, or the reader beside the feed): the pill goes under the source.
        val narrowByline = columnWidthDp in 1..559
        Row(Modifier.fillMaxWidth().padding(bottom = if (narrowByline) 10.dp else 20.dp).cascadeIn(5, settled), verticalAlignment = Alignment.CenterVertically) {
            SourceRing(start = settled, size = 36.dp) { SourceLogo(article.domain, article.source, size = 22.dp) }
            Spacer(Modifier.width(12.dp))
            val nameIn by androidx.compose.animation.core.animateFloatAsState(
                if (settled) 1f else 0f, if (reduce) tween(0) else tween(420, delayMillis = 220), label = "name",
            )
            Column(Modifier.weight(1f).graphicsLayer { alpha = nameIn }) {
                Text(
                    content?.author ?: article.source ?: article.domain ?: "",
                    style = sans(12.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (content?.author != null) (article.source ?: article.domain ?: "") else (article.domain ?: ""),
                    style = sans(11.sp), color = g.textPrimary.copy(alpha = 0.6f), maxLines = 1,
                )
            }
            // Optional: the same story as other newsrooms reported it (nothing is fetched until tapped).
            if (!narrowByline) {
                OtherOutletsPill(showing = useOthers, original = article.source ?: article.domain ?: "") { if (useOthers) useOthers = false else openOthers() }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.clip(CircleShape).background(g.chipFill.copy(alpha = g.chipFill.alpha / 2))
                    .border(1.dp, g.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                CountUp(content?.readMinutes) { m ->
                    Text(
                        m?.let { tr(R.string.reader_minutes, it) } ?: tr(R.string.reader_minutes_unknown),
                        style = mono(11.sp, tracking = 0.sp), color = g.textPrimary.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (narrowByline) {
            Box(Modifier.padding(bottom = 18.dp).cascadeIn(6, settled)) {
                OtherOutletsPill(showing = useOthers, original = article.source ?: article.domain ?: "") { if (useOthers) useOthers = false else openOthers() }
            }
        }

        // Liquid Glass narrated-briefing player
        Box(Modifier.cascadeIn(6, settled)) { AudioBriefingCard(article, content) }
        Spacer(Modifier.height(16.dp))

        // On-device summary: the article's own key sentences, written in front of you.
        if (!useOthers) Box(Modifier.cascadeIn(7, settled)) { KeyPointsCard(state, content) }
        Spacer(Modifier.height(16.dp))

        if (noOthers) Text(tr(R.string.reader_other_none), style = sans(13.sp, lineHeight = 20.sp), color = g.indigo, modifier = Modifier.padding(bottom = 16.dp))
        if (useOthers) OtherOutletsView(others, original = article.source ?: article.domain ?: "", onBack = { useOthers = false }, loadPost = actions.loadPost)
        else when (val s = state) {
            BodyState.Loading -> {
                // The sheet shows loading as the comet along its top edge.
                if (onLoading == null) LoadingBar(tr(R.string.reader_loading))
                if (slow) SlowArticleCard(failed = false, onRetry = { attempt++ }, onOthers = openOthers, smryUrl = if (service == null) url else "")
                else BodySkeleton()
            }
            BodyState.Failed -> {
                Text(
                    if (service != null) tr(R.string.reader_blocked_service, service.name) else tr(R.string.reader_failed),
                    style = sans(14.sp, lineHeight = 22.sp), color = g.textSecondary, modifier = Modifier.padding(bottom = 12.dp),
                )
                SlowArticleCard(failed = true, onRetry = { attempt++ }, onOthers = openOthers, smryUrl = if (service == null) url else "")
            }
            is BodyState.Loaded -> {
                // The original turned the reader away: say whose report this is, up front.
                if (s.content.via == news.openwire.data.remote.TextSource.OTHER_OUTLETS) {
                    Text(
                        tr(R.string.reader_auto_other, article.source ?: article.domain ?: "", s.content.viaName ?: tr(R.string.reader_other_outlet)),
                        style = sans(13.sp, FontWeight.Medium, lineHeight = 19.sp), color = g.indigo,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(14.dp))
                            .background(g.indigo.copy(alpha = .10f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
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
                val mediaAfter = remember(s.content) { s.content.media.groupBy { it.after } }
                s.content.paragraphs.forEachIndexed { i, para ->
                    // Drop caps are a Latin-type tradition; Arabic (and Hebrew) text opens plainly.
                    Box(Modifier.revealOnScroll()) {
                        if (i == 0 && !RX_RTL.containsMatchIn(para.take(40))) DropCapParagraph(para) else BodyParagraph(para)
                    }
                    Spacer(Modifier.height(16.dp))
                    // The story's own photos and videos, where the publisher placed them.
                    mediaAfter[i]?.forEach { m -> Box(Modifier.revealOnScroll()) { StoryMedia(m) }; Spacer(Modifier.height(16.dp)) }
                }
                if (s.content.embeds.isNotEmpty()) {
                    EmbeddedPosts(s.content.embeds, actions.loadPost)
                    Spacer(Modifier.height(16.dp))
                }
                if (s.content.partial) {
                    Text(
                        tr(R.string.reader_preview),
                        style = sans(13.sp, lineHeight = 20.sp), color = g.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Only a teaser: the whole story may be open at another newsroom.
                    SlowArticleCard(failed = true, onRetry = { attempt++ }, onOthers = openOthers, smryUrl = if (service == null) url else "")
                }
                if (s.content.via != news.openwire.data.remote.TextSource.PUBLISHER) {
                    val viaUrl = s.content.viaUrl
                    Text(
                        tr(R.string.reader_text_via, if (s.content.via == news.openwire.data.remote.TextSource.OTHER_OUTLETS) s.content.viaName ?: s.content.via.label else s.content.via.label),
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
    Text(text, style = bodyStyle.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), color = Glass.textPrimary.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth())
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

/** How long an article loads before the reader is offered Retry or other outlets. */
private const val SLOW_ARTICLE_MS = 12_000L

/** A slim accent bar sweeping across while the article loads. */
@Composable
private fun LoadingBar(label: String) {
    val g = Glass
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        SweepBar()
        Text(label, style = mono(10.sp, tracking = 0.06.em), color = g.textSecondary, modifier = Modifier.padding(top = 8.dp))
    }
}

/** An indeterminate progress bar: an accent highlight sweeping along a hairline track. */
@Composable
internal fun SweepBar(modifier: Modifier = Modifier) {
    val g = Glass
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "load")
    val x by t.animateFloat(
        -0.4f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        label = "sweep",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(g.hairline)) {
            Box(
                Modifier.fillMaxHeight().width(maxWidth * 0.4f).offset(x = maxWidth * x).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(g.accent.copy(alpha = 0f), g.accent, g.accentSoft))),
            )
    }
}

/** Offered when an article is slow or fails: try again, read other outlets, or smry.ai. */
@Composable
private fun SlowArticleCard(failed: Boolean, onRetry: () -> Unit, onOthers: (() -> Unit)?, smryUrl: String) {
    val g = Glass
    val context = LocalContext.current
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentPadding = PaddingValues(18.dp)) {
        if (!failed) {
            Text(tr(R.string.reader_slow_t), style = serif(19.sp, FontWeight.Bold, lineHeight = 24.sp), color = g.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.reader_slow_b), style = sans(13.sp, lineHeight = 20.sp), color = g.textSecondary)
            Spacer(Modifier.height(14.dp))
        }
        // Two full-width, round-cornered buttons (stacked on a narrow screen, side by side otherwise).
        val shape = RoundedCornerShape(14.dp)
        @Composable
        fun RetryButton(m: Modifier) = Text(
            tr(R.string.reader_retry), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary, textAlign = TextAlign.Center,
            modifier = m.clip(shape).background(g.chipFill).border(1.dp, g.hairline, shape).clickable(onClick = onRetry).padding(vertical = 13.dp),
        )
        @Composable
        fun OthersButton(m: Modifier, go: () -> Unit) = Text(
            tr(R.string.reader_other), style = sans(14.sp, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center,
            modifier = m.clip(shape).background(g.accent).clickable(onClick = go).padding(vertical = 13.dp),
        )
        if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 520) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                onOthers?.let { OthersButton(Modifier.fillMaxWidth(), it) }
                RetryButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RetryButton(Modifier.weight(1f))
                onOthers?.let { OthersButton(Modifier.weight(1.6f), it) }
            }
        }
        if (smryUrl.isNotEmpty()) {
            Text(
                tr(R.string.reader_smry), style = mono(10.sp, FontWeight.SemiBold, 0.06.em), color = g.indigo,
                modifier = Modifier.padding(top = 12.dp).clip(CircleShape).clickable {
                    runCatching { androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse("https://smry.ai/$smryUrl")) }
                }.padding(vertical = 4.dp),
            )
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
    // Same engine choice as the briefing: Google's, else Editrove's own voice, else the phone's.
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, { status ->
        ready = status == TextToSpeech.SUCCESS
    }, news.openwire.i18n.VoiceSetup.engine(context.applicationContext)).apply {
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

/** Other newsrooms' reports of the story: not asked for yet, loading, none found, or ready. */
internal sealed interface OthersState {
    data object Idle : OthersState
    data object Loading : OthersState
    data object Empty : OthersState
    data class Ready(val reports: List<news.openwire.data.remote.ArticleContent>) : OthersState
}

/**
 * "Read from other outlets", beside the source: gold on gold (like the red section pill) so it
 * stands out; while other outlets are shown it becomes the way back to the original.
 */
@Composable
private fun OtherOutletsPill(showing: Boolean, original: String, onClick: () -> Unit) {
    val g = Glass
    val gold = if (g.deviceBg.luminance() < 0.5f) GOLD_ON_DARK else GOLD_ON_LIGHT
    Row(
        Modifier.clip(CircleShape)
            .background(gold.copy(alpha = if (showing) 0.24f else 0.15f))
            .border(1.dp, gold.copy(alpha = 0.40f), CircleShape)
            .clickable(onClickLabel = if (showing) tr(R.string.reader_others_back, original) else tr(R.string.reader_others_pill), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (showing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Outlined.Newspaper, null, tint = gold, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (showing) tr(R.string.reader_others_back, original) else tr(R.string.reader_others_pill),
            style = mono(10.sp, FontWeight.SemiBold, 0.08.em), color = gold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val GOLD_ON_DARK = Color(0xFFF5C451)
private val GOLD_ON_LIGHT = Color(0xFF9A6A00)

/**
 * The same story from other newsrooms, one tab per outlet (logo and name), the most detailed
 * report first. Swipe the report or tap a tab to switch; every report is credited and links
 * to its source.
 */
@Composable
private fun OtherOutletsView(state: OthersState, original: String, onBack: () -> Unit, loadPost: suspend (String) -> news.openwire.domain.opinion.SocialPost?) {
    val g = Glass
    when (state) {
        OthersState.Idle, OthersState.Loading -> { LoadingBar(tr(R.string.reader_other_loading)); BodySkeleton() }
        OthersState.Empty -> Text(tr(R.string.reader_other_none), style = sans(14.sp, lineHeight = 22.sp), color = g.textSecondary)
        is OthersState.Ready -> {
            val reports = state.reports
            var selected by androidx.compose.runtime.saveable.rememberSaveable(reports.size) { androidx.compose.runtime.mutableIntStateOf(0) }
            val tabs = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(selected) { tabs.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
            val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(tr(R.string.reader_others_header, reports.size).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                    Text(
                        tr(R.string.reader_others_back, original), style = sans(12.sp, FontWeight.Medium), color = g.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                // Tabs: each outlet's logo and name; the selected one filled.
                androidx.compose.foundation.lazy.LazyRow(
                    state = tabs, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    items(reports.size) { i ->
                        val r = reports[i]
                        val on = i == selected
                        val bg by animateColorAsState(if (on) g.accent else g.chipFill, springTween(260), label = "tab")
                        Row(
                            Modifier.clip(CircleShape).background(bg).border(1.dp, if (on) Color.Transparent else g.hairline, CircleShape)
                                .semantics { this.selected = on; role = androidx.compose.ui.semantics.Role.Tab }
                                .clickable { selected = i }
                                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val domain = r.viaUrl?.let { news.openwire.domain.engine.FeedDiscovery.domainOf(it) }
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { SourceLogo(domain, r.viaName, size = 18.dp) }
                            Spacer(Modifier.width(8.dp))
                            Text(r.viaName ?: domain.orEmpty(), style = sans(13.sp, if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) Color.White else g.textPrimary, maxLines = 1)
                        }
                    }
                }
                // The report: swipe sideways for the next outlet.
                var drag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
                val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
                androidx.compose.animation.AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        val forward = (targetState > initialState) != rtl
                        (androidx.compose.animation.slideInHorizontally(springTween(340)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(springTween(260)))
                            .togetherWith(androidx.compose.animation.slideOutHorizontally(springTween(260)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(140)))
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "outlet",
                    modifier = Modifier.fillMaxWidth().pointerInput(reports.size, rtl) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onHorizontalDrag = { _, dx -> drag += dx },
                            onDragEnd = {
                                val towardsNext = if (rtl) drag > threshold else drag < -threshold
                                val towardsPrev = if (rtl) drag < -threshold else drag > threshold
                                if (towardsNext && selected < reports.lastIndex) selected++
                                else if (towardsPrev && selected > 0) selected--
                                drag = 0f
                            },
                        )
                    },
                ) { i -> OutletReport(reports[i], loadPost) }
                // Step through outlets without hunting for the tab.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected + 1} / ${reports.size}", style = mono(11.sp, tracking = 0.06.em), color = g.textSecondary, modifier = Modifier.weight(1f))
                    reports.getOrNull(selected + 1)?.let { n ->
                        Text(
                            tr(R.string.reader_others_next, n.viaName ?: ""), style = sans(13.sp, FontWeight.SemiBold), color = g.accentSoft,
                            modifier = Modifier.clip(CircleShape).clickable { selected++ }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One outlet's report: its headline and deck, key points, the text with its photos, credit and link. */
@Composable
private fun OutletReport(c: news.openwire.data.remote.ArticleContent, loadPost: suspend (String) -> news.openwire.domain.opinion.SocialPost?) {
    val g = Glass
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        if (c.title.isNotBlank()) {
            Text(c.title, style = serif(21.sp, FontWeight.Bold, lineHeight = 27.sp), color = g.textPrimary, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        c.description?.let { Text(it, style = sans(14.sp, lineHeight = 22.sp).reading(), color = g.textPrimary.copy(alpha = 0.75f), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }
        Text(
            listOfNotNull(c.author, c.viaName).joinToString(" · "),
            style = mono(10.sp, tracking = 0.06.em), color = g.textSecondary, modifier = Modifier.padding(bottom = 14.dp),
        )
        val points = remember(c) { news.openwire.domain.engine.Summarizer.keyPoints(c.text, 3) }
        if (points.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.reader_summary), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                    Text(tr(R.string.reader_key_points), style = sans(10.sp), color = g.textSecondary)
                }
                points.forEach { point ->
                    Row(Modifier.padding(top = 10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(g.accent))
                        Spacer(Modifier.width(10.dp))
                        Text(point, style = sans(13.5.sp, lineHeight = 21.sp).reading(), color = g.textPrimary.copy(alpha = 0.92f), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        c.imageUrl?.takeIf { c.media.none { m -> m.url == it } }?.let { lead ->
            StoryMedia(news.openwire.data.remote.ArticleMedia(news.openwire.data.remote.ArticleMedia.Kind.IMAGE, lead))
            Spacer(Modifier.height(16.dp))
        }
        val mediaAfter = remember(c) { c.media.groupBy { it.after } }
        c.paragraphs.forEachIndexed { i, para ->
            if (i == 0 && !RX_RTL.containsMatchIn(para.take(40))) DropCapParagraph(para) else BodyParagraph(para)
            Spacer(Modifier.height(16.dp))
            mediaAfter[i]?.forEach { m -> StoryMedia(m); Spacer(Modifier.height(16.dp)) }
        }
        if (c.embeds.isNotEmpty()) { EmbeddedPosts(c.embeds, loadPost); Spacer(Modifier.height(16.dp)) }
        c.viaUrl?.let { link ->
            Row(
                Modifier.clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape)
                    .clickable { runCatching { androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(link)) } }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr(R.string.reader_others_open, c.viaName ?: ""), style = sans(13.sp, FontWeight.SemiBold), color = g.accentSoft)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = g.accentSoft, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private sealed interface Points {
    data object Waiting : Points
    data object Writing : Points
    data class Done(val points: List<String>, val ms: Long) : Points
}

/**
 * The key-points card: shimmer until the summary starts; the points written letter by letter as
 * they arrive; then the real time it took on this phone. No card when there's nothing to summarise.
 */
@Composable
private fun KeyPointsCard(body: BodyState, content: news.openwire.data.remote.ArticleContent?) {
    val g = Glass
    val points by produceState<Points>(Points.Waiting, content) {
        val c = content ?: return@produceState
        value = Points.Writing
        val t0 = System.nanoTime()
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            c.combinedPoints.ifEmpty { news.openwire.domain.engine.Summarizer.keyPoints(c.text, 3) }
        }
        value = Points.Done(list, (System.nanoTime() - t0) / 1_000_000)
    }
    val p = points
    if (body is BodyState.Failed || (p is Points.Done && p.points.isEmpty())) return
    var written by remember(content) { mutableIntStateOf(0) }
    val done = p is Points.Done && written >= p.points.size
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.reader_summary), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft, modifier = Modifier.weight(1f))
            if (!done) {
                OrbitLoader(running = true)
                Spacer(Modifier.width(6.dp))
                Text(tr(R.string.reader_points_working), style = sans(10.sp), color = g.textSecondary)
            } else {
                val ms = (p as Points.Done).ms
                val loc = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                Text(
                    if (ms < 1000) tr(R.string.reader_points_done_ms, ms.coerceAtLeast(1)) else tr(R.string.reader_points_done, String.format(loc, "%.1f", ms / 1000.0)),
                    style = sans(10.sp), color = g.textSecondary,
                )
            }
        }
        when (p) {
            Points.Waiting, Points.Writing -> ShimmerLines()
            is Points.Done -> {
                var offset = 0
                p.points.forEach { point ->
                    val at = offset
                    offset += point.length + 4
                    Row(Modifier.padding(top = 10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(g.accent))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            WrittenText(point, startAt = at, style = sans(13.5.sp, lineHeight = 21.sp).reading(), settled = g.textPrimary.copy(alpha = 0.92f)) { written++ }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The story's main photo, as sharp as can be had: the feed image at the publisher's large size when
 * its address says how (BBC, NYT, Guardian…), otherwise the article page's own photo once it loads,
 * the feed image only as a last resort. The small feed image stands in, blurred, until then.
 */
@Composable
internal fun HeroPhoto(
    article: Article,
    content: news.openwire.data.remote.ArticleContent?,
    loading: Boolean,
    aspect: Float,
    radius: androidx.compose.ui.unit.Dp,
    captionGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val g = Glass
    val feed = article.imageUrl?.takeIf { it.startsWith("http") }
    val page = content?.imageUrl?.takeIf { it.startsWith("http") }
    val upgraded = remember(feed) { feed?.let { news.openwire.domain.engine.ImageExtractor.highRes(it) } }
    val full = when {
        upgraded != null && upgraded != feed -> upgraded // a known large size of the same photo
        page != null -> news.openwire.domain.engine.ImageExtractor.highRes(page) ?: page
        loading && feed != null -> null // wait for the page's photo; the thumbnail develops meanwhile
        else -> feed
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(aspect)
            .sharedArticleImage(article.id, hasImage = feed != null, cornerRadius = radius)
            .clip(RoundedCornerShape(radius))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(radius)),
    ) {
        var failed by remember(full) { mutableStateOf(false) }
        val settled = rememberReaderSettled()
        if (failed || full == null) {
            // No photo to be had (or it won't load): the publisher's canvas develops the same way,
            // once the page has finished looking for one.
            DevelopReveal(ready = !loading || failed, start = settled, modifier = Modifier.fillMaxSize()) {
                ArticleImage(article.copy(imageUrl = if (failed) null else feed), Modifier.fillMaxSize(), logoSize = 72.dp)
            }
        } else {
            // No thumbnail to soften: the publisher canvas stays soft underneath until the photo arrives.
            if (feed == null) DevelopReveal(ready = false, start = false, modifier = Modifier.fillMaxSize()) {
                ArticleImage(article.copy(imageUrl = null), Modifier.fillMaxSize(), logoSize = 72.dp)
            }
            DevelopingImage(preview = feed, full = full, modifier = Modifier.fillMaxSize(), start = settled, onFailed = { failed = true })
        }
    }
    Text(
        // A photo from another newsroom's report is credited to it.
        tr(
            R.string.reader_photo,
            if (full != null && full != upgraded && content?.via == news.openwire.data.remote.TextSource.OTHER_OUTLETS) (content.viaName ?: article.publication()).uppercase()
            else article.publication(),
        ),
        style = mono(10.sp, tracking = 0.05.em),
        color = g.textPrimary.copy(alpha = 0.5f),
        textAlign = TextAlign.End,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Spacer(Modifier.height(captionGap))
}
