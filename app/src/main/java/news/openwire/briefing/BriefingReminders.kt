// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.briefing

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.R
import news.openwire.domain.model.BriefingSlot
import news.openwire.ui.home.BriefingService

/**
 * Morning / Afternoon / Evening briefings: at each enabled time a notification announces
 * the briefing. Tapping it opens the Audio tab, fetches the latest stories and plays;
 * "Play now" plays straight from the notification.
 */
object BriefingReminders {
    const val CHANNEL = "briefing_bell"
    private const val OLD_CHANNEL = "briefing_reminders"
    private const val ACTION = "news.editrove.BRIEFING_REMINDER"
    private const val EXTRA_KIND = "kind"

    /** (Re)schedules every enabled slot; cancels the others. */
    fun schedule(context: Context, slots: List<BriefingSlot>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in slots) {
            val pi = alarmIntent(context, slot.kind)
            am.cancel(pi)
            if (!slot.enabled) continue
            val at = nextTime(slot, System.currentTimeMillis())
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi) // within ten minutes
        }
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /** The next time (epoch ms) this slot's clock time comes round. */
    fun nextTime(slot: BriefingSlot, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, slot.hour); set(Calendar.MINUTE, slot.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now + 5_000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun alarmIntent(context: Context, kind: BriefingSlot.Kind): PendingIntent = PendingIntent.getBroadcast(
        context, 4400 + kind.ordinal,
        Intent(context, BriefingReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal fun notify(context: Context, kind: BriefingSlot.Kind) {
        ensureChannel(context)
        post(context, kind)
    }

    /** Creates the channel with Editrove's bell (and removes the previous, tone-less one). */
    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(OLD_CHANNEL) != null) nm.deleteNotificationChannel(OLD_CHANNEL)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Scheduled briefings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Your Morning, Afternoon and Evening audio briefings"
                    setSound(
                        news.openwire.notifications.Notifier.sound(context, news.openwire.notifications.R.raw.notify_bell),
                        news.openwire.notifications.Notifier.SOUND_ATTRS,
                    )
                },
            )
        }
    }

    private fun post(context: Context, kind: BriefingSlot.Kind) {
        val open = PendingIntent.getActivity(
            context, 4410 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TAB, "AUDIO")
                .putExtra(MainActivity.EXTRA_AUTOPLAY, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val play = PendingIntent.getForegroundService(
            context, 4420 + kind.ordinal,
            Intent(context, BriefingService::class.java).setAction(BriefingService.ACTION_PLAY_TOPIC)
                .putExtra(BriefingService.EXTRA_TOPIC, "TOP").putExtra(BriefingService.EXTRA_LABEL, "Top Stories"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(news.openwire.notifications.R.drawable.ic_stat_openwire)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.briefing_art))
            .setContentTitle("Your ${kind.label} is ready")
            .setContentText("Tap to hear today's top stories, freshly updated.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Play now", play)
            .build()
        if (!news.openwire.notifications.Notifier.canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify("briefing", kind.ordinal, n)
        } catch (e: SecurityException) {
            // Notification permission revoked between the check and the post.
        }
    }

    internal fun kindOf(intent: Intent) = BriefingSlot.Kind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) }
}

/** Fires at a slot's time: shows the briefing notification and books tomorrow's. */
class BriefingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slots = (context.applicationContext as OpenWireApp).container.settingsRepository.settings.value.briefing.slots
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, "android.intent.action.TIME_SET", Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> BriefingReminders.kindOf(intent)?.let { k -> if (slots.any { it.kind == k && it.enabled }) BriefingReminders.notify(context, k) }
        }
        BriefingReminders.schedule(context, slots)
    }
}
