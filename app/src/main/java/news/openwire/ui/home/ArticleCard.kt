package news.openwire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article

/**
 * Article image shown at full width without cropping. When the article has no
 * photo, the publisher logo fills the canvas as a fallback.
 */
@Composable
fun NewsImage(article: Article, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.FillWidth) {
    val imageUrl = article.imageUrl
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            SourceLogo(article.domain, article.source, size = 64.dp)
        }
    }
}

@Composable
private fun SourceRow(article: Article) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceLogo(article.domain, article.source, size = 16.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = article.source ?: article.domain ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = NewsProcessor.formatRelativeTime(article.publishedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A masonry card: full-width image (or logo canvas) with title and source row. */
@Composable
fun ArticleCard(
    article: Article,
    onArticleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onArticleClick, modifier = modifier) {
        Column {
            NewsImage(article, modifier = Modifier.fillMaxWidth())
            Column(modifier = Modifier.padding(12.dp)) {
                Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                Spacer(modifier = Modifier.height(8.dp))
                SourceRow(article)
            }
        }
    }
}
