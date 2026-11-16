// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import news.openwire.data.repository.FeedResult
import news.openwire.di.AppContainer
import news.openwire.domain.engine.FeedOrdering
import news.openwire.domain.engine.TopicRegistry
import news.openwire.notifications.BackgroundRefresher
import news.openwire.notifications.NewHeadline
import news.openwire.notifications.RefreshScheduler
import news.openwire.notifications.TopicUpdate

class OpenWireApp : Application(), BackgroundRefresher.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Home-screen widgets follow the briefing player and saved stories.
        appScope.launch(Dispatchers.Main) {
            container.libraryStore.saved.collect { news.openwire.widget.WidgetSync.feedsChanged(this@OpenWireApp, delayMs = 300) }
        }
        // Keep the periodic refresh in step with the user's preferences.
        appScope.launch {
            container.settingsRepository.settings
                .map { it.backgroundRefresh || (it.notificationsEnabled && it.notifyTopics.isNotEmpty()) }
                .distinctUntilChanged()
                .collect { RefreshScheduler.schedule(this@OpenWireApp, it) }
        }
    }

    override val backgroundRefresher: BackgroundRefresher by lazy {
        object : BackgroundRefresher {
            override suspend fun refresh(): List<TopicUpdate> {
                val s = container.settingsRepository.settings.value
                val topics = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder)
                    .filter { it.isVisible && (s.backgroundRefresh || it.id in s.notifyTopics) }
                val updates = mutableListOf<TopicUpdate>()
                val now = System.currentTimeMillis()
                for (topic in topics) {
                    // Already fresh from a recent visit (e.g. the app is open): only notify topics
                    // need checking again.
                    if (topic.id !in s.notifyTopics && now - container.feedState.lastUpdated(topic.id) < 20 * 60_000L) continue
                    val result = runCatching { container.feedRepository.observeTopic(topic, force = true).last() }.getOrNull()
                    val articles = (result as? FeedResult.Success)?.takeIf { !it.fromCache }?.articles ?: continue
                    val seen = container.feedState.seen(topic.id)
                    val fresh = FeedOrdering.newStories(articles, seen)
                    container.feedState.setSeen(topic.id, (articles.map(FeedOrdering::storyKey) + seen.orEmpty()).toSet())
                    if (topic.id in s.notifyTopics && fresh.isNotEmpty()) {
                        updates += TopicUpdate(topic.id, topic.label, fresh.take(8).map { NewHeadline(it.title, it.source, it.originalUrl) })
                    }
                }
                return updates
            }

            override fun notificationsEnabled(): Boolean = container.settingsRepository.settings.value.notificationsEnabled

            override fun launchIntent(context: Context, update: TopicUpdate): Intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_TOPIC, update.topicId)
                    if (update.headlines.size == 1) {
                        putExtra(MainActivity.EXTRA_URL, update.headlines.first().url)
                        putExtra(MainActivity.EXTRA_TITLE, update.headlines.first().title)
                    }
                }
        }
    }
}
