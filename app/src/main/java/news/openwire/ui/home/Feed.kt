// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import news.openwire.i18n.tr
import news.openwire.i18n.text
import news.openwire.R
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import news.openwire.domain.engine.FeedWeaver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Refresh
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.domain.model.Topic
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Emerald400
import news.openwire.ui.theme.Fuchsia400
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.InterFamily
import news.openwire.ui.theme.JetBrainsMonoFamily
import news.openwire.ui.theme.NewsreaderFamily
import news.openwire.ui.theme.Obsidian
import news.openwire.ui.theme.Rose200
import news.openwire.ui.theme.Rose400
import news.openwire.ui.theme.Slate300

// ---- Type helpers matching the prototype's Tailwind classes ------------------

fun mono(size: TextUnit, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.08.em) =
    TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = size, fontWeight = weight, letterSpacing = tracking)

fun sans(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = InterFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight)

/** Reading text (excerpts, decks, key points): Arabic sits on the right, whatever the app's language. */
fun TextStyle.reading(): TextStyle = copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content)

fun serif(size: TextUnit, weight: FontWeight = FontWeight.Bold, lineHeight: TextUnit = TextUnit.Unspecified, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = NewsreaderFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = tracking, textDirection = androidx.compose.ui.text.style.TextDirection.Content)

fun Article.publication(): String = (source ?: domain ?: "").uppercase()

/** "Also covered by NYT, BBC +3", or null when only one outlet has the story. */
fun Article.coverageLine(names: Int = 2): String? {
    if (alsoCoveredBy.isEmpty()) return null
    val shown = alsoCoveredBy.take(names).joinToString(", ")
    val more = alsoCoveredBy.size - names
    return tr(R.string.also_covered, shown) + if (more > 0) " +$more" else ""
}

/** Paywall badge from the routing engine, shown where the mock shows read time. */
fun Article.badgeLabel(): String? {
    val raw = badge?.takeIf { it.isNotBlank() } ?: status?.replaceFirstChar { it.uppercase() } ?: return null
    // Service names stay as they are; the plain words are translated.
    return when (raw) {
        "Free" -> tr(R.string.badge_free)
        "Locked" -> tr(R.string.badge_locked)
        "Archive" -> tr(R.string.badge_archive)
        "Bypassed" -> tr(R.string.badge_bypassed)
        "Open source" -> tr(R.string.badge_open_source)
        else -> raw
    }
}

// ---- Masthead + category pills ----------------------------------------------

/** "SUNDAY • NOV 15" / Editrove masthead with the edition pill and settings on the right. */
@Composable
fun Masthead(edition: String, onEdition: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val g = Glass
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE • MMM d", Locale.getDefault())).uppercase()
    }
    // Top row, just under the glass fade: the date, and at the right the edition pill and
    // settings (sized so the whole edition name shows); the wordmark below.
    Column(modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = mono(11.sp, FontWeight.SemiBold, 0.18.em), color = g.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(CircleShape)
                    .background(g.chipFill)
                    .border(1.dp, g.hairline, CircleShape)
                    .clickable(onClick = onEdition)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(edition, style = sans(12.5.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Outlined.Settings, contentDescription = tr(R.string.settings),
                    tint = g.textPrimary, modifier = Modifier.size(17.dp),
                )
            }
        }
        // The pull's one "ink" detail: the wordmark gets very slightly heavier (wght 700 → 800) and
        // tighter (−0.5 sp) as the rule grows. Stepped, so it reuses five typefaces.
        val inkState = LocalPullInk.current
        val ink: Int by remember(inkState) { androidx.compose.runtime.derivedStateOf { (inkState.value * 5f).roundToInt() } }
        Text(
            "Editrove",
            style = if (ink == 0) serif(40.sp, FontWeight.Bold, lineHeight = 40.sp, tracking = (-0.02).em)
            else serif(40.sp, FontWeight(700 + ink * 20), lineHeight = 40.sp, tracking = (40 * -0.02f - 0.1f * ink).sp)
                .copy(fontFamily = news.openwire.ui.theme.VariableType.newsreader(700 + ink * 20)),
            color = g.textPrimary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Sort mode selector + freshness / refresh action under the category pills (SPEC §6.1). */
@Composable
fun FeedControls(
    sortMode: news.openwire.domain.model.SortMode,
    updatedAt: Long,
    refreshing: Boolean,
    failed: List<String>,
    onSort: (news.openwire.domain.model.SortMode) -> Unit,
    onRefresh: () -> Unit,
) {
    val g = Glass
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            news.openwire.domain.model.SortMode.entries.forEach { m ->
                val active = m == sortMode
                val bg by animateColorAsState(if (active) g.glassElevated else Color.Transparent, tween(240, easing = SpringEasing), label = "sortBg")
                Box(
                    Modifier.clip(CircleShape).background(bg).clickable { onSort(m) }.padding(horizontal = 10.dp, vertical = 5.dp),
                ) { Text(m.text, style = sans(11.sp, if (active) FontWeight.SemiBold else FontWeight.Medium), color = if (active) g.textPrimary else g.textSecondary) }
            }
        }
        Spacer(Modifier.weight(1f))
        // While refreshing, the last time stays up (the dot pulses); when it lands, the time rolls
        // over like an odometer and the offline count updates.
        var shownAt by remember { mutableStateOf(updatedAt) }
        var shownFailed by remember { mutableStateOf(failed) }
        if (!refreshing) { shownAt = updatedAt; shownFailed = failed }
        // Keep the relative time current while the screen is open.
        var minute by remember { mutableStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); minute++ } }
        val label = remember(shownAt, shownFailed, minute) {
            when {
                shownAt > 0 -> tr(R.string.status_updated, NewsProcessor.formatRelativeTime(shownAt))
                else -> tr(R.string.status_saved_copy)
            } + if (shownFailed.isNotEmpty()) " · " + tr(if (shownFailed.size > 1) R.string.status_offline_many else R.string.status_offline_one, shownFailed.size) else ""
        }
        Row(
            Modifier.clip(CircleShape).clickable(enabled = !refreshing, onClickLabel = tr(R.string.action_refresh), onClick = onRefresh).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseDot(refreshing)
            Spacer(Modifier.width(7.dp))
            Odometer(label, style = mono(10.sp, tracking = 0.sp), color = g.textSecondary)
        }
    }
}

