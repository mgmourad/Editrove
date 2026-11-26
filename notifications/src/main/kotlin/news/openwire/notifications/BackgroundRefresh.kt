// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** A genuinely new headline in an opted-in topic. */
data class NewHeadline(val title: String, val source: String?, val url: String?, val key: String? = null)

/** New headlines found for one topic during a background refresh. */
data class TopicUpdate(val topicId: String, val topicLabel: String, val headlines: List<NewHeadline>)

/** A breaking story from one of the newsrooms the reader follows. */
data class BreakingAlert(val key: String, val headline: NewHeadline)

/**
 * Implemented by the app (it owns the repositories): refresh feeds in the background
 * and report new headlines for topics opted into notifications (SPEC §6.6, §6.7).
 */
interface BackgroundRefresher {
    /** Refreshes cached feeds; returns new headlines per opted-in topic (already diffed). */
    suspend fun refresh(): List<TopicUpdate>

    /** Master switch for notifications (default off). */
    fun notificationsEnabled(): Boolean

    /** Breaking stories not alerted yet (empty when Breaking News is off). */
    suspend fun breaking(): List<BreakingAlert> = emptyList()

    /** Opens one story in the reader. */
    fun storyIntent(context: Context, headline: NewHeadline, topicId: String?): Intent

    /** The Activity that notification taps open. */
    fun launchIntent(context: Context, update: TopicUpdate): Intent

    /** The reader's sound and quiet-time choices. */
    fun alertPrefs(): news.openwire.domain.alerts.AlertPrefs = news.openwire.domain.alerts.AlertPrefs()

    /** "Pause 3 h" from an alert: no alerts until [untilMs]. */
    fun pauseAlerts(untilMs: Long) {}

    interface Provider {
        val backgroundRefresher: BackgroundRefresher
    }
}

