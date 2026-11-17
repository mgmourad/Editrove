// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    // The publisher's own high-res icon (manifest / touch icon), else a 128px+ favicon —
    // never the tiny default, which looks blurry even at 18dp on a dense screen.
    val logos = LocalLogoResolver.current
    val favicon = faviconUrl(domain, maxOf(128, (size.value * 4).toInt()))
    val best by androidx.compose.runtime.produceState<String?>(null, domain) { value = logos?.logo(domain) }
    var failed by androidx.compose.runtime.remember(best) { androidx.compose.runtime.mutableStateOf(false) }
    val url = best?.takeIf { !failed } ?: favicon
    if (url != null) {
        Box(modifier.size(size).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                onError = { if (url == best) failed = true },
                modifier = Modifier.size(size),
            )
        }
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