/** The status line's rose dot: still when idle, a slow breath while refreshing. */
@Composable
private fun PulseDot(refreshing: Boolean) {
    val reduce = news.openwire.ui.theme.rememberReduceMotion()
    val k = if (refreshing && !reduce) rememberInfiniteTransition(label = "dot")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k") else null
    Box(
        Modifier.size(6.dp).graphicsLayer {
            val v = k?.value ?: 0f
            alpha = 1f - .55f * v; scaleX = 1f + .35f * v; scaleY = scaleX
        }.clip(CircleShape).background(Glass.accent),
    )
}


/**
 * Text that rolls over like an odometer when it changes: the part that stays put doesn't move,
 * the rest rolls up (420 ms, emphasized decelerate). "18 min ago" → "just now".
 */
@Composable
internal fun Odometer(text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    var previous by remember { mutableStateOf(text) }
    val keep = remember(text) { previous.commonPrefixWith(text).let { p -> p.substring(0, p.lastIndexOf(' ').let { if (it < 0) 0 else it + 1 }) } }
    LaunchedEffect(text) { previous = text }
    Row {
        if (keep.isNotEmpty()) Text(keep, style = style, color = color, maxLines = 1)
        androidx.compose.animation.AnimatedContent(
            targetState = text.removePrefix(keep),
            transitionSpec = {
                (androidx.compose.animation.slideInVertically(tween(420, easing = EmphasizedDecelerate)) { it } + androidx.compose.animation.fadeIn(tween(300))) togetherWith
                    (androidx.compose.animation.slideOutVertically(tween(420, easing = EmphasizedDecelerate)) { -it } + androidx.compose.animation.fadeOut(tween(200)))
            },
            label = "odometer",
        ) { Text(it, style = style, color = color, maxLines = 1) }
    }
}

/** Horizontal swipeable category pills; the active one is solid rose. */
@Composable
fun CategoryPills(
    topics: List<Topic>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = Glass
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { if (selectedIndex != null) listState.animateScrollToItem(maxOf(0, selectedIndex - 1)) }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(topics, key = { _, t -> t.id }) { index, topic ->
            val active = index == selectedIndex
            val bg by animateColorAsState(if (active) g.accent else g.chipFill, tween(280, easing = SpringEasing), label = "pillBg")
            val fg by animateColorAsState(if (active) Color.White else g.textPrimary, tween(280), label = "pillFg")
            val glow by animateDpAsState(if (active) 8.dp else 0.dp, tween(280), label = "pillGlow")
            Box(
                modifier = Modifier
                    .glowShadow(g.accent, glow)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (active) Color.Transparent else g.hairline, CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    topic.label,
                    style = sans(12.sp, if (active) FontWeight.SemiBold else FontWeight.Medium),
                    color = fg,
                )
            }
        }
    }
}

/** Soft colored glow under an accent element (`shadow-md shadow-rose-500/25`). */
fun Modifier.glowShadow(color: Color, radius: Dp = 8.dp): Modifier =
    this.shadow(radius, CircleShape, clip = false, ambientColor = color, spotColor = color)

// ---- Feed grid -----------------------------------------------------------------

