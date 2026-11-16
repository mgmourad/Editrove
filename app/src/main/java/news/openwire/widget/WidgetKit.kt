// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import news.openwire.R

/**
 * Editrove widget design tokens: Black Glass (night) and Editorial Paper (day), switched
 * by the system theme. Corner geometry is concentric: a 28dp shell, 18dp cards, 14dp photos.
 */
object W {
    private fun pair(day: Long, night: Long): ColorProvider = ColorProvider(day = Color(day), night = Color(night))

    val text = pair(0xFF0F172A, 0xFFF8FAFC)
    val textSecondary = pair(0xFF475569, 0xFF94A3B8)
    val textMuted = pair(0xFF64748B, 0xFF64748B)
    // The app's own language: Rose is the accent, Indigo the secondary (see ui/theme).
    val indigo = pair(0xFF4F46E5, 0xFF818CF8)
    val accent = pair(0xFFE11D48, 0xFFFB7185)
    val emerald = pair(0xFF059669, 0xFF34D399)
    val chip = pair(0x0D0F172A, 0x1AFFFFFF)
    val divider = pair(0x140F172A, 0x1AFFFFFF)
    val accentChip = pair(0x1FE11D48, 0x33FB3768)
    val indigoChip = pair(0x1F4F46E5, 0x336366F1)
    val white: ColorProvider = ColorProvider(Color.White)
    val onPhoto: ColorProvider = ColorProvider(Color(0xFFFB7185))
    val onPlay: ColorProvider = ColorProvider(Color.White)
    val photoChip: ColorProvider = ColorProvider(Color(0x80000000))

    val SHELL = 28.dp
    val CARD = 18.dp
    val PHOTO = 14.dp
}

/**
 * Size classes (container-query style): the layout changes shape, not just scale.
 * Compact 2×2 < 300dp wide · Wide 4×2 < 270dp tall · Expanded 4×4 · Fold 6×3 ≥ 520dp wide.
 */
enum class SizeClass {
    COMPACT, WIDE, EXPANDED, FOLD;

    companion object {
        fun of(size: DpSize): SizeClass = when {
            size.width < 300.dp -> COMPACT
            size.height < 270.dp -> WIDE
            size.width >= 520.dp -> FOLD
            else -> EXPANDED
        }
    }
}

/** A topic accent (switches the Topic widget's colour with the topic). */
data class Accent(val fg: ColorProvider, val chip: ColorProvider)

object Accents {
    private fun a(day: Long, night: Long) = Accent(
        ColorProvider(day = Color(day), night = Color(night)),
        ColorProvider(day = Color(day).copy(alpha = 0.14f), night = Color(night).copy(alpha = 0.2f)),
    )
    // The app's ticker palette.
    val INDIGO = a(0xFF4F46E5, 0xFF818CF8)
    val EMERALD = a(0xFF059669, 0xFF34D399)
    val AMBER = a(0xFFD97706, 0xFFFBBF24)
    val FUCHSIA = a(0xFFC026D3, 0xFFE879F9)
    val ROSE = a(0xFFE11D48, 0xFFFB7185)
    private val cycle = listOf(INDIGO, EMERALD, AMBER, FUCHSIA, ROSE)

    fun forTopic(id: String, index: Int): Accent = when (id) {
        "AI", "TECHNOLOGY" -> INDIGO
        "BUSINESS", "FINANCE" -> EMERALD
        "SCIENCE" -> FUCHSIA
        "SPORTS", "LIFESTYLE" -> AMBER
        "ENTERTAINMENT" -> ROSE
        "POLITICS", "INTERNATIONAL" -> ROSE
        else -> cycle[index % cycle.size]
    }
}

// ---- Typography: editorial serif headlines, mono metadata, sans controls ---------------

fun serif(size: TextUnit, color: ColorProvider = W.text, bold: Boolean = true) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontFamily = FontFamily.Serif)

fun mono(size: TextUnit = 10.sp, color: ColorProvider = W.textSecondary, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, fontFamily = FontFamily.Monospace)

fun sans(size: TextUnit = 12.sp, color: ColorProvider = W.textSecondary, bold: Boolean = false) =
    TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, fontFamily = FontFamily.SansSerif)

// ---- Shared pieces ----------------------------------------------------------------------

/** The ET monogram on its obsidian tile. */
@Composable
fun Monogram(size: Dp = 24.dp, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier.size(size).background(ImageProvider(R.drawable.widget_et_badge)).cornerRadius(size / 3), contentAlignment = Alignment.Center) {
        Image(ImageProvider(R.drawable.ic_brand), contentDescription = "Editrove", contentScale = ContentScale.Crop, modifier = GlanceModifier.size(size - 2.dp).cornerRadius(size / 3))
    }
}

/** Rounded mono label (BRIEF, TOP, LEAD, speed…). */
@Composable
fun Pill(label: String, fg: ColorProvider, bg: ColorProvider, onClick: Action? = null, size: TextUnit = 9.sp, modifier: GlanceModifier = GlanceModifier) {
    var m = modifier.background(bg).cornerRadius(99.dp).padding(horizontal = 8.dp, vertical = 3.dp)
    if (onClick != null) m = m.clickable(onClick)
    Box(m) { Text(label, style = mono(size, fg, bold = true), maxLines = 1) }
}

/** Gradient transport button; a soft amber ring sits around it while playing. */
@Composable
fun PlayButton(playing: Boolean, onClick: Action, diameter: Dp = 44.dp) {
    Box(
        GlanceModifier.size(diameter + 8.dp).let { if (playing) it.background(ImageProvider(R.drawable.widget_play_ring)) else it }.clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(GlanceModifier.size(diameter).background(ImageProvider(R.drawable.widget_play_bg)).cornerRadius(diameter / 2), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(if (playing) R.drawable.wic_pause else R.drawable.wic_play),
                contentDescription = if (playing) "Pause briefing" else "Play briefing",
                colorFilter = ColorFilter.tint(W.onPlay),
                modifier = GlanceModifier.size(diameter * 0.45f),
            )
        }
    }
}

/** Small round control (-15 / +15). */
@Composable
fun RoundControl(label: String, onClick: Action, diameter: Dp = 32.dp) {
    Box(GlanceModifier.size(diameter).background(W.chip).cornerRadius(diameter / 2).clickable(onClick), contentAlignment = Alignment.Center) {
        Text(label, style = mono(10.sp, W.text, bold = true))
    }
}

@Composable
fun BookmarkButton(saved: Boolean, onClick: Action, tint: ColorProvider = W.text, bg: ColorProvider = W.chip, diameter: Dp = 28.dp) {
    Box(GlanceModifier.size(diameter).background(bg).cornerRadius(diameter / 2).clickable(onClick), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(if (saved) R.drawable.wic_bookmark_filled else R.drawable.wic_bookmark),
            contentDescription = if (saved) "Remove from Saved" else "Save story",
            colorFilter = ColorFilter.tint(if (saved) W.accent else tint),
            modifier = GlanceModifier.size(diameter * 0.55f),
        )
    }
}

/** "mm:ss". */
fun clock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
