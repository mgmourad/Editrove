// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.R
import news.openwire.domain.engine.FeedOrdering
import news.openwire.domain.engine.Languages
import news.openwire.domain.live.Jetstream
import news.openwire.domain.live.Newsroom
import news.openwire.domain.live.RealtimeBreaking
import news.openwire.domain.model.UserSettings
import news.openwire.i18n.tr
import news.openwire.notifications.BreakingAlert
import news.openwire.notifications.NewHeadline
import news.openwire.notifications.Notifier
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Opt-in real-time breaking news. Keeps one connection to Bluesky's public stream for the
 * watched newsrooms' posts (nothing about the reader is sent) and alerts the moment a
 * story is labelled breaking or several newsrooms post it at once. Android shows a
 * permanent notification while it runs; it reconnects by itself after network changes.
 */
class RealtimeAlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).connectTimeout(15, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var loop: Job? = null
    private var rooms: List<Newsroom> = emptyList()
    private lateinit var detector: RealtimeBreaking
    private val container get() = (application as OpenWireApp).container
    private val prefs by lazy { getSharedPreferences("realtime", MODE_PRIVATE) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        detector = RealtimeBreaking({ container.domainRegistry.normalizeDomain(it) })
        startInForeground(0)
        // Follows the settings: stops when switched off, reconnects when the newsrooms change.
        scope.launch {
            container.settingsRepository.settings.map { watchKey(it) }.distinctUntilChanged().collect { key ->
                if (key == null) { stopSelf(); return@collect }
                restart()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            container.settingsRepository.update { it.copy(realtimeAlerts = false) }
            stopSelf()
        }
        return START_STICKY
    }

    private fun watchKey(s: UserSettings): String? =
        if (!enabled(s)) null else Jetstream.watched(s.breakingOutlets, Languages.effective(s.language)).joinToString { it.key }

    private fun restart() {
        loop?.cancel()
        socket?.cancel()
        val s = container.settingsRepository.settings.value
        rooms = Jetstream.watched(s.breakingOutlets, Languages.effective(s.language))
        startInForeground(rooms.size)
        loop = scope.launch { connectLoop() }
    }

    /** Connects, and after any drop waits a little longer each time (5 s up to 5 min). */
    private suspend fun connectLoop() {
        var attempt = 0
        while (true) {
            val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val server = Jetstream.SERVERS[attempt % Jetstream.SERVERS.size]
            // Resume from where the last connection stopped (posts missed while offline), up to 10 minutes back.
            val cursor = prefs.getLong(KEY_CURSOR, 0L).takeIf { it > 0 && System.currentTimeMillis() * 1000 - it < 10 * 60_000_000L }
            val request = Request.Builder().url(Jetstream.url(server, rooms, cursor)).header("User-Agent", "Editrove/1.0 (Android news reader)").build()
            var opened = false
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened = true }
                override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(Unit) }
            })
            closed.await()
            attempt = if (opened) 0 else attempt + 1
            delay(listOf(5_000L, 10_000L, 30_000L, 60_000L, 120_000L, 300_000L)[attempt.coerceAtMost(5)])
        }
    }

    private var lastCursorSave = 0L

    private fun handle(text: String) {
        val post = Jetstream.parse(text) ?: return
        val now = System.currentTimeMillis()
        if (now - lastCursorSave > 15_000) { prefs.edit().putLong(KEY_CURSOR, post.timeUs).apply(); lastCursorSave = now }
        val room = rooms.firstOrNull { it.did == post.did } ?: return
        val alert = detector.onPost(post, room) ?: return
        // Old news replayed after a reconnect isn't breaking any more.
        if (now - (alert.article.publishedAt ?: now) > 25 * 60_000L) return
        val key = FeedOrdering.storyKey(alert.article)
        val seen = container.feedState.seen("BREAKING").orEmpty()
        if (key in seen) return
        container.feedState.setSeen("BREAKING", seen + key)
        val headline = NewHeadline(alert.article.title, alert.newsroom.name, alert.article.originalUrl)
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_URL, headline.url)
            putExtra(MainActivity.EXTRA_TITLE, headline.title)
        }
        Notifier.postBreaking(this, BreakingAlert(key, headline), open)
    }

    private fun startInForeground(count: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.rt_channel), NotificationManager.IMPORTANCE_MIN).apply {
                description = tr(R.string.rt_channel_desc); setShowBadge(false)
            },
        )
        val off = PendingIntent.getService(this, 4801, Intent(this, RealtimeAlertService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 4802, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(news.openwire.notifications.R.drawable.ic_stat_openwire)
            .setContentTitle(tr(R.string.rt_running_title))
            .setContentText(if (count > 0) tr(R.string.rt_running_text, count) else tr(R.string.rt_connecting))
            .setOngoing(true).setSilent(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(0, tr(R.string.rt_turn_off), off)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onDestroy() {
        socket?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "realtime_connection"
        private const val NOTIFICATION_ID = 4800
        private const val ACTION_OFF = "news.editrove.REALTIME_OFF"
        private const val KEY_CURSOR = "cursor_us"

        fun enabled(s: UserSettings) = s.realtimeAlerts && s.breakingEnabled && s.notificationsEnabled

        /** Starts (or keeps) the service when the setting is on. Safe to call from anywhere. */
        fun sync(context: Context, s: UserSettings) {
            if (!enabled(s)) {
                context.stopService(Intent(context, RealtimeAlertService::class.java)); return
            }
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, RealtimeAlertService::class.java)) }
        }
    }
}
