// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import news.openwire.data.local.InMemoryAppearance
import news.openwire.data.local.InMemoryLibrary
import news.openwire.data.local.InMemorySettingsRepository
import news.openwire.data.repository.FeedCheck
import news.openwire.data.repository.FeedRepository
import news.openwire.data.repository.FeedResult
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Strategy
import news.openwire.domain.model.Topic
import news.openwire.ui.home.ArchiveState
import news.openwire.ui.home.HomeViewModel
import news.openwire.ui.home.NavTab
import news.openwire.ui.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private class FakeRepo(private val articles: (Topic) -> List<Article> = { listOf(Article("${it.id}-1", "Story for ${it.id}", originalUrl = "https://example.com/${it.id}")) }) : FeedRepository {
        val requests = mutableListOf<Pair<Topic, Boolean>>()
        override fun observeTopic(topic: Topic, force: Boolean): Flow<FeedResult> {
            requests += topic to force
            return flowOf(FeedResult.Success(articles(topic), fromCache = false, updatedAt = 1L))
        }
        override suspend fun verifyFeed(url: String) = FeedCheck(ok = url.contains("good"), count = 5, title = "Good Feed", error = "bad")
    }

    private val settings = InMemorySettingsRepository()
    private val registry = DomainRegistry()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun home(repo: FeedRepository = FakeRepo(), archive: suspend (Article) -> String? = { null }) =
        HomeViewModel(repo, InMemoryLibrary(), InMemoryAppearance(), settings, registry, archive)

    private fun settingsVm(repo: FeedRepository = FakeRepo()) = SettingsViewModel(settings, registry, repo, catalog = { emptyList() }, clock = { 42L })

    @Test fun loadsFirstTopicOnStart() {
        val vm = home()
        assertEquals("TOP", vm.currentTopic.id)
        assertTrue(vm.feeds.value["TOP"] is FeedResult.Success)
    }

    @Test fun hiddenTopicsDisappearAndSelectionStaysValid() {
        val vm = home()
        settings.update { it.copy(hiddenTopics = setOf("TOP")) }
        assertFalse(vm.topics.any { it.id == "TOP" })
        assertEquals("LATEST", vm.currentTopic.id)
    }

    @Test fun refreshForcesTheNetwork() {
        val repo = FakeRepo()
        val vm = home(repo)
        vm.refresh()
        assertTrue(repo.requests.any { it.first.id == "TOP" && it.second })
    }

    @Test fun editionChangeReloadsLiveTopics() {
        val repo = FakeRepo()
        val vm = home(repo)
        vm.selectTopicId("US")
        settings.update { it.copy(edition = LocalEdition("London", "England", "GB")) }
        val usLoads = repo.requests.filter { it.first.id == "US" }
        assertEquals(2, usLoads.size)
        assertTrue(usLoads.last().first.liveUrl!!.contains("gl=GB"))
        assertEquals("UK", vm.currentTopic.label)
    }

    @Test fun sharedLinksOpenInTheReaderWithPaywallRouting() {
        val vm = home()
        assertTrue(vm.openLink("Worth a read: https://www.wsj.com/articles/some-story-123"))
        val a = vm.selected.value!!
        assertEquals("https://www.wsj.com/articles/some-story-123", a.originalUrl)
        assertEquals("wsj.com", a.domain)
        assertTrue(vm.sheetOpen.value)
        assertFalse(vm.openLink("no link here"))
    }

    @Test fun hardPaywallWithoutArchiveIsLocked() {
        registry.setDomainRule("locked.example", Strategy.ARCHIVE_IS)
        val repo = FakeRepo { listOf(registryRouted(Article("x", "Locked story", domain = "locked.example", originalUrl = "https://locked.example/a"))) }
        val vm = home(repo, archive = { null })
        assertEquals(ArchiveState.Locked, vm.archive.value["https://locked.example/a"])
    }

    @Test fun archivedStoryIsAvailable() {
        registry.setDomainRule("archived.example", Strategy.WAYBACK)
        val repo = FakeRepo { listOf(registryRouted(Article("y", "Archived", domain = "archived.example", originalUrl = "https://archived.example/b"))) }
        val vm = home(repo, archive = { "https://web.archive.org/x" })
        assertEquals(ArchiveState.Available("https://web.archive.org/x"), vm.archive.value["https://archived.example/b"])
    }

    private fun registryRouted(a: Article) = news.openwire.domain.engine.StrategyRouter.applyStrategy(registry, a)

    @Test fun sortModePersists() {
        val vm = home()
        vm.setSortMode(SortMode.OLDEST)
        assertEquals(SortMode.OLDEST, settings.settings.value.sortMode)
    }

    @Test fun discoverLoadsEveryTopic() {
        val repo = FakeRepo()
        val vm = home(repo)
        vm.selectTab(NavTab.DISCOVER)
        assertEquals(vm.topics.size, repo.requests.map { it.first.id }.distinct().size)
    }

    @Test fun settingsTopicsLifecycle() {
        val vm = settingsVm()
        assertTrue(vm.addTopic("Formula 1", "F1, Grand Prix", emptyList()))
        val id = settings.settings.value.customTopics.single().id
        assertTrue(vm.allTopics.value.any { it.id == id })
        vm.setNotifyTopic(id, true)
        vm.deleteTopic(id)
        assertTrue(settings.settings.value.customTopics.isEmpty())
        assertFalse(id in settings.settings.value.notifyTopics)
        assertFalse(vm.addTopic("  ", "", emptyList()))
    }

    @Test fun cannotHideTheLastVisibleTopic() {
        val vm = settingsVm()
        val ids = vm.allTopics.value.map { it.id }
        ids.forEach { vm.setTopicVisible(it, false) }
        assertEquals(1, vm.allTopics.value.count { it.isVisible })
    }

    @Test fun addSourceVerifiesFirst() = runTest {
        val vm = settingsVm()
        var ok: Boolean? = null
        vm.addSource("", "https://bad.example/rss", listOf("TOP"), 8) { ok = it }
        assertEquals(false, ok)
        vm.addSource("", "https://good.example/rss", listOf("TOP"), 8) { ok = it }
        assertEquals(true, ok)
        assertEquals("Good Feed", settings.settings.value.customFeeds.single().name)
    }

    @Test fun backupRoundTrip() {
        val vm = settingsVm()
        vm.setEdition("Seattle", "Washington", "US")
        vm.setRule("economist.com", Strategy.WAYBACK)
        val json = vm.exportJson()
        vm.resetEverything()
        assertEquals(LocalEdition.DEFAULT, settings.settings.value.edition)
        vm.importJson(json)
        assertEquals("Seattle", settings.settings.value.edition.city)
        assertEquals(Strategy.WAYBACK, registry.getRule("economist.com").strategy)
    }
}
