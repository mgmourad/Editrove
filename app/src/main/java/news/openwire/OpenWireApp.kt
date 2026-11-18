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
import news.openwire.domain.engine.BreakingNews
import news.openwire.notifications.BackgroundRefresher
import news.openwire.notifications.BreakingAlert
import news.openwire.notifications.NewHeadline
import news.openwire.notifications.RefreshScheduler
import news.openwire.notifications.TopicUpdate

class OpenWireApp : Application(), BackgroundRefresher.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        news.openwire.i18n.L10n.init(this)
        container = AppContainer(this)
        // Scheduling and widget sync belong to the main process only (WorkManager lives there).
        if (!isMainProcess()) return
        // Alert channels with Editrove's sounds: the bell, and the sting for breaking news.
        runCatching {
            news.openwire.notifications.Notifier.ensureChannel(this)
            news.openwire.notifications.Notifier.ensureBreakingChannel(this)
            news.openwire.briefing.BriefingReminders.ensureChannel(this)
        }
        // Home-screen widgets follow the briefing player and saved stories.
        appScope.launch(Dispatchers.Main) {
            container.libraryStore.saved.collect { news.openwire.widget.WidgetSync.feedsChanged(this@OpenWireApp, delayMs = 300) }
        }
        // Scheduled briefings follow their settings.
        appScope.launch {
            container.settingsRepository.settings.map { it.briefing.slots }.distinctUntilChanged()
                .collect { news.openwire.briefing.BriefingReminders.schedule(this@OpenWireApp, it) }
        }
        // Keep the periodic refresh in step with the user's preferences.
        appScope.launch {
            container.settingsRepository.settings
                .map { (it.backgroundRefresh || (it.notificationsEnabled && (it.notifyTopics.isNotEmpty() || it.breakingEnabled))) to it.refreshMinutes }
                .distinctUntilChanged()
                .collect { (on, minutes) -> RefreshScheduler.schedule(this@OpenWireApp, on, minutes.toLong()) }
        }
    }

    private fun isMainProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName()
        else getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        return name == null || name == packageName
    }

    override val backgroundRefresher: BackgroundRefresher by lazy {
        object : BackgroundRefresher {
            override suspend fun refresh(): List<TopicUpdate> {
                val s = container.settingsRepository.settings.value
                val topics = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)
                    // Breaking alerts read every visible topic, so each is kept fresh while they're on.
                    .filter { it.isVisible && (s.backgroundRefresh || it.id in s.notifyTopics || (s.breakingEnabled && s.notificationsEnabled)) }
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

            override suspend fun breaking(): List<BreakingAlert> {
                val s = container.settingsRepository.settings.value
                if (!s.breakingEnabled || !s.notificationsEnabled) return emptyList()
                val country = TopicRegistry.country(s.edition.country)
                // The big newsrooms' own breaking stories, plus every story from the sources the
                // reader follows (built-in, local, custom): a story is breaking wherever it comes from.
                val url = BreakingNews.queryUrl(s.breakingOutlets, hours = 2, country = country.code, language = country.hl.substringBefore('-'))
                val watched = url?.let { u ->
                    runCatching {
                        val feed = news.openwire.data.remote.GoogleNewsRssSource().fetchFeed(u)
                        val normalizer = news.openwire.data.remote.RssNormalizer(container.domainRegistry)
                        feed.items.mapIndexed { i, item -> normalizer.normalize(item, "BREAKING", i) }
                    }.getOrDefault(emptyList())
                }.orEmpty()
                val followed = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)
                    .filter { it.isVisible }
                    .flatMap { container.feedRepository.cachedArticles(it.id) }
                val items = (watched + followed).distinctBy { it.originalUrl }
                val norm: (String) -> String = { container.domainRegistry.normalizeDomain(it) }
                val hits = BreakingNews.detect(items, System.currentTimeMillis(), norm)
                val seen = container.feedState.seen(BREAKING_ID).orEmpty()
                val fresh = hits.filter { FeedOrdering.storyKey(it) !in seen }
                container.feedState.setSeen(BREAKING_ID, (hits.map(FeedOrdering::storyKey) + seen).toSet())
                return fresh.map { BreakingAlert(FeedOrdering.storyKey(it), NewHeadline(BreakingNews.cleanTitle(it.title), it.source, it.originalUrl)) }
            }

            override fun storyIntent(context: Context, headline: NewHeadline, topicId: String?): Intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    topicId?.let { putExtra(MainActivity.EXTRA_TOPIC, it) }
                    putExtra(MainActivity.EXTRA_URL, headline.url)
                    putExtra(MainActivity.EXTRA_TITLE, headline.title)
                }

            override fun launchIntent(context: Context, update: TopicUpdate): Intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_TOPIC, update.topicId)
                }
        }
    }

    private companion object { const val BREAKING_ID = "BREAKING" }
}
