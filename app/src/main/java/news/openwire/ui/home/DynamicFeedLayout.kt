package news.openwire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.ui.theme.Obsidian

/**
 * Editorial Bento feed: full-bleed hero cover, a live ticker, and an asymmetric
 * 2-column Bento grid. Used as the feed content for each topic.
 */
@Composable
fun DynamicFeedLayout(
    articles: List<Article>,
    onArticleClick: (Article) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (articles.isEmpty()) return
    val rest = articles.drop(1)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }, key = "hero") {
            HeroCard(articles.first(), onArticleClick)
        }
        item(span = { GridItemSpan(2) }, key = "ticker") {
            TickerStrip(articles, onArticleClick)
        }
        item(span = { GridItemSpan(2) }, key = "bento-header") {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "BENTO DISPATCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${rest.size} curated reads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(rest, key = { it.id }, span = { item -> if (rest.indexOf(item) == 2) GridItemSpan(2) else GridItemSpan(1) }) { article ->
            BentoCard(article, onArticleClick)
        }
    }
}

@Composable
private fun HeroCard(article: Article, onArticleClick: (Article) -> Unit) {
    GlassCard(onClick = { onArticleClick(article) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, Obsidian.copy(alpha = 0.92f)),
                        ),
                    ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    SourceLogo(article.domain, article.source, size = 72.dp)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (article.source ?: article.domain ?: "").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = NewsProcessor.formatRelativeTime(article.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TickerStrip(articles: List<Article>, onArticleClick: (Article) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            if (listState.canScrollForward) listState.scrollBy(2f) else listState.scrollToItem(0)
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            LazyRow(state = listState, modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(MARKET_QUOTES.size) { index ->
                    val (symbol, change, color) = MARKET_QUOTES[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(change, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
                items(articles.take(12), key = { it.id }) { article ->
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(220.dp).clickable { onArticleClick(article) },
                    )
                }
            }
        }
    }
}

private data class MarketQuote(val symbol: String, val change: String, val color: Color)

private val MARKET_QUOTES = listOf(
    MarketQuote("NVDA", "+3.8%", Color(0xFF34D399)),
    MarketQuote("AAPL", "+1.2%", Color(0xFF34D399)),
    MarketQuote("BTC", "$67,240", Color(0xFFA78BFA)),
    MarketQuote("SPX", "+0.4%", Color(0xFF34D399)),
    MarketQuote("ESA", "Europa nominal", Color(0xFF60A5FA)),
)

@Composable
private fun BentoCard(article: Article, onArticleClick: (Article) -> Unit) {
    GlassCard(onClick = { onArticleClick(article) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(InnerRadius)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(InnerRadius)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    SourceLogo(article.domain, article.source, size = 36.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = (article.source ?: article.domain ?: "").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = NewsProcessor.formatRelativeTime(article.publishedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
