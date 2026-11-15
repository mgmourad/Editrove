package news.openwire.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import news.openwire.OpenWireApp
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Topic

@Composable
fun HomeScreen() {
    val app = LocalContext.current.applicationContext as OpenWireApp
    val context = LocalContext.current
    val repository = app.container.feedRepository
    val router = remember { ArticleRouter(app.container.domainRegistry, app.container.archiveChecker) }
    val scope = rememberCoroutineScope()
    val topics = remember { TopicRegistry.defaultTopics() }
    val pagerState = rememberPagerState(pageCount = { topics.size })

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TopicBar(
                topics = topics,
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                TopicFeed(
                    topic = topics[page],
                    repository = repository,
                    router = router,
                    scope = scope,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun TopicFeed(
    topic: Topic,
    repository: FeedRepository,
    router: ArticleRouter,
    scope: CoroutineScope,
    context: Context,
) {
    var retry by remember(topic.id) { mutableIntStateOf(0) }
    val result by produceState<FeedResult>(initialValue = FeedResult.Loading, topic.id, retry) {
        value = FeedResult.Loading
        repository.observeTopic(topic).collect { value = it }
    }

    when (val r = result) {
        is FeedResult.Loading -> LoadingState()
        is FeedResult.Success -> DynamicFeedLayout(
            articles = r.articles,
            onArticleClick = { article ->
                router.open(scope, context, article) {
                    Toast.makeText(context, "No readable copy available", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        is FeedResult.Error -> ErrorState(r.message, onRetry = { retry++ })
        is FeedResult.Empty -> EmptyState(r.topicId)
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unable to load headlines", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun EmptyState(topicId: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No headlines yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "This edition is being fetched live.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
