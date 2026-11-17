// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import news.openwire.i18n.tr
import news.openwire.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Glass

/** Concentric geometry: outer cards 28dp, inner thumbnails/pills 18dp (28 − 10 padding). */
val BentoRadius: Dp = 28.dp
val InnerRadius: Dp = 18.dp

/** The prototype's signature spring curve, cubic-bezier(0.22, 1, 0.36, 1). */
val SpringEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Liquid Glass surface: translucent fill, a brighter specular rim on the top/left
 * edges fading to a faint inner border on the bottom/right, and a soft drop shadow
 * (`.liquid-card` / `.liquid-pill` in the prototype).
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(BentoRadius),
    fill: Color = Glass.glassSurface,
    elevation: Dp = 18.dp,
): Modifier {
    val g = Glass
    val rim = Brush.linearGradient(
        0f to g.specularHighlight,
        0.35f to g.specularBorder,
        0.65f to g.innerBorder,
        1f to g.innerBorder,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return this
        .shadow(elevation, shape, ambientColor = g.shadow, spotColor = g.shadow)
        .clip(shape)
        .background(fill)
        .border(1.dp, rim, shape)
}

/** Spring press scale — `.liquid-card:active { transform: scale(0.985) }`. */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, pressed: Float = 0.985f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A Liquid Glass card with spring press feedback. */
@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BentoRadius)
    val g = Glass
    val interactionSource = remember { MutableInteractionSource() }
    var m = modifier
    if (onClick != null) m = m.pressScale(interactionSource)
    m = m.glassSurface(shape, fill = if (selected) g.accent.copy(alpha = 0.10f).compositeOver(g.glassSurface) else g.glassSurface)
    if (selected) m = m.border(2.dp, g.accent, shape)
    if (onClick != null) m = m.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    Box(modifier = m) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1 - alpha)
    if (a == 0f) return Color.Transparent
    fun ch(f: Color.() -> Float) = (f(this) * alpha + f(background) * background.alpha * (1 - alpha)) / a
    return Color(ch { red }, ch { green }, ch { blue }, a)
}

/** Small frosted pill over imagery — `bg-black/45 backdrop-blur-xl border-white/20`. */
@Composable
fun ScrimPill(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 4.dp),
        content = content,
    )
}

/**
 * The ambient studio mesh behind the whole app: soft indigo / rose / emerald
 * radial glows on the obsidian (or paper) canvas.
 */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val g = Glass
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(g.deviceBg)
            .drawBehind {
                fun glow(color: Color, cx: Float, cy: Float, r: Float) {
                    if (color.alpha == 0f) return
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color, color.copy(alpha = 0f)),
                            center = Offset(size.width * cx, size.height * cy),
                            radius = r,
                        ),
                        radius = r,
                        center = Offset(size.width * cx, size.height * cy),
                    )
                }
                val r = maxOf(size.width, size.height) * 0.55f
                glow(g.meshIndigo, 0.12f, 0.10f, r)
                glow(g.meshRose, 0.90f, 0.32f, r)
                glow(g.meshEmerald, 0.50f, 0.95f, r)
            },
    )
}

/**
 * Like / bookmark toggle with the prototype's `springPop` burst
 * (1 → 1.38 → 0.92 → 1 over 450ms) and a haptic tick.
 */
@Composable
fun SpringToggle(
    active: Boolean,
    kind: ToggleKind,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    inactiveTint: Color = Glass.textPrimary.copy(alpha = 0.7f),
    background: Color = Color.Transparent,
    border: Color = Color.Transparent,
    padding: Dp = 6.dp,
) {
    val haptics = LocalHapticFeedback.current
    var pops by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pops) {
        if (pops == 0) return@LaunchedEffect
        scale.animateTo(
            1f,
            keyframes {
                durationMillis = 450
                1.38f at 180 using SpringEasing
                0.92f at 315 using SpringEasing
                1f at 450
            },
        )
    }
    val (icon, tint, label) = when (kind) {
        ToggleKind.LIKE -> Triple(
            if (active) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (active) Glass.accent else inactiveTint,
            tr(if (active) R.string.cd_unlike else R.string.cd_like),
        )
        ToggleKind.BOOKMARK -> Triple(
            if (active) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            if (active) Amber400 else inactiveTint,
            tr(if (active) R.string.cd_unsave else R.string.cd_save),
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                pops++
                onToggle()
            }
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

enum class ToggleKind { LIKE, BOOKMARK }

/** Tween used for posture / pane transitions. */
fun <T> springTween(durationMs: Int = 450) = tween<T>(durationMs, easing = SpringEasing)

/** "Headline" then the publisher's own link (never a Google or archive wrapper). */
fun shareText(a: news.openwire.domain.model.Article): String {
    val link = listOf(a.originalUrl, a.targetUrl).firstOrNull { u ->
        !u.isNullOrBlank() && !u.contains("news.google.com") && !u.contains("archive.") && !u.contains("web.archive.org")
    } ?: a.originalUrl.orEmpty()
    return a.title.trim() + "\n" + link
}

/** Share button for cards and the reader: headline first, then the article link. */
@androidx.compose.runtime.Composable
fun ShareButton(
    article: news.openwire.domain.model.Article,
    tint: androidx.compose.ui.graphics.Color = Glass.textPrimary,
    background: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    border: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    padding: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier.clip(CircleShape).background(background).border(1.dp, border, CircleShape)
            .clickable {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, shareText(article))
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, article.title)
                context.startActivity(android.content.Intent.createChooser(send, tr(R.string.share_story)))
            }
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Share, contentDescription = tr(R.string.share_story),
            tint = tint, modifier = Modifier.size(18.dp),
        )
    }
}