/** The single lazy grid every tab renders into (two columns; 3–4 in Full view, 12–16dp gutters). */
@Composable
fun EditorialGrid(
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    content: LazyGridScope.() -> Unit,
) {
    val gap = if (columns > 2) 16.dp else 12.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** A full-width grid row that animates in/out with the feed. */
fun LazyGridScope.fullRow(key: Any, content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, key = key) { Box(feedItemMotion()) { content() } }

/**
 * The editorial feed (as in the prototype): live ticker, full-bleed hero cover,
 * then the asymmetric two-column Bento grid.
 */
fun LazyGridScope.feedItems(
    articles: List<Article>,
    category: String,
    compact: Boolean,
    selectedId: String?,
    savedIds: Set<String>,
    likedIds: Set<String>,
    onOpen: (Article) -> Unit,
    onToggleSaved: (Article) -> Unit,
    onToggleLiked: (Article) -> Unit,
    showTicker: Boolean = true,
    sectionTitle: String = tr(R.string.feed_bento),
    archive: Map<String, ArchiveState> = emptyMap(),
    /** Full view: magazine lead card and multi-column grid with feature cards. */
    magazine: Boolean = false,
    /** Business tab: the market-watch bar under the LIVE ticker. */
    marketWatch: Boolean = false,
    /** The live bar's stories across topics (null: this feed's own). */
    ticker: List<TickerItem>? = null,
    /** Stories that arrived with the last refresh, and when the one before it was. */
    newIds: Set<String> = emptySet(),
    newSince: Long = 0L,
    /** The tab's news Shorts ("Video moments"), shown after the first few stories. */
    videos: List<news.openwire.domain.video.ShortVideo> = emptyList(),
    /** The grid's column count (rows are filled to it). */
    columns: Int = 2,
    /** Stories like the ones the reader loved (For You). */
    forYou: List<Article> = emptyList(),
) {
    if (articles.isEmpty()) return
    // New since the last refresh: under a divider at the top, each with the rose "new" dot.
    val fresh = if (newIds.isEmpty()) emptyList() else articles.filter { it.id in newIds }
    if (fresh.isNotEmpty()) {
        fullRow("new-divider-$newSince") { NewDivider(fresh.size, newSince) }
        fullRow("new-grid-$newSince") { NewSinceGrid(fresh, onOpen) }
    }
    fun state(a: Article) = a.originalUrl?.let { archive[it] }
    if (showTicker) fullRow("ticker") { LiveTicker(ticker ?: articles.drop(1).take(12).map { TickerItem(it, category, category) }, onOpen) }
    if (showTicker && marketWatch) fullRow("markets") { MarketBar() }
    // The lead: the top four stories as a slideshow (5 s each; a touch holds it for 10 s).
    val leads = articles.take(LEAD_SLIDES)
    fullRow("hero-slides") {
        // The magazine card has its buttons bottom-right: dots go bottom-left, over the photo.
        LeadSlideshow(leads, dotsAtStart = magazine) { hero ->
            Box(Modifier.landing(hero.id)) {
                if (magazine) LeadStoryCard(
                    article = hero.withArchive(state(hero)),
                    locked = state(hero) == ArchiveState.Locked,
                    category = category,
                    saved = hero.id in savedIds,
                    onOpen = onOpen,
                    onToggleSaved = onToggleSaved,
                    liked = hero.id in likedIds,
                    onToggleLiked = onToggleLiked,
                ) else HeroCard(
                    article = hero.withArchive(state(hero)),
                    locked = state(hero) == ArchiveState.Locked,
                    category = category,
                    selected = !compact && selectedId == hero.id,
                    saved = hero.id in savedIds,
                    onOpen = onOpen,
                    onToggleSaved = onToggleSaved,
                    liked = hero.id in likedIds,
                    onToggleLiked = onToggleLiked,
                )
            }
        }
    }
    val rest = articles.drop(leads.size)
    if (rest.isNotEmpty()) {
        fullRow("bento-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    sectionTitle.uppercase(),
                    style = mono(12.sp, FontWeight.SemiBold, 0.16.em),
                    color = Glass.textPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                Text(tr(R.string.curated_reads, rest.size), style = sans(11.sp, FontWeight.Medium), color = Glass.accentSoft)
            }
        }
    }
    // Highlighted sections between runs of stories, each with a full-width story on either side.
    val specials = buildList<Pair<Int, String>> {
        if (videos.isNotEmpty()) add(VIDEOS_AFTER to "videos")
        if (forYou.size >= 4) add((if (videos.isNotEmpty()) FOR_YOU_AFTER else VIDEOS_AFTER) to "foryou")
    }.map { (at, k) -> at.coerceAtMost(rest.size) to k }
    var from = 0
    specials.forEachIndexed { n, (at, kind) ->
        if (at > from) bento(rest.subList(from, at), from, compact, magazine, category, selectedId, savedIds, likedIds, ::state, onOpen, onToggleSaved, onToggleLiked,
            columns, wideFirst = n > 0 || false, wideLast = true)
        when (kind) {
            "videos" -> fullRow("videos") { VideoMoments(videos, compact) }
            else -> fullRow("foryou") { ForYouSection(forYou, compact, savedIds, likedIds, onOpen, onToggleSaved, onToggleLiked) }
        }
        from = maxOf(from, at)
    }
    if (rest.size > from) bento(rest.drop(from), from, compact, magazine, category, selectedId, savedIds, likedIds, ::state, onOpen, onToggleSaved, onToggleLiked,
        columns, wideFirst = specials.isNotEmpty())
}

/** Stories between Video moments and For You. */
private const val FOR_YOU_AFTER = 12

/** Stories under the lead before the video carousel. */
private const val VIDEOS_AFTER = 4

private fun LazyGridScope.bento(
    rest: List<Article>, offset: Int, compact: Boolean, magazine: Boolean, category: String,
    selectedId: String?, savedIds: Set<String>, likedIds: Set<String>, state: (Article) -> ArchiveState?,
    onOpen: (Article) -> Unit, onToggleSaved: (Article) -> Unit, onToggleLiked: (Article) -> Unit,
    columns: Int, wideFirst: Boolean = false, wideLast: Boolean = false,
) {
    val spans = BentoLayout.spans(rest.size, columns, offset, compact, magazine, wideFirst, wideLast)
    itemsIndexed(
        rest,
        key = { _, a -> a.id },
        span = { i0, _ -> GridItemSpan(spans[i0].coerceAtMost(maxLineSpan)) },
    ) { i0, article ->
        Box(feedItemMotion().landing(article.id)) {
            BentoCard(
                article = article.withArchive(state(article)),
                locked = state(article) == ArchiveState.Locked,
                category = category,
                wide = spans[i0] >= 2,
                selected = !compact && selectedId == article.id,
                saved = article.id in savedIds,
                liked = article.id in likedIds,
                onOpen = onOpen,
                onToggleSaved = onToggleSaved,
                onToggleLiked = onToggleLiked,
            )
        }
    }
}

