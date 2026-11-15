package news.openwire.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import news.openwire.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import news.openwire.OpenWireApp
import news.openwire.data.remote.ArticleTextExtractor
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.Topic
import news.openwire.ui.theme.Obsidian

private enum class NavTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Home),
    DISCOVER("Discover", Icons.Filled.Search),
    AUDIO("Audio", Icons.Filled.PlayArrow),
    SAVED("Saved", Icons.Filled.Bookmark),
}

@Composable
fun HomeScreen() {
    val app = LocalContext.current.applicationContext as OpenWireApp
    val context = LocalContext.current
    val repository = app.container.feedRepository
    val router = remember { ArticleRouter(app.container.domainRegistry, app.container.archiveChecker) }
    val extractor = app.container.articleTextExtractor
    val scope = rememberCoroutineScope()
    val topics = remember { TopicRegistry.defaultTopics() }
    val pagerState = rememberPagerState(pageCount = { topics.size })
    val selectedArticle = remember { mutableStateOf<Article?>(null) }
    val selectedTab = remember { mutableStateOf(NavTab.TODAY) }
    val haptics = LocalHapticFeedback.current

    val expanded = LocalConfiguration.current.screenWidthDp >= 600
    if (expanded) {
            ExpandedLayout(
                topics = topics,
                pagerState = pagerState,
                repository = repository,
                router = router,
                extractor = extractor,
                scope = scope,
                context = context,
                selectedArticle = selectedArticle.value,
                onSelectArticle = { selectedArticle.value = it },
                selectedTab = selectedTab.value,
                onSelectTab = { selectedTab.value = it; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            )
        } else {
            CompactLayout(
                topics = topics,
                pagerState = pagerState,
                repository = repository,
                router = router,
                extractor = extractor,
                scope = scope,
                context = context,
                selectedArticle = selectedArticle.value,
                onSelectArticle = { selectedArticle.value = it },
                onCloseArticle = { selectedArticle.value = null },
                selectedTab = selectedTab.value,
                onSelectTab = { selectedTab.value = it; haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            )
    }
}

@Composable
private fun CompactLayout(
    topics: List<Topic>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    repository: FeedRepository,
    router: ArticleRouter,
    extractor: ArticleTextExtractor,
    scope: CoroutineScope,
    context: Context,
    selectedArticle: Article?,
    onSelectArticle: (Article) -> Unit,
    onCloseArticle: () -> Unit,
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopicBar(
                topics = topics,
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                TopicFeed(topics[page], repository, onSelectArticle)
            }
        }
        BottomNavPill(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
        selectedArticle?.let { article ->
            ReaderOverlay(
                article = article,
                router = router,
                extractor = extractor,
                scope = scope,
                context = context,
                onClose = onCloseArticle,
            )
        }
    }
}

@Composable
private fun ExpandedLayout(
    topics: List<Topic>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    repository: FeedRepository,
    router: ArticleRouter,
    extractor: ArticleTextExtractor,
    scope: CoroutineScope,
    context: Context,
    selectedArticle: Article?,
    onSelectArticle: (Article) -> Unit,
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        SideRail(selectedTab = selectedTab, onSelectTab = onSelectTab)
        Column(modifier = Modifier.fillMaxHeight().weight(0.5f)) {
            TopicBar(
                topics = topics,
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                TopicFeed(topics[page], repository, onSelectArticle)
            }
        }
        Box(modifier = Modifier.fillMaxHeight().weight(0.5f).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))) {
            if (selectedArticle != null) {
                ReaderContent(article = selectedArticle, router = router, extractor = extractor, scope = scope, context = context, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a story", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TopicFeed(
    topic: Topic,
    repository: FeedRepository,
    onArticleClick: (Article) -> Unit,
) {
    var retry by remember(topic.id) { mutableIntStateOf(0) }
    val result by produceState<FeedResult>(initialValue = FeedResult.Loading, topic.id, retry) {
        value = FeedResult.Loading
        repository.observeTopic(topic).collect { value = it }
    }
    when (val r = result) {
        is FeedResult.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is FeedResult.Success -> DynamicFeedLayout(articles = r.articles, onArticleClick = onArticleClick, modifier = Modifier.fillMaxSize())
        is FeedResult.Error -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Unable to load headlines\n\n${r.message}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is FeedResult.Empty -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Fetching this edition…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomNavPill(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            NavTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    if (selected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SideRail(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
) {
    Column(
        modifier = Modifier.width(64.dp).fillMaxHeight().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "OpenWire",
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)),
        )
        Spacer(modifier = Modifier.weight(1f))
        NavTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelectTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Obsidian), contentAlignment = Alignment.Center) {
            Text("M", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