/** Periodic worker: refresh → diff → post local notifications. No server, no push. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? BackgroundRefresher.Provider ?: return Result.failure()
        val refresher = provider.backgroundRefresher
        // While Editrove is on screen the reader already sees new stories: mark them seen,
        // don't pop alerts over the app (that's what made alerts "arrive after opening").
        val onScreen = appInForeground()
        // Topics first, so breaking news is judged against fresh stories from every source.
        val updates = try {
            refresher.refresh()
        } catch (e: Exception) {
            null
        }
        val breaking = runCatching { refresher.breaking() }.getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        // Everything found goes through one place: duplicates (any kind, any outlet, 48 h) are
        // dropped, quiet time is honoured, and the rest arrive one at a time.
        val found = breaking.take(3).map { b ->
            news.openwire.domain.alerts.PendingAlert(b.key, b.headline.title, b.headline.source, b.headline.url, breaking = true, foundAt = now)
        } + updates.orEmpty().flatMap { u ->
            // Two per topic at most per check: the rest are in the app.
            u.headlines.take(2).map { h ->
                news.openwire.domain.alerts.PendingAlert(h.key ?: h.url ?: h.title, h.title, h.source, h.url, u.topicId, u.topicLabel, foundAt = now)
            }
        }
        if (refresher.notificationsEnabled() && !onScreen) AlertCenter.submit(applicationContext, found, now)
        else AlertCenter.markSeen(applicationContext, found, now)
        if (updates == null) return if (runAttemptCount < 2) Result.retry() else Result.failure()
        return Result.success()
    }

    /** Needed for expedited runs on Android 11 and older (shown briefly, silently). */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel("refresh") == null) {
            nm?.createNotificationChannel(NotificationChannel("refresh", applicationContext.getString(R.string.n_refresh_channel), NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(applicationContext, "refresh")
            .setSmallIcon(R.drawable.ic_stat_openwire).setContentTitle(applicationContext.getString(R.string.n_updating)).setSilent(true).build()
        return androidx.work.ForegroundInfo(4302, n)
    }

    private fun appInForeground(): Boolean {
        val info = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(info)
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}

object RefreshScheduler {
    private const val WORK = "openwire-refresh"

    /** Hourly refresh on any network; replaced whenever settings change. Cancelled when not needed. */
    fun schedule(context: Context, enabled: Boolean, intervalMinutes: Long = 60) {
        // (15 minutes is WorkManager's floor; Android may still defer runs on battery saver.)
        val wm = WorkManager.getInstance(context)
        RefreshAlarm.set(context, if (enabled) intervalMinutes else 0)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

object Notifier {
    /**
     * Topic alerts pop up on screen (heads-up). Android never lets an app raise an existing
     * channel's importance, so this is a new channel; the old silent one is removed.
     */
    const val CHANNEL = "headlines_bell_v2"
    /** Earlier topic-alert channels (silent default / heads-up without Editrove's tone). */
    private val OLD_CHANNELS = listOf("headlines", "headlines_alerts", "headlines_bell")
    const val BREAKING = "breaking_sting_v2"
    private const val OLD_BREAKING = "breaking"

    /** A bundled sound for a notification channel (played at notification volume). */
    /**
     * The sound's address by name (android.resource://pkg/raw/name). A channel keeps its sound
     * for good, and resource numbers shift between builds, so a numeric address can end up
     * pointing at nothing (Android then plays its default sound).
     */
    fun sound(context: Context, raw: Int): android.net.Uri =
        android.net.Uri.parse("android.resource://${context.packageName}/raw/${context.resources.getResourceEntryName(raw)}")

    val SOUND_ATTRS: android.media.AudioAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        OLD_CHANNELS.forEach { if (nm.getNotificationChannel(it) != null) nm.deleteNotificationChannel(it) }
        // Created again each time so its name follows Editrove's language (the sound stays as set).
        run {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.n_headlines_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.n_headlines_channel_desc)
                    setSound(sound(context, R.raw.notify_bell), SOUND_ATTRS) // Editrove's bell
                },
            )
        }
    }

    fun ensureBreakingChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(OLD_BREAKING, "breaking_sting").forEach { if (nm.getNotificationChannel(it) != null) nm.deleteNotificationChannel(it) }
        run {
            nm.createNotificationChannel(
                NotificationChannel(BREAKING, context.getString(R.string.n_breaking_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.n_breaking_channel_desc)
                    setSound(sound(context, R.raw.breaking_sting), SOUND_ATTRS) // the briefing's transition sting
                },
            )
        }
    }

    /**
     * One notification per topic. Expanding it shows the lead story in full (headline,
     * outlet), not a list of truncated headlines; tapping opens that story, and
     * "See all" opens the topic.
     */
    private const val GROUP = "editrove_topics"

    /** The stack header when several topics have news at once (Android groups the rest under it). */
    fun postSummary(context: Context, updates: List<TopicUpdate>) {
        if (!canPost(context)) return
        val total = updates.sumOf { it.headlines.size }
        val style = NotificationCompat.InboxStyle().setSummaryText(context.getString(R.string.n_new_stories, total))
        updates.forEach { u -> val h = u.headlines.first(); style.addLine("${h.source?.takeIf { it.isNotBlank() } ?: u.topicLabel}: ${h.title}") }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(context.getString(R.string.n_topics_have_news, updates.size))
            .setContentText(updates.joinToString(" · ") { it.topicLabel })
            .setStyle(style)
            .setGroup(GROUP).setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(GROUP, 0, n) } catch (e: SecurityException) { }
    }

    fun post(context: Context, update: TopicUpdate, storyIntent: Intent, topicIntent: Intent, quiet: Boolean = false) {
        if (!canPost(context)) return
        ensureChannel(context)
        val first = update.headlines.first()
        val more = update.headlines.size - 1
        val open = PendingIntent.getActivity(context, update.topicId.hashCode(), storyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val all = PendingIntent.getActivity(context, update.topicId.hashCode() + 1, topicIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The newspaper is the title; the topic sits in the header beside the app name.
        val source = first.source?.takeIf { it.isNotBlank() } ?: update.topicLabel
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(source)
            .setSubText(if (more == 0) update.topicLabel else context.getString(R.string.n_topic_new, update.topicLabel, update.headlines.size))
            .setContentText(first.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(source)
                    .bigText(first.title)
                    .also { if (more > 0) it.setSummaryText(context.getString(R.string.n_more, more)) },
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup(GROUP)
            // New headlines replace the topic's earlier alert, and must sound again: "alert only
            // once" made every update silent while an older alert sat unread in the shade.
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .also { if (quiet) it.setSilent(true) }
        if (more > 0) b.addAction(0, context.getString(R.string.n_see_all, update.headlines.size), all)
        val n = b.build()
        try {
            NotificationManagerCompat.from(context).notify(update.topicId.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    /** One topic story, on its own (several arrive minutes apart, so each pops up and sounds). */
    fun postStory(context: Context, a: news.openwire.domain.alerts.PendingAlert, storyIntent: Intent, silent: Boolean) {
        if (!canPost(context)) return
        ensureChannel(context)
        val id = a.key.hashCode()
        val open = PendingIntent.getActivity(context, id, storyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val source = a.source?.takeIf { it.isNotBlank() } ?: a.topicLabel.orEmpty()
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(source)
            .setSubText(a.topicLabel)
            .setContentText(a.title)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(source).bigText(a.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setGroup(GROUP)
            .setWhen(System.currentTimeMillis()).setShowWhen(true)
            .addAction(0, context.getString(R.string.n_pause_3h), AlertPauseReceiver.intent(context, "story", id))
            .setSilent(silent)
        try { NotificationManagerCompat.from(context).notify("story", id, b.build()) } catch (e: SecurityException) { }
        trim(context, "story")
    }

    private fun trim(context: Context, tag: String) = runCatching {
        val nm = NotificationManagerCompat.from(context)
        context.getSystemService(NotificationManager::class.java)?.activeNotifications
            ?.filter { it.tag == tag }?.sortedByDescending { it.postTime }?.drop(MAX_SHOWN)
            ?.forEach { nm.cancel(tag, it.id) }
    }

    private const val MAX_SHOWN = 6

    /** A heads-up alert for one breaking story. */
    fun postBreaking(context: Context, alert: BreakingAlert, intent: Intent, silent: Boolean = false) {
        if (!canPost(context)) return
        ensureBreakingChannel(context)
        val h = alert.headline
        val pending = PendingIntent.getActivity(context, alert.key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, BREAKING)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(context.getString(R.string.n_breaking) + (h.source?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
            .setContentText(h.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(h.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.n_pause_3h), AlertPauseReceiver.intent(context, "breaking", alert.key.hashCode()))
            .setSilent(silent)
            .build()
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify("breaking", alert.key.hashCode(), n)
            // Keep the shade tidy: the five newest breaking alerts stay, older ones go.
            runCatching {
                context.getSystemService(NotificationManager::class.java)?.activeNotifications
                    ?.filter { it.tag == "breaking" }?.sortedByDescending { it.postTime }?.drop(MAX_BREAKING_SHOWN)
                    ?.forEach { nm.cancel("breaking", it.id) }
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private const val MAX_BREAKING_SHOWN = 5
}

/**
 * A periodic job alone can be held back for hours while the phone sits idle (Doze), then
 * fire the moment the app opens — alerts arriving late. An idle-allowed alarm at the same
 * interval wakes Editrove on time and runs one expedited refresh.
 */
object RefreshAlarm {
    private const val PREFS = "refresh_alarm"
    const val ACTION = "news.editrove.REFRESH_ALARM"

    fun set(context: Context, minutes: Long) {
        val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("minutes", minutes).apply()
        if (minutes <= 0) return
        am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + minutes * 60_000L, pi)
    }

    internal fun rearm(context: Context) =
        set(context, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("minutes", 0))

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 4301, Intent(context, RefreshAlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class RefreshAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val req = androidx.work.OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("openwire-refresh-now", androidx.work.ExistingWorkPolicy.KEEP, req)
        RefreshAlarm.rearm(context)
    }
}
