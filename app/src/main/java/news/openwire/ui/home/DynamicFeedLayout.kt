package news.openwire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article

/**
 * Apple News-style dynamic feed: a full-width featured card, a swipeable
 * horizontal carousel, and a masonry grid of the rest. Columns adapt to the
 * screen width (more on tablets and unfolded foldables).
 */
@Composable
fun DynamicFeedLayout(
    articles: List<Article>,
    onArticleClick: (Article) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (articles.isEmpty()) return

    val featured = articles.first()
    val carousel = articles.drop(1).take(6)
    val rest = articles.drop(7)

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 168.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            FeaturedArticleCard(featured, onArticleClick)
        }
        if (carousel.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                HorizontalCarousel(carousel, onArticleClick)
            }
        }
        items(rest, key = { it.id }) { article ->
            ArticleCard(article, onArticleClick = { onArticleClick(article) })
        }
    }
}

@Composable
private fun FeaturedArticleCard(article: Article, onArticleClick: (Article) -> Unit) {
    Card(onClick = { onArticleClick(article) }, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    SourceLogo(article.domain, article.source, size = 72.dp)
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(article.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceLogo(article.domain, article.source, size = 18.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(article.source ?: article.domain ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(NewsProcessor.formatRelativeTime(article.publishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HorizontalCarousel(articles: List<Article>, onArticleClick: (Article) -> Unit) {
    Column {
        Text("Top stories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(articles, key = { it.id }) { article ->
                CarouselCard(article, onArticleClick)
            }
        }
    }
}

@Composable
private fun CarouselCard(article: Article, onArticleClick: (Article) -> Unit) {
    Card(onClick = { onArticleClick(article) }, modifier = Modifier.width(220.dp)) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    SourceLogo(article.domain, article.source, size = 40.dp)
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(article.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.source ?: article.domain ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
