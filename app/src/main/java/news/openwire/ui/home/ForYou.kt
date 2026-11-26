// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import news.openwire.R
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.i18n.tr
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Fuchsia400
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Indigo
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.Rose

/** Video moments' backdrop: magenta, indigo and purple, a step brighter than the page. */
val VideoGlow = listOf(Fuchsia400.copy(alpha = .26f), Indigo.copy(alpha = .30f), Indigo400.copy(alpha = .16f))
/** For You's backdrop: the warm side of the same palette (rose, magenta, amber). */
val ForYouGlow = listOf(Rose.copy(alpha = .26f), Fuchsia400.copy(alpha = .18f), Amber400.copy(alpha = .14f))

/** A highlighted band of the page: a soft gradient panel with a hairline edge, full width. */
@Composable
fun HighlightSection(colors: List<Color>, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f))), shape)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) { content() }
}

/**
 * For You: stories picked from what the reader loved, four to a page (two by two), swiped
 * sideways through up to five pages, dots underneath.
 */
@Composable
fun ForYouSection(
    stories: List<Article>,
    compact: Boolean,
    savedIds: Set<String>,
    likedIds: Set<String>,
    onOpen: (Article) -> Unit,
    onToggleSaved: (Article) -> Unit,
    onToggleLiked: (Article) -> Unit,
) {
    val pages = stories.take(20).chunked(4).filter { it.size == 4 }
    if (pages.isEmpty()) return
    val g = Glass
    HighlightSection(ForYouGlow) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.for_you), style = serif(24.sp, FontWeight.Bold), color = g.textPrimary)
                    Text(tr(R.string.for_you_sub), style = sans(12.sp), color = g.textSecondary)
                }
                Text(tr(R.string.for_you_tag), style = mono(9.sp, tracking = 0.08.em), color = g.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            val pager = rememberPagerState { pages.size }
            val cardH: Dp = if (compact) 262.dp else 290.dp
            HorizontalPager(state = pager, pageSpacing = 14.dp, key = { pages[it].first().id }) { p ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pages[p].chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { a ->
                                ForYouCard(a, a.id in savedIds, a.id in likedIds, onOpen, onToggleSaved, onToggleLiked, Modifier.weight(1f).height(cardH))
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val on = pager.currentPage == i
                    val w by animateDpAsState(if (on) 16.dp else 6.dp, tween(300), label = "dot")
                    Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(CircleShape).background(if (on) g.accent else g.textSecondary.copy(alpha = .4f)))
                }
            }
        }
    }
}

@Composable
private fun ForYouCard(
    a: Article, saved: Boolean, liked: Boolean,
    onOpen: (Article) -> Unit, onToggleSaved: (Article) -> Unit, onToggleLiked: (Article) -> Unit,
    modifier: Modifier,
) {
    val g = Glass
    GlassCard(onClick = { onOpen(a) }, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        Column(Modifier.fillMaxSize()) {
            ArticleImage(a, Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(16.dp)), logoSize = 36.dp)
            Spacer(Modifier.height(8.dp))
            Text(a.publication(), style = mono(9.5.sp, FontWeight.SemiBold, 0.12.em), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(3.dp))
            Text(a.title, style = serif(15.sp, FontWeight.SemiBold, lineHeight = 19.sp), color = g.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(NewsProcessor.formatRelativeTime(a.publishedAt), style = mono(10.sp, tracking = 0.sp), color = g.textSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                SpringToggle(liked, ToggleKind.LIKE, { onToggleLiked(a) }, iconSize = 15.dp, padding = 5.dp)
                ShareButton(a, padding = 5.dp, iconSize = 15.dp)
                SpringToggle(saved, ToggleKind.BOOKMARK, { onToggleSaved(a) }, iconSize = 15.dp, padding = 5.dp)
            }
        }
    }
}
