package news.openwire.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import news.openwire.OpenWireApp
import news.openwire.data.repository.FeedResult
import news.openwire.domain.model.Article

@Composable
fun HomeScreen() {
    val app = LocalContext.current.applicationContext as OpenWireApp
    val viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(app.container.feedRepository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TopicBar(
                topics = state.topics,
                selected = state.selected,
                onSelect = viewModel::selectTopic,
            )
            when (val result = state.result) {
                is FeedResult.Loading -> LoadingState()
                is FeedResult.Success -> FeedList(result.articles)
                is FeedResult.Error -> ErrorState(result.message, onRetry = viewModel::retry)
                is FeedResult.Empty -> EmptyState(result.topicId)
            }
        }
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
                "This edition is fetched live and is coming soon.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FeedList(articles: List<Article>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(articles, key = { it.id }) { article ->
            ArticleCard(article = article, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
