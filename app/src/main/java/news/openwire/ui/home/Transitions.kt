// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import news.openwire.ui.theme.Glass

// ---- Motion -------------------------------------------------------------------------

/** Lazy-grid item motion: new stories fade in on the spring curve, others glide into place. */
fun LazyGridItemScope.feedItemMotion(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(420, easing = SpringEasing),
    placementSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    ),
    fadeOutSpec = tween(160),
)

/**
 * Card → reader "container transform": the story's photo flies from its card into
 * the reader while the sheet springs up (cover screen only).
 */
class ImageTransition(
    val shared: SharedTransitionScope,
    /** The reader sheet's visibility scope; null for feed-side (source) elements. */
    val sheetScope: AnimatedVisibilityScope?,
    /** The story whose photo currently lives in the reader, if any. */
    val openId: String?,
)

val LocalImageTransition = compositionLocalOf<ImageTransition?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
private val ImageBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = Rect.VisibilityThreshold)
}

/** Tags an article photo as a shared element. Feed cards hide theirs while it is shown in the reader. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArticleImage(articleId: String, hasImage: Boolean, cornerRadius: Dp): Modifier {
    val t = LocalImageTransition.current ?: return this
    if (!hasImage) return this
    with(t.shared) {
        val state = rememberSharedContentState("article-image-$articleId")
        val clip = OverlayClip(RoundedCornerShape(cornerRadius))
        val sheet = t.sheetScope
        return if (sheet != null) {
            this@sharedArticleImage.sharedElement(state, sheet, boundsTransform = ImageBounds, clipInOverlayDuringTransition = clip)
        } else {
            this@sharedArticleImage.sharedElementWithCallerManagedVisibility(
                state,
                visible = t.openId != articleId,
                boundsTransform = ImageBounds,
                clipInOverlayDuringTransition = clip,
            )
        }
    }
}

// ---- Backdrop blur (Haze) -----------------------------------------------------------

/** Blur source for floating chrome (bottom pill, reader top bar). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** True backdrop blur — `backdrop-filter: blur(28px) saturate(180%)` from the prototype. */
@Composable
fun Modifier.frostedGlass(state: HazeState?, blur: Dp = 28.dp): Modifier {
    if (state == null) return this
    val g = Glass
    return this.hazeEffect(
        state,
        HazeStyle(
            backgroundColor = g.deviceBg,
            tint = HazeTint(g.glassPill),
            blurRadius = blur,
            noiseFactor = 0.04f,
        ),
    )
}

/**
 * Top of the screen: the mirror of [BottomFadeGlass] — a blur that is strongest at
 * the very top and fades out below the status bar, with no hard edge or rule.
 * [extra] is how far below the status bar the fade reaches.
 */
@Composable
fun StatusBarGlass(state: HazeState?, modifier: Modifier = Modifier, extra: Dp = 28.dp) {
    val g = Glass
    val top = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(top + extra)
            .then(
                if (state != null) Modifier.hazeEffect(
                    state,
                    HazeStyle(backgroundColor = g.deviceBg, tint = HazeTint(g.deviceBg.copy(alpha = 0.35f)), blurRadius = 22.dp, noiseFactor = 0.03f),
                ) { progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f) }
                else Modifier,
            )
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to g.deviceBg.copy(alpha = 0.7f),
                    1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            ),
    )
}

/**
 * Bottom of the screen: a blur that fades in towards the edge (progressive haze
 * plus a tint gradient), so content dissolves under the navigation pill.
 */
@Composable
fun BottomFadeGlass(state: HazeState?, height: Dp, modifier: Modifier = Modifier) {
    val g = Glass
    val bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(bottom + height)
            .then(
                if (state != null) Modifier.hazeEffect(
                    state,
                    HazeStyle(backgroundColor = g.deviceBg, tint = HazeTint(g.deviceBg.copy(alpha = 0.35f)), blurRadius = 22.dp, noiseFactor = 0.03f),
                ) { progressive = dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f) }
                else Modifier,
            )
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to g.deviceBg.copy(alpha = 0.7f),
                ),
            ),
    )
}
