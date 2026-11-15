package news.openwire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Google's favicon service for a publisher domain. */
fun faviconUrl(domain: String?, size: Int = 64): String? =
    domain?.takeIf { it.isNotBlank() }?.let { "https://www.google.com/s2/favicons?domain=$it&sz=$size" }

/**
 * The publisher's logo (favicon), falling back to a lettered circle when the
 * domain is unknown. Used next to the source name and as the article-image
 * fallback canvas.
 */
@Composable
fun SourceLogo(domain: String?, source: String?, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val url = faviconUrl(domain, (size.value * 4).toInt())
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = source?.firstOrNull()?.uppercase() ?: "•",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
