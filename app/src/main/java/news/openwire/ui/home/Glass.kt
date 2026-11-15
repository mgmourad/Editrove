package news.openwire.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Concentric geometry: outer cards 28dp, inner thumbnails/pills 18dp. */
val BentoRadius: Dp = 28.dp
val InnerRadius: Dp = 18.dp

/** Frosted glass surface: translucent fill, 1px specular border, soft shadow. */
@Composable
fun Modifier.glassSurface(radius: Dp = BentoRadius): Modifier = this
    .shadow(20.dp, RoundedCornerShape(radius), spotColor = Color.Black.copy(alpha = 0.35f))
    .clip(RoundedCornerShape(radius))
    .background(MaterialTheme.colorScheme.surfaceVariant)
    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(radius))

/** Spring press scale (≈ cubic-bezier(0.22, 1, 0.36, 1)). */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A frosted glass card with spring press feedback. */
@Composable
fun GlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BentoRadius)
    val surface = modifier.glassSurface(BentoRadius)
    if (onClick == null) {
        Box(modifier = surface) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = surface
                .clip(shape)
                .pressScale(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}