/**
 * How wide each story card is, so rows always fill: the usual rhythm (every 7th wide on the cover
 * screen, every 5th on the magazine), full-width cards next to the video row, and a card that
 * would leave a gap (a wide one that doesn't fit, or a lone last one) stretched to fill its row.
 */
internal object BentoLayout {
    fun spans(n: Int, columns: Int, offset: Int, compact: Boolean, magazine: Boolean, wideFirst: Boolean, wideLast: Boolean): IntArray {
        val out = IntArray(n)
        var col = 0
        for (i in 0 until n) {
            val index = i + offset
            val forced = (wideFirst && i == 0) || (wideLast && i == n - 1)
            var w = when {
                forced -> columns
                magazine && index % 5 == 0 && columns > 2 -> 2
                compact && index % 7 == 2 -> columns
                else -> 1
            }.coerceAtMost(columns)
            if (col + w > columns) {
                // Doesn't fit beside the previous card: that one stretches to close the row.
                if (i > 0) out[i - 1] += columns - col
                col = 0
            }
            out[i] = w
            col += w
            if (col >= columns) col = 0
        }
        // A part-filled last row: its last card takes the rest.
        if (n > 0 && col > 0) out[n - 1] += columns - col
        return out
    }
}

// ---- Live ticker -----------------------------------------------------------------


/** One story in the live bar, with the topic it comes from. */
data class TickerItem(val article: Article, val topicId: String, val topicLabel: String)

object TickerFeed {
    private val LIVE = Regex("(?i)\\b(live|live updates|breaking|just in|developing)\\b|مباشر|عاجل|en direct|en vivo|liveticker")

    /**
     * The newest stories from every topic shown: live coverage and breaking stories first, then
     * by time, one story per headline, topics interleaved so no single one fills the bar.
     */
    fun build(topics: List<Pair<news.openwire.domain.model.Topic, List<Article>>>, now: Long = System.currentTimeMillis(), max: Int = 24): List<TickerItem> {
        val seen = HashSet<String>()
        val all = topics.flatMap { (t, list) -> list.take(15).map { TickerItem(it, t.id, t.label) } }
            .filter { (it.article.publishedAt ?: 0L) > now - 36 * 3_600_000L || it.article.publishedAt == null }
        val live = all.filter { LIVE.containsMatchIn(it.article.title) && (it.article.publishedAt ?: now) > now - 6 * 3_600_000L }
            .sortedByDescending { it.article.publishedAt ?: 0L }
        val rest = all.sortedByDescending { it.article.publishedAt ?: 0L }
        val out = ArrayList<TickerItem>()
        val perTopic = HashMap<String, Int>()
        for (it in live + rest) {
            val key = it.article.title.lowercase().take(60)
            if (!seen.add(key)) continue
            val n = perTopic[it.topicId] ?: 0
            if (n >= 4 && it !in live) continue
            perTopic[it.topicId] = n + 1
            out += it
            if (out.size >= max) break
        }
        return out
    }
}

/** Topic colours for the live bar, in the app's palette. */
private val TopicTint = listOf(Rose400, Indigo400, Emerald400, Amber400, Fuchsia400, Color(0xFF38BDF8), Color(0xFFA3E635), Color(0xFFF472B6))

private fun tintFor(topicId: String) = TopicTint[Math.floorMod(topicId.hashCode(), TopicTint.size)]

/**
 * The live bar: the latest across your topics, drifting continuously (Arabic headlines drift the
 * other way); swipe it back and forth, and it carries on a moment after you let go.
 */
