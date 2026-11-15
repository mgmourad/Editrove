// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

fun serif(size: TextUnit, weight: FontWeight = FontWeight.Bold, lineHeight: TextUnit = TextUnit.Unspecified, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = NewsreaderFamily, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = tracking)

fun Article.publication(): String = (source ?: domain ?: "").uppercase()

/** Paywall badge from the routing engine, shown where the mock shows read time. */
fun Article.badgeLabel(): String? = badge?.takeIf { it.isNotBlank() } ?: status?.replaceFirstChar { it.uppercase() }

// ---- Masthead + category pills ----------------------------------------------

/** "SUNDAY • NOV 15" / OpenWire masthead with the edition pill and settings on the right. */
@Composable
fun Masthead(edition: String, onEdition: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val g = Glass
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE • MMM d", Locale.ENGLISH)).uppercase()
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.padding(end = 8.dp)) {
            Text(date, style = mono(11.sp, FontWeight.SemiBold, 0.18.em), color = g.accent)
            Text(
                "OpenWire",
                style = serif(30.sp, FontWeight.Bold, lineHeight = 30.sp, tracking = (-0.02).em),
                color = g.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .widthIn(max = 150.dp)
                .clip(CircleShape)
                .background(g.chipFill)
                .border(1.dp, g.hairline, CircleShape)
                .clickable(onClick = onEdition)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(edition, style = sans(11.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Settings, contentDescription = "Settings",
                tint = g.textPrimary, modifier = Modifier.size(16.dp),
            )
        }
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
                ) { Text(m.label, style = sans(11.sp, if (active) FontWeight.SemiBold else FontWeight.Medium), color = if (active) g.textPrimary else g.textSecondary) }
            }
        }
        Spacer(Modifier.weight(1f))
        val label = when {
            refreshing -> "Refreshing…"
            updatedAt > 0 -> "Updated " + NewsProcessor.formatRelativeTime(updatedAt)
            else -> "Saved copy"
        } + if (failed.isNotEmpty() && !refreshing) " · ${failed.size} source${if (failed.size > 1) "s" else ""} offline" else ""
        Row(
            Modifier.clip(CircleShape).clickable(enabled = !refreshing, onClick = onRefresh).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = mono(10.sp, tracking = 0.sp), color = g.textSecondary, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            val spin = rememberInfiniteTransition(label = "spin")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Refresh, contentDescription = "Refresh",
                tint = g.accentSoft, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (refreshing) angle else 0f },
            )
        }
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

/** The single lazy grid every tab renders into (two columns, 12dp gutters). */
@Composable
fun EditorialGrid(
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A full-width grid row that animates in/out with the feed. */
fun LazyGridScope.fullRow(key: Any, content: @Composable () -> Unit) =
    item(span = { GridItemSpan(2) }, key = key) { Box(feedItemMotion()) { content() } }

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
    sectionTitle: String = "Bento Dispatch",
    archive: Map<String, ArchiveState> = emptyMap(),
) {
    if (articles.isEmpty()) return
    fun state(a: Article) = a.originalUrl?.let { archive[it] }
    if (showTicker) fullRow("ticker") { LiveTicker(articles, onOpen) }
    val hero = articles.first()
    fullRow("hero-${hero.id}") {
        HeroCard(
            article = hero.withArchive(state(hero)),
            locked = state(hero) == ArchiveState.Locked,
            category = category,
            selected = !compact && selectedId == hero.id,
            saved = hero.id in savedIds,
            onOpen = onOpen,
            onToggleSaved = onToggleSaved,
        )
    }
    val rest = articles.drop(1)
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
                Text("${rest.size} Curated Reads", style = sans(11.sp, FontWeight.Medium), color = Glass.accentSoft)
            }
        }
    }
    itemsIndexed(
        rest,
        key = { _, a -> a.id },
        // Every 7th bento item (starting with the 3rd) spans both columns on the cover screen.
        span = { index, _ -> if (compact && index % 7 == 2) GridItemSpan(2) else GridItemSpan(1) },
    ) { index, article ->
        Box(feedItemMotion()) {
            BentoCard(
                article = article.withArchive(state(article)),
                locked = state(article) == ArchiveState.Locked,
                category = category,
                wide = compact && index % 7 == 2,
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

// ---- Live ticker -----------------------------------------------------------------

private val TickerColors = listOf(Indigo400, Emerald400, Amber400, Fuchsia400, Rose400)

/** Swipeable live strip of real headlines (slow auto-scroll; tap to open). */
@Composable
fun LiveTicker(articles: List<Article>, onOpen: (Article) -> Unit) {
    val g = Glass
    val items = remember(articles) { articles.drop(1).take(10) }
    val listState = rememberLazyListState()
    LaunchedEffect(items) {
        while (isActive) {
            delay(32)
            try {
                if (listState.canScrollForward) listState.scrollBy(1.2f) else { delay(1500); listState.scrollToItem(0) }
            } catch (e: CancellationException) {
                if (!isActive) throw e // the user grabbed the strip; resume after they let go
                delay(2500)
            }
        }
    }
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivePill()
            Spacer(Modifier.width(10.dp))
            LazyRow(state = listState, modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(items, key = { _, a -> a.id }) { index, article ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            Text("•", style = sans(12.sp), color = g.textPrimary.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = TickerColors[index % TickerColors.size])) {
                                    append(shortSource(article))
                                }
                                append("  ")
                                append(article.title)
                            },
                            style = sans(12.sp),
                            color = g.textPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                            modifier = Modifier.clickable { onOpen(article) },
                        )
                    }
                }
            }
        }
    }
}

private fun shortSource(a: Article): String {
    val s = a.source ?: a.domain?.substringBefore('.') ?: "WIRE"
    return s.split(' ').let { if (it.size > 2) it.joinToString("") { w -> w.take(1) } else s }.uppercase()
}

@Composable
private fun LivePill() {
    val g = Glass
    val t = rememberInfiniteTransition(label = "ping")
    val ping by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "p")
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
                    .graphicsLayer { scaleX = 1f + ping; scaleY = 1f + ping; alpha = 1f - ping }
                    .clip(CircleShape).background(g.accent),
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(g.accent))
        }
        Spacer(Modifier.width(6.dp))
        Text("LIVE", style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = g.accentSoft)
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
                ScrimPill { Text(category.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.1.em), color = Color.White) }
                Spacer(Modifier.weight(1f))
                article.badgeLabel()?.let { badge ->
                    ScrimPill { Text(badge, style = mono(10.sp), color = Color.White.copy(alpha = 0.9f)) }
                    Spacer(Modifier.width(6.dp))
                }
                SpringToggle(
                    active = saved,
                    kind = ToggleKind.BOOKMARK,
                    onToggle = { onToggleSaved(article) },
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
                )
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
            SpringToggle(saved, ToggleKind.BOOKMARK, { onToggleSaved(article) })
        }
    }
}

/** Resolves missing lead photos from the article page (js/imageService.js). */
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
    if (url != null) {
        AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier,
            colorFilter = if (grayscale) GrayscaleFilter else null,
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(g.indigo.copy(alpha = 0.35f), g.accent.copy(alpha = 0.25f))),
            ),
            contentAlignment = Alignment.Center,
        ) {
            SourceLogo(article.domain, article.source, size = logoSize)
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
