package news.openwire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Emerald,
    secondary = EmeraldDark,
    background = Paper,
    surface = Paper,
)

private val DarkColors = darkColorScheme(
    primary = Emerald,
    secondary = EmeraldDark,
    background = Ink,
    surface = InkSurface,
)

@Composable
fun OpenWireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