@Composable
fun LiveTicker(items: List<TickerItem>, onOpen: (Article) -> Unit) {
    val g = Glass
    if (items.isEmpty()) return
    // Repeated, so the drift never runs out and you can swipe back past the start.
    val loop = remember(items) { List(LOOPS) { r -> items.mapIndexed { i, it -> Triple(r * items.size + i, it, i) } }.flatten() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.size * (LOOPS / 2))
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    val arabic = remember(items) { items.count { RX_AR.containsMatchIn(it.article.title) } * 2 > items.size }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val speed = with(density) { TICKER_DP_PER_SEC.dp.toPx() }
    LaunchedEffect(items, dragged) {
        if (dragged) return@LaunchedEffect
        delay(if (listState.firstVisibleItemScrollOffset == 0 && listState.firstVisibleItemIndex == items.size * (LOOPS / 2)) 400 else 1800)
        var last = 0L
        while (isActive) {
            val t = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceAtMost(0.05f)
            last = t
            try { listState.scrollBy(speed * dt) } catch (e: CancellationException) { if (!isActive) throw e }
            // Far along the loop: jump back by whole copies (invisible), so it never ends.
            if (listState.firstVisibleItemIndex > items.size * (LOOPS - 1)) {
                listState.scrollToItem(listState.firstVisibleItemIndex - items.size * (LOOPS / 2), listState.firstVisibleItemScrollOffset)
            }
        }
    }
    LaunchedEffect(dragged) {
        if (!dragged && listState.firstVisibleItemIndex < items.size) {
            listState.scrollToItem(listState.firstVisibleItemIndex + items.size * (LOOPS / 2), listState.firstVisibleItemScrollOffset)
        }
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivePill(pulsing = true)
            Spacer(Modifier.width(10.dp))
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides if (arabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                LazyRow(state = listState, modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    items(loop.size, key = { loop[it].first }) { k ->
                        val item = loop[k].second
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("•", style = sans(12.sp), color = g.textPrimary.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 10.dp))
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = tintFor(item.topicId))) {
                                        append(item.topicLabel.uppercase())
                                    }
                                    append("  ")
                                    append(item.article.title)
                                    (item.article.source ?: item.article.domain)?.takeIf { it.isNotBlank() }?.let { src -> withStyle(SpanStyle(color = g.textSecondary)) { append("  · $src") } }
                                },
                                style = sans(12.sp),
                                color = g.textPrimary.copy(alpha = 0.88f),
                                maxLines = 1,
                                modifier = Modifier.clickable { onOpen(item.article) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LOOPS = 40
private const val TICKER_DP_PER_SEC = 42f
private val RX_AR = Regex("[\\u0600-\\u06FF]")

private fun shortSource(a: Article): String {
    val s = a.source ?: a.domain?.substringBefore('.') ?: "WIRE"
    return s.split(' ').let { if (it.size > 2) it.joinToString("") { w -> w.take(1) } else s }.uppercase()
}

@Composable
private fun LivePill(pulsing: Boolean) {
    val g = Glass
    // The ping ring animates only while the strip is moving (an idle screen should draw nothing).
    val ping = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(pulsing) {
        while (pulsing && isActive) {
            ping.snapTo(0f)
            ping.animateTo(1f, tween(1000, easing = LinearEasing))
        }
        ping.snapTo(1f) // ring hidden (alpha 0)
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(g.accent.copy(alpha = 0.20f))
            .border(1.dp, g.accent.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(6.dp)) {
            // animate-ping: an expanding, fading ring around the dot
            Box(
                Modifier.size(6.dp)
                    .graphicsLayer { val p = ping.value; scaleX = 1f + p; scaleY = 1f + p; alpha = 1f - p }
                    .clip(CircleShape).background(g.accent),
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(g.accent))
        }
        Spacer(Modifier.width(6.dp))
        Text(tr(R.string.live), style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = g.accentSoft)
    }
}

// ---- Hero cover ----------------------------------------------------------------

@Composable
fun HeroCard(
    article: Article,
    locked: Boolean = false,
    category: String,
    selected: Boolean,
    saved: Boolean,
    onOpen: (Article) -> Unit,
    onToggleSaved: (Article) -> Unit,
    liked: Boolean = false,
    onToggleLiked: ((Article) -> Unit)? = null,
) {
    GlassCard(
        onClick = { onOpen(article) },
        modifier = Modifier.fillMaxWidth().lockedLook(locked),
        contentPadding = PaddingValues(0.dp),
        selected = selected,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(310.dp)) {
            ArticleImage(
                article,
                Modifier.fillMaxSize().sharedArticleImage(article.id, article.imageUrl != null, BentoRadius),
                logoSize = 72.dp,
                grayscale = locked,
            )
            // Multi-stop mask for editorial legibility: from-obsidian via-obsidian/65 to-transparent (bottom → top)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Obsidian.copy(alpha = 0.65f), 1f to Obsidian),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrimPill { Text(category.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = Color.White, maxLines = 1) }
                Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
                // The badge gives way first when the card is narrow (the three buttons always fit).
                article.badgeLabel()?.let { badge ->
                    Box(Modifier.weight(1f, fill = false)) {
                        ScrimPill { Text(badge, style = mono(10.sp), color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                // Love, share, save: the same 15 dp icons in the same 31 dp circles.
                onToggleLiked?.let { like ->
                    SpringToggle(
                        active = liked, kind = ToggleKind.LIKE, onToggle = { like(article) }, iconSize = 15.dp,
                        inactiveTint = Color.White, background = Color.Black.copy(alpha = 0.45f), border = Color.White.copy(alpha = 0.20f), padding = 8.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                ShareButton(article, tint = Color.White, background = Color.Black.copy(alpha = 0.45f), border = Color.White.copy(alpha = 0.20f), padding = 8.dp, iconSize = 15.dp)
                Spacer(Modifier.width(6.dp))
                SpringToggle(
                    active = saved,
                    kind = ToggleKind.BOOKMARK,
                    onToggle = { onToggleSaved(article) },
                    iconSize = 15.dp,
                    inactiveTint = Color.White,
                    background = Color.Black.copy(alpha = 0.45f),
                    border = Color.White.copy(alpha = 0.20f),
                    padding = 8.dp,
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        article.publication(),
                        style = mono(10.sp, FontWeight.SemiBold, 0.16.em),
                        color = Rose400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("  •  ", style = mono(10.sp), color = Rose400)
                    Text(
                        NewsProcessor.formatRelativeTime(article.publishedAt).uppercase(),
                        style = mono(10.sp, tracking = 0.16.em),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    article.title,
                    style = serif(22.sp, FontWeight.Bold, lineHeight = 25.sp, tracking = (-0.01).em),
                    color = if (selected) Rose200 else Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                article.coverageLine()?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = sans(11.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---- Lead story (Full view) ---------------------------------------------------------

/**
 * Full-view lead story, Apple News–style: a large photo with the headline,
 * standfirst and source set beside it on glass.
 */
@Composable
fun LeadStoryCard(
    article: Article,
    locked: Boolean,
    category: String,
    saved: Boolean,
    onOpen: (Article) -> Unit,
    onToggleSaved: (Article) -> Unit,
    liked: Boolean = false,
    onToggleLiked: ((Article) -> Unit)? = null,
) {
    val g = Glass
    GlassCard(
        onClick = { onOpen(article) },
        modifier = Modifier.fillMaxWidth().lockedLook(locked),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(360.dp)) {
            Box(Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(InnerRadius + 4.dp))) {
                ArticleImage(
                    article,
                    Modifier.fillMaxSize().sharedArticleImage(article.id, article.imageUrl != null, InnerRadius + 4.dp),
                    logoSize = 84.dp,
                    grayscale = locked,
                )
                ScrimPill(Modifier.padding(14.dp)) { Text(category.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = Color.White) }
            }
            Column(Modifier.weight(0.4f).fillMaxHeight().padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceLogo(article.domain, article.source, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(article.publication(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Headline, standfirst and coverage take what's left; the action row always fits.
                Column(Modifier.weight(1f).clipToBounds()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        article.title,
                        style = serif(30.sp, FontWeight.Bold, lineHeight = 34.sp, tracking = (-0.01).em),
                        color = g.textPrimary, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
                    )
                    // The feed's excerpt, or (Google items carry none) the story's opening from its page.
                    val excerpt = article.summary?.takeIf { it.isNotBlank() && FeedWeaver.calculateTitleSimilarity(it, article.title) < 0.7 }
                    val loader = LocalArticlePreview.current
                    val opening by produceState(excerpt, article.id) {
                        if (value == null && loader != null) value = runCatching { loader(article) }.getOrNull()
                    }
                    androidx.compose.animation.AnimatedVisibility(opening != null, enter = androidx.compose.animation.fadeIn()) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Text(opening.orEmpty(), style = sans(14.sp, lineHeight = 21.sp).reading(), color = g.textSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    article.coverageLine(3)?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = sans(12.sp, FontWeight.Medium), color = g.accentSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        NewsProcessor.formatRelativeTime(article.publishedAt) + (article.badgeLabel()?.let { "  ·  $it" } ?: ""),
                        style = mono(11.sp, tracking = 0.sp), color = g.textSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    onToggleLiked?.let { like ->
                        SpringToggle(liked, ToggleKind.LIKE, { like(article) }, iconSize = 15.dp, background = g.chipFill, padding = 8.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    ShareButton(article, background = g.chipFill, padding = 8.dp, iconSize = 15.dp)
                    Spacer(Modifier.width(6.dp))
                    SpringToggle(saved, ToggleKind.BOOKMARK, { onToggleSaved(article) }, iconSize = 15.dp, background = g.chipFill, padding = 8.dp)
                }
            }
        }
    }
}

// ---- Bento card ------------------------------------------------------------------

@Composable
fun BentoCard(
    article: Article,
    locked: Boolean = false,
    category: String,
    wide: Boolean,
    selected: Boolean,
    saved: Boolean,
    liked: Boolean,
    onOpen: (Article) -> Unit,
    onToggleSaved: (Article) -> Unit,
    onToggleLiked: (Article) -> Unit,
) {
    val g = Glass
    GlassCard(
        onClick = { onOpen(article) },
        modifier = Modifier.fillMaxWidth().lockedLook(locked),
        contentPadding = PaddingValues(10.dp),
        selected = selected,
    ) {
        // Concentric 18dp inner thumbnail
        Box(modifier = Modifier.fillMaxWidth().height(if (wide) 150.dp else 112.dp).clip(RoundedCornerShape(InnerRadius))) {
            ArticleImage(
                article,
                Modifier.fillMaxSize().sharedArticleImage(article.id, article.imageUrl != null, InnerRadius),
                logoSize = 36.dp,
                grayscale = locked,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(category.uppercase(), style = mono(9.sp, tracking = 0.1.em), color = Color.White, maxLines = 1)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text(
                article.publication(),
                style = mono(9.sp, tracking = 0.1.em),
                color = g.textPrimary.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                article.title,
                style = serif(14.5.sp, FontWeight.SemiBold, lineHeight = 19.sp),
                color = if (selected) g.accentSoft else g.textPrimary,
                minLines = 3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(g.hairline))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                NewsProcessor.formatRelativeTime(article.publishedAt),
                style = mono(10.sp, tracking = 0.sp),
                color = g.textPrimary.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            SpringToggle(liked, ToggleKind.LIKE, { onToggleLiked(article) })
            ShareButton(article, tint = Glass.textPrimary.copy(alpha = 0.7f))
            SpringToggle(saved, ToggleKind.BOOKMARK, { onToggleSaved(article) })
        }
    }
}

/** Resolves missing lead photos from the article page (js/imageService.js). */
/** Loads a story's opening lines for cards whose feed gave no excerpt. */
val LocalArticlePreview = androidx.compose.runtime.staticCompositionLocalOf<(suspend (Article) -> String?)?> { null }

val LocalImageResolver = androidx.compose.runtime.staticCompositionLocalOf<news.openwire.data.remote.ImageResolver?> { null }

private val GrayscaleFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })

/** Article photo; when the feed had none, the page's editorial image; else the publisher logo. */
@Composable
fun ArticleImage(article: Article, modifier: Modifier, logoSize: Dp, grayscale: Boolean = false) {
    val g = Glass
    val resolver = LocalImageResolver.current
    val found by androidx.compose.runtime.produceState<String?>(null, article.imageUrl, article.originalUrl) {
        value = if (article.imageUrl == null && resolver != null) resolver.resolve(article.originalUrl) else null
    }
    val url = article.imageUrl ?: found
    // The large version of a feed thumbnail when the image service offers one; the original
    // address if the large one won't load; the publisher canvas if neither does.
    val large = remember(url) { news.openwire.domain.engine.ImageExtractor.highRes(url) }
    var useOriginal by remember(url) { mutableStateOf(false) }
    var broken by remember(url) { mutableStateOf(false) }
    if (url != null && !broken) {
        AsyncImage(
            model = if (useOriginal) url else large, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier,
            colorFilter = if (grayscale) GrayscaleFilter else null,
            onError = { if (!useOriginal && large != url) useOriginal = true else broken = true },
        )
    } else {
        PublisherCanvas(article, modifier, logoSize, grayscale)
    }
}

/** High-res publisher logos for stories without a photo. */
val LocalLogoResolver = androidx.compose.runtime.staticCompositionLocalOf<news.openwire.data.remote.PublisherLogoResolver?> { null }

/**
 * No photo: the publisher's high-resolution logo fills the canvas — a blurred, enlarged
 * copy as a brand-coloured backdrop with the crisp logo on top. Falls back to the favicon.
 */
@Composable
private fun PublisherCanvas(article: Article, modifier: Modifier, logoSize: Dp, grayscale: Boolean) {
    val g = Glass
    val logos = LocalLogoResolver.current
    // The publisher's own high-res icon, else the largest favicon (256px) — both fill the canvas.
    val logo by androidx.compose.runtime.produceState<String?>(null, article.domain) {
        value = logos?.logo(article.domain) ?: faviconUrl(article.domain, 256)
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.background(Brush.linearGradient(listOf(g.indigo.copy(alpha = 0.35f), g.accent.copy(alpha = 0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        // If the publisher's icon won't load (some sites refuse image requests), use the favicon.
        val fallback = faviconUrl(article.domain, 256)
        var src by remember(logo) { mutableStateOf(logo) }
        val l = src
        if (l == null) {
            SourceLogo(article.domain, article.source, size = logoSize)
        } else {
            val filter = if (grayscale) GrayscaleFilter else null
            val onError: (coil.compose.AsyncImagePainter.State.Error) -> Unit = { if (src != fallback) src = fallback else src = null }
            AsyncImage(
                model = l, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = filter, onError = onError,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f; alpha = 0.75f }.then(androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded.let { Modifier.blur(42.dp, it) }),
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.35f)))))
            val side = (minOf(maxWidth, maxHeight) * 0.46f).coerceIn(48.dp, 200.dp)
            AsyncImage(
                model = l, contentDescription = article.source, contentScale = ContentScale.Fit, colorFilter = filter, onError = onError,
                modifier = Modifier.size(side).clip(RoundedCornerShape(side * 0.22f)),
            )
        }
    }
}

/** Hard paywall with no archive snapshot: greyed out and marked locked (SPEC §6.2). */
fun Modifier.lockedLook(locked: Boolean): Modifier = if (locked) this.graphicsLayer { alpha = 0.55f } else this

/** Applies an archive check result: archived stories open the snapshot; missing ones are locked. */
fun Article.withArchive(state: ArchiveState?): Article = when (state) {
    is ArchiveState.Available -> copy(targetUrl = state.url, archiveUrl = state.url, badge = "Archive")
    ArchiveState.Locked -> copy(status = "locked", badge = "Locked")
    else -> this
}

// ---- States ------------------------------------------------------------------------

@Composable
fun MessageCard(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val g = Glass
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(32.dp)) {
        Text(title, style = serif(18.sp, FontWeight.Medium), color = g.textPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = sans(12.sp, lineHeight = 18.sp),
            color = g.textPrimary.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(g.accent)
                    .clickable(onClick = onAction).padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(action, style = sans(12.sp, FontWeight.SemiBold), color = Color.White) }
        }
    }
}

/** Shimmer-free skeleton (pulsing glass) shown while a topic loads. */
fun LazyGridScope.skeletonItems() {
    fullRow("sk-ticker") { SkeletonBlock(44.dp) }
    fullRow("sk-hero") { SkeletonBlock(310.dp) }
    items(4, key = { "sk-$it" }) { Box(feedItemMotion()) { SkeletonBlock(230.dp) } }
}

@Composable
private fun SkeletonBlock(height: Dp) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.fillMaxWidth().height(height)
            .graphicsLayer { alpha = a }
            .glassSurface(elevation = 0.dp),
    )
}

/** "3 new since 9:12": rose mono, with a hairline fading away in the reading direction. */
@Composable
private fun NewDivider(count: Int, since: Long) {
    val time = remember(since) {
        if (since <= 0L) "" else java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            .format(java.time.Instant.ofEpochMilli(since).atZone(java.time.ZoneId.systemDefault()))
    }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (time.isEmpty()) tr(R.string.refresh_new, count) else tr(R.string.refresh_new_since, count, time)).uppercase(),
            style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accent, maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(1.dp).drawWithCache {
                val b = Brush.horizontalGradient(
                    if (rtl) listOf(Color.Transparent, Glass_accentFade) else listOf(Glass_accentFade, Color.Transparent),
                )
                onDrawBehind { drawRect(b) }
            },
        )
    }
}

private val Glass_accentFade = news.openwire.ui.theme.Rose.copy(alpha = .55f)

/**
 * New stories as a compact 2 × 2 grid of headlines (text only): the first four, with a cue to
 * show the rest. Each fades in and slides down 8 dp, 70 ms after the one before it.
 */
@Composable
private fun NewSinceGrid(stories: List<Article>, onOpen: (Article) -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val shown = if (open) stories else stories.take(4)
    Column(Modifier.fillMaxWidth().animateContentSize(tween(320, easing = FastOutSlowInEasing)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { c, a -> NewStoryCell(a, r * 2 + c, onOpen, Modifier.weight(1f).fillMaxHeight().landing(a.id)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (stories.size > 4) {
            Text(
                if (open) tr(R.string.refresh_show_less) else tr(R.string.refresh_show_more, stories.size - 4),
                style = mono(11.sp, FontWeight.SemiBold, 0.08.em), color = Glass.accent,
                modifier = Modifier.clip(CircleShape).clickable { open = !open }.padding(horizontal = 6.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun NewStoryCell(a: Article, index: Int, onOpen: (Article) -> Unit, modifier: Modifier) {
    val g = Glass
    val reduce = news.openwire.ui.theme.rememberReduceMotion()
    val k = remember(a.id) { androidx.compose.animation.core.Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(a.id) { if (!reduce) { kotlinx.coroutines.delay(70L * (index % 4)); k.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) } }
    Column(
        modifier.graphicsLayer { alpha = k.value; translationY = -8.dp.toPx() * (1f - k.value) }
            .clip(RoundedCornerShape(14.dp)).background(g.chipFill).clickable { onOpen(a) }.padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(g.accent).semantics { contentDescription = tr(R.string.refresh_new_dot) })
            Spacer(Modifier.width(6.dp))
            Text(a.publication(), style = mono(9.5.sp, FontWeight.SemiBold, 0.1.em), color = g.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(3.dp))
        Text(a.title, style = serif(14.sp, FontWeight.SemiBold, lineHeight = 18.sp), color = g.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** How many top stories the lead slideshow shows. */
private const val LEAD_SLIDES = 4
private const val SLIDE_MS = 5_000L
private const val HOLD_AFTER_TOUCH_MS = 10_000L

/**
 * The lead card as a slideshow of the top stories: it advances every 5 s; any touch (swiping
 * through, pressing) holds it for 10 s from the last touch, then it carries on. Dots show the
 * position; with animations off it doesn't advance on its own.
 */
@Composable
private fun LeadSlideshow(stories: List<Article>, dotsAtStart: Boolean = false, card: @Composable (Article) -> Unit) {
    if (stories.size <= 1) { stories.firstOrNull()?.let { card(it) }; return }
    val reduce = news.openwire.ui.theme.rememberReduceMotion()
    val pager = androidx.compose.foundation.pager.rememberPagerState { stories.size }
    var lastTouch by remember { mutableStateOf(0L) }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) lastTouch = System.currentTimeMillis() }
    LaunchedEffect(stories.map { it.id }) {
        if (reduce) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(SLIDE_MS)
            val held = System.currentTimeMillis() - lastTouch
            if (held < HOLD_AFTER_TOUCH_MS) { kotlinx.coroutines.delay(HOLD_AFTER_TOUCH_MS - held); continue }
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % stories.size, animationSpec = tween(650, easing = EmphasizedDecelerate))
        }
    }
    Box(
        Modifier.pointerInput(Unit) {
            // Any touch on the card counts (a press, a swipe), without taking it from the card.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    lastTouch = System.currentTimeMillis()
                }
            }
        },
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager, pageSpacing = 12.dp, beyondViewportPageCount = 1,
            key = { stories[it].id },
        ) { page -> card(stories[page]) }
        // Position dots inside the card, bottom corner (clear of the headline and the top pills).
        Row(
            Modifier.align(if (dotsAtStart) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, bottom = 14.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .35f)).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(stories.size) { i ->
                val on = pager.currentPage == i
                val w by androidx.compose.animation.core.animateDpAsState(if (on) 14.dp else 5.dp, tween(300), label = "dot")
                Box(
                    Modifier.padding(horizontal = 2.dp).height(5.dp).width(w).clip(CircleShape)
                        .background(if (on) Color.White else Color.White.copy(alpha = .45f)),
                )
            }
        }
    }
}
