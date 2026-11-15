// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Liquid Glass design tokens that have no Material 3 equivalent. Values mirror the
 * CSS custom properties in the reference prototype (`:root` / `html.light-theme`).
 */
@Immutable
data class GlassTokens(
    val isDark: Boolean,
    val canvas: Color,
    val deviceBg: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassPill: Color,
    val specularHighlight: Color,
    val specularBorder: Color,
    val innerBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val indigo: Color,
    /** Hairline used for dividers and pill borders (white/10 in dark). */
    val hairline: Color,
    /** Fill for neutral chips (white/10 in dark). */
    val chipFill: Color,
    val shadow: Color,
    /** Ambient mesh glows: indigo top-left, rose top-right, emerald bottom. */
    val meshIndigo: Color,
    val meshRose: Color,
    val meshEmerald: Color,
)

private val DarkTokens = GlassTokens(
    isDark = true,
    canvas = ObsidianCanvas,
    deviceBg = Obsidian,
    glassSurface = GlassSurfaceDark,
    glassElevated = GlassElevatedDark,
    glassPill = GlassPillDark,
    specularHighlight = SpecularHighlight,
    specularBorder = SpecularBorder,
    innerBorder = InnerBorder,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textMuted = TextMutedDark,
    accent = Rose,
    accentSoft = Rose400,
    indigo = Indigo400,
    hairline = Color.White.copy(alpha = 0.10f),
    chipFill = Color.White.copy(alpha = 0.10f),
    shadow = Color.Black.copy(alpha = 0.65f),
    meshIndigo = Indigo.copy(alpha = 0.16f),
    meshRose = Rose.copy(alpha = 0.12f),
    meshEmerald = Color(0xFF10B981).copy(alpha = 0.08f),
)

private val LightTokens = GlassTokens(
    isDark = false,
    canvas = PaperCanvas,
    deviceBg = Paper,
    glassSurface = GlassSurfaceLight,
    glassElevated = GlassElevatedLight,
    glassPill = GlassPillLight,
    specularHighlight = SpecularHighlightLight,
    specularBorder = SpecularBorderLight,
    innerBorder = InnerBorderLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textMuted = TextMutedLight,
    accent = RoseLight,
    accentSoft = RoseLight,
    indigo = IndigoLight,
    hairline = Slate900.copy(alpha = 0.08f),
    chipFill = Slate900.copy(alpha = 0.05f),
    shadow = Slate900.copy(alpha = 0.18f),
    meshIndigo = Indigo.copy(alpha = 0.09f),
    meshRose = RoseLight.copy(alpha = 0.07f),
    meshEmerald = Color.Transparent,
)

val LocalGlass = staticCompositionLocalOf { DarkTokens }

/** Shorthand: `Glass.accent`, `Glass.hairline`, … */
val Glass: GlassTokens
    @Composable get() = LocalGlass.current

private val DarkColors = darkColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Rose.copy(alpha = 0.18f),
    onPrimaryContainer = Rose400,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color.White.copy(alpha = 0.10f),
    onSecondaryContainer = TextPrimaryDark,
    background = Obsidian,
    onBackground = TextPrimaryDark,
    surface = Obsidian,
    onSurface = TextPrimaryDark,
    surfaceVariant = GlassSurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = ObsidianSurface,
    outline = SpecularBorder,
    outlineVariant = InnerBorder,
)

private val LightColors = lightColorScheme(
    primary = RoseLight,
    onPrimary = Color.White,
    primaryContainer = RoseLight.copy(alpha = 0.12f),
    onPrimaryContainer = RoseLight,
    secondary = IndigoLight,
    onSecondary = Color.White,
    secondaryContainer = Slate900.copy(alpha = 0.06f),
    onSecondaryContainer = TextPrimaryLight,
    background = Paper,
    onBackground = TextPrimaryLight,
    surface = Paper,
    onSurface = TextPrimaryLight,
    surfaceVariant = GlassSurfaceLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = PaperSurface,
    outline = InnerBorderLight,
    outlineVariant = InnerBorderLight,
)

@Composable
fun OpenWireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlass provides if (darkTheme) DarkTokens else LightTokens) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = OpenWireTypography,
            content = content,
        )
    }
}
