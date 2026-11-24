// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import news.openwire.domain.model.Article
import news.openwire.ui.theme.Glass

/**
 * Full view (unfolded): stories open as a dedicated page that pushes in from the
 * right while the photo flies from its card into a wide header — Apple News–style.
 * Predictive back peeks the feed behind it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.FullReaderHost(
    visible: Boolean,
    article: Article?,
    category: String,
    backLabel: String,
    savedIds: Set<String>,
    likedIds: Set<String>,
    actions: ReaderActions,
    onClose: () -> Unit,
) {
    val back = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) back.snapTo(0f) }
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { back.snapTo(it.progress) }
            onClose()
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, springTween(300)) }
            throw e
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(springTween(520)) { it / 5 } + fadeIn(springTween(360)) + scaleIn(springTween(520), initialScale = 0.985f),
        exit = slideOutHorizontally(springTween(420)) { it / 5 } + fadeOut(tween(260)) + scaleOut(springTween(420), targetScale = 0.985f),
    ) {
        var shown by remember { mutableStateOf(article) }
        LaunchedEffect(article) { if (article != null) shown = article }
        shown?.let { a ->
            CompositionLocalProvider(LocalImageTransition provides ImageTransition(this@FullReaderHost, this, a.id)) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val p = back.value
                        val s = 1f - 0.06f * p
                        scaleX = s; scaleY = s
                        translationX = 48.dp.toPx() * p
                        shape = RoundedCornerShape((32 * p).dp)
                        clip = p > 0f
                    },
                ) {
                    FullReaderPage(a, category, backLabel, a.id in savedIds, a.id in likedIds, actions, onClose)
                }
            }
        }
    }
}

@Composable
private fun FullReaderPage(
    article: Article,
    category: String,
    backLabel: String,
    saved: Boolean,
    liked: Boolean,
    actions: ReaderActions,
    onClose: () -> Unit,
) {
    val g = Glass
    val haze = rememberHazeState()
    val barHeight = 64.dp
    Box(Modifier.fillMaxSize().background(g.deviceBg)) {
        Column(
            Modifier.fillMaxSize().hazeSource(haze).background(g.deviceBg)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(top = barHeight + 8.dp, bottom = 72.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wide header photo (the card's photo flies into it), at the sharpest size available.
            var content by remember(article.id) { mutableStateOf<news.openwire.data.remote.ArticleContent?>(null) }
            var loading by remember(article.id) { mutableStateOf(true) }
            HeroPhoto(
                article, content, loading, aspect = 2.1f, radius = 28.dp, captionGap = 20.dp,
                modifier = Modifier.widthIn(max = 1040.dp).padding(horizontal = 32.dp),
            )
            // Comfortable reading measure.
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 32.dp)) {
                ArticleBody(
                    article, category, showMetaBar = true, saved = saved, liked = liked, actions = actions, showFeatureImage = false,
                    onLoading = { loading = it }, onContent = { content = it },
                )
            }
        }
        StatusBarGlass(haze, extra = barHeight + 22.dp)
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(barHeight).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape)
                    .clickable(onClick = onClose).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = g.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(backLabel, style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary)
            }
            // Like/save live in the article's meta bar just below.
        }
    }
}
