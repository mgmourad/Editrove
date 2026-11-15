package news.openwire.ui.home

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article

@Composable
fun ArticleCard(
    article: Article,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(onClick = { openInCustomTabs(context, article) }, modifier = modifier) {
        Column {
            val imageUrl = article.imageUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(article.title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text(
                        text = article.source ?: article.domain ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val badge = article.badge
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = NewsProcessor.formatRelativeTime(article.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun openInCustomTabs(context: Context, article: Article) {
    val url = article.targetUrl ?: article.originalUrl ?: return
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}
