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

/**
 * Implemented by the app (it owns the repositories): refresh feeds in the background
 * and report new headlines for topics opted into notifications (SPEC §6.6, §6.7).
 */
interface BackgroundRefresher {
    /** Refreshes cached feeds; returns new headlines per opted-in topic (already diffed). */
    suspend fun refresh(): List<TopicUpdate>

    /** Master switch for notifications (default off). */
    fun notificationsEnabled(): Boolean

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
        val updates = try {
            refresher.refresh()
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
        if (refresher.notificationsEnabled()) {
            updates.filter { it.headlines.isNotEmpty() }.forEach { Notifier.post(applicationContext, it, refresher.launchIntent(applicationContext, it)) }
        }
        return Result.success()
    }
}

object RefreshScheduler {
    private const val WORK = "openwire-refresh"

    /** Hourly refresh on any network; replaced whenever settings change. Cancelled when not needed. */
    fun schedule(context: Context, enabled: Boolean, intervalMinutes: Long = 60) {
        val wm = WorkManager.getInstance(context)
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
    const val CHANNEL = "headlines"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New headlines", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New stories in topics you follow"
                },
            )
        }
    }

    fun post(context: Context, update: TopicUpdate, intent: Intent) {
        if (!canPost(context)) return
        ensureChannel(context)
        val first = update.headlines.first()
        val pending = PendingIntent.getActivity(
            context, update.topicId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle("${update.topicLabel} · ${update.headlines.size} new")
        update.headlines.take(5).forEach { style.addLine(it.title) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_openwire)
            .setContentTitle(if (update.headlines.size == 1) update.topicLabel else "${update.topicLabel} · ${update.headlines.size} new stories")
            .setContentText(first.title)
            .setStyle(if (update.headlines.size > 1) style else NotificationCompat.BigTextStyle().bigText(first.title))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(update.topicId.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
