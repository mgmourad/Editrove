package news.openwire.ui.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.model.Topic
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedViewModelTest {

    private class FakeRepository(private val result: FeedResult) : FeedRepository {
        override fun observeTopic(topic: Topic): Flow<FeedResult> = flowOf(result)
    }

    @Test
    fun networkErrorSurfacesErrorStateNotLoading() = runTest {
        val viewModel = FeedViewModel(FakeRepository(FeedResult.Error("boom")))
        val state = viewModel.feedState.first()
        assertTrue(state.result is FeedResult.Error)
    }

    @Test
    fun emptyTopicSurfacesEmptyState() = runTest {
        val viewModel = FeedViewModel(FakeRepository(FeedResult.Empty("US")))
        viewModel.selectTopic("US")
        val state = viewModel.feedState.first()
        assertTrue(state.result is FeedResult.Empty)
    }
}
