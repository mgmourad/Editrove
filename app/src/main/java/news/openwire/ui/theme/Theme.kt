package news.openwire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Rose,
    secondary = Indigo,
    background = Obsidian,
    surface = ObsidianSurface,
    surfaceVariant = GlassSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SpecularBorder,
)

private val LightColors = lightColorScheme(
    primary = RoseLight,
    secondary = IndigoLight,
    background = Paper,
    surface = PaperSurface,
    surfaceVariant = GlassSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = InnerBorderLight,
)

@Composable
fun OpenWireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OpenWireTypography,
        content = content,
    )
}
