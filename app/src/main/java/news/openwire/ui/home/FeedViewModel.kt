package news.openwire.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Topic

data class FeedUiState(
    val topics: List<Topic>,
    val selected: Topic,
    val result: FeedResult,
)

class FeedViewModel(
    private val repository: FeedRepository,
) : ViewModel() {

    private val topics = TopicRegistry.defaultTopics()
    private val selected = MutableStateFlow(topics.first())
    private val refreshTrigger = MutableStateFlow(0)

    /** Cold flow of UI state — the pure, testable logic. */
    val feedState: Flow<FeedUiState> = combine(selected, refreshTrigger) { topic, _ -> topic }
        .flatMapLatest { topic ->
            repository.observeTopic(topic).map { result ->
                FeedUiState(topics = topics, selected = topic, result = result)
            }
        }

    val uiState: StateFlow<FeedUiState> = feedState
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FeedUiState(topics, topics.first(), FeedResult.Loading),
        )

    fun selectTopic(id: String) {
        topics.firstOrNull { it.id == id }?.let { selected.value = it }
    }

    fun retry() {
        refreshTrigger.value += 1
    }

    companion object {
        fun factory(repository: FeedRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { FeedViewModel(repository) }
        }
    }
}
