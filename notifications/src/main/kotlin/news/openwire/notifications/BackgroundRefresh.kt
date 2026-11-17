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
data class NewHeadline(val title: String, val source: String?, val url: String?)

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

    interface Provider {
        val backgroundRefresher: BackgroundRefresher
    }
}

/** Periodic worker: refresh → diff → post local notifications. No server, no push. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val provider = applicationContext as? BackgroundRefresher.Provider ?: return Result.failure()
        val refresher = provider.backgroundRefresher
        // Breaking news first: it's the time-sensitive part of the run.
        val breaking = runCatching { refresher.breaking() }.getOrDefault(emptyList())
        // While Editrove is on screen the reader already sees new stories: mark them seen,
        // don't pop alerts over the app (that's what made alerts "arrive after opening").
        val onScreen = appInForeground()
        if (refresher.notificationsEnabled() && !onScreen) breaking.take(3).forEach { Notifier.postBreaking(applicationContext, it, refresher.storyIntent(applicationContext, it.headline, null)) }
        val updates = try {
            refresher.refresh()
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
        if (refresher.notificationsEnabled() && !onScreen) {
            updates.filter { it.headlines.isNotEmpty() }.forEach {
                Notifier.post(applicationContext, it, refresher.storyIntent(applicationContext, it.headlines.first(), it.topicId), refresher.launchIntent(applicationContext, it))
            }
        }
        return Result.success()
    }

    /** Needed for expedited runs on Android 11 and older (shown briefly, silently). */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel("refresh") == null) {
            nm?.createNotificationChannel(NotificationChannel("refresh", "Background refresh", NotificationManager.IMPORTANCE_MIN))
        }
        val n = NotificationCompat.Builder(applicationContext, "refresh")
            .setSmallIcon(R.drawable.ic_stat_openwire).setContentTitle("Updating news").setSilent(true).build()
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
    const val CHANNEL = "headlines_bell"
    /** Earlier topic-alert channels (silent default / heads-up without Editrove's tone). */
    private val OLD_CHANNELS = listOf("headlines", "headlines_alerts")
    const val BREAKING = "breaking_sting"
    private const val OLD_BREAKING = "breaking"

    /** A bundled sound for a notification channel (played at notification volume). */
    fun sound(context: Context, raw: Int): android.net.Uri =
        android.net.Uri.parse("android.resource://${context.packageName}/$raw")

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
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New headlines", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New stories in topics you follow"
                    setSound(sound(context, R.raw.notify_bell), SOUND_ATTRS) // Editrove's bell
                },
            )
        }
    }

    fun ensureBreakingChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(OLD_BREAKING) != null) nm.deleteNotificationChannel(OLD_BREAKING)
        if (nm.getNotificationChannel(BREAKING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(BREAKING, "Breaking news", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Breaking stories from the newsrooms you follow"
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
    fun post(context: Context, update: TopicUpdate, storyIntent: Intent, topicIntent: Intent) {
        if (!canPost(context)) return
        ensureChannel(context)
        val first = update.headlines.first()
        val more = update.headlines.size - 1
        val open = PendingIntent.getActivity(context, update.topicId.hashCode(), storyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val all = PendingIntent.getActivity(context, update.topicId.hashCode() + 1, topicIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val expanded = buildString {
            append(first.title)
            first.source?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(if (more == 0) update.topicLabel else "${update.topicLabel} · ${update.headlines.size} new stories")
            .setContentText(first.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(update.topicLabel)
                    .bigText(expanded)
                    .also { if (more > 0) it.setSummaryText("+$more more") },
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        if (more > 0) b.addAction(0, "See all ${update.headlines.size}", all)
        val n = b.build()
        try {
            NotificationManagerCompat.from(context).notify(update.topicId.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    /** A heads-up alert for one breaking story. */
    fun postBreaking(context: Context, alert: BreakingAlert, intent: Intent) {
        if (!canPost(context)) return
        ensureBreakingChannel(context)
        val h = alert.headline
        val pending = PendingIntent.getActivity(context, alert.key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, BREAKING)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle("Breaking" + (h.source?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
            .setContentText(h.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(h.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify("breaking", alert.key.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
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
