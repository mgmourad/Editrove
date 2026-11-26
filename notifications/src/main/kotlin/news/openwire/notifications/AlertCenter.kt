// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import news.openwire.domain.alerts.AlertLedger
import news.openwire.domain.alerts.AlertQueue
import news.openwire.domain.alerts.PendingAlert
import news.openwire.domain.alerts.QuietTime

/**
 * Every alert goes through here, whatever found it (the periodic check, breaking news, the
 * real-time stream, followed writers):
 *
 * 1. one memory for all of them, 48 hours: a story already alerted (same link, or a similar
 *    headline from any outlet) is never alerted again;
 * 2. quiet time: alerts found during it are remembered as seen, never delivered later in a heap;
 * 3. a queue, so several finds arrive one at a time a few minutes apart, not in a burst;
 * 4. the reader's sound choices.
 */
object AlertCenter {
    private const val PREFS = "alert_center"
    private const val K_LEDGER = "ledger"
    private const val K_QUEUE = "queue"
    private val lock = Any()

    /** Hands new finds over. Returns how many were queued (after duplicates and quiet time). */
    fun submit(context: Context, found: List<PendingAlert>, now: Long = System.currentTimeMillis()): Int {
        val refresher = (context.applicationContext as? BackgroundRefresher.Provider)?.backgroundRefresher ?: return 0
        val prefs = refresher.alertPrefs()
        val queued = synchronized(lock) {
            val p = sp(context)
            var ledger = AlertLedger.decode(p.getString(K_LEDGER, null))
            val fresh = ArrayList<PendingAlert>()
            for (a in found) {
                if (ledger.alreadyAlerted(a.key, a.title, now)) continue
                ledger = ledger.record(a.key, a.title, now) // seen now, even if quiet time drops it
                fresh += a
            }
            val quiet = QuietTime.active(prefs, now)
            val q = AlertQueue.decode(p.getString(K_QUEUE, null)).let { if (quiet) it else it.add(fresh) }
            p.edit().putString(K_LEDGER, ledger.encode()).putString(K_QUEUE, q.encode()).apply()
            if (quiet) 0 else fresh.size
        }
        drain(context)
        return queued
    }

    /** Only remembers (the reader already saw these in the app). */
    fun markSeen(context: Context, found: List<PendingAlert>, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        val p = sp(context)
        var ledger = AlertLedger.decode(p.getString(K_LEDGER, null))
        found.forEach { if (!ledger.alreadyAlerted(it.key, it.title, now)) ledger = ledger.record(it.key, it.title, now) }
        p.edit().putString(K_LEDGER, ledger.encode()).apply()
    }

    /** Posts the next alert if its turn has come, and sets an alarm for the one after. */
    fun drain(context: Context, now: Long = System.currentTimeMillis()) {
        val refresher = (context.applicationContext as? BackgroundRefresher.Provider)?.backgroundRefresher ?: return
        val prefs = refresher.alertPrefs()
        val (post, wait) = synchronized(lock) {
            val p = sp(context)
            var q = AlertQueue.decode(p.getString(K_QUEUE, null))
            if (QuietTime.active(prefs, now) || !refresher.notificationsEnabled()) q = AlertQueue(emptyList(), q.lastPostedAt)
            val (head, rest, again) = q.next(now)
            p.edit().putString(K_QUEUE, rest.encode()).apply()
            head to again
        }
        post?.let { a ->
            val headline = NewHeadline(a.title, a.source, a.url)
            val intent = refresher.storyIntent(context, headline, a.topicId)
            val silent = !prefs.audible(a.channel, a.breaking)
            if (a.breaking) Notifier.postBreaking(context, BreakingAlert(a.key, headline), intent, silent = silent)
            else Notifier.postStory(context, a, intent, silent = silent)
        }
        schedule(context, wait)
    }

    private fun schedule(context: Context, waitMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 4310, Intent(context, AlertDrainReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (waitMs == null) return
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + waitMs.coerceAtLeast(1_000L), pi)
    }

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The next queued alert's turn. */
class AlertDrainReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val done = goAsync()
        Thread { try { AlertCenter.drain(context) } finally { done.finish() } }.start()
    }
}

/** "Pause 3 h" on an alert. */
class AlertPauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val refresher = (context.applicationContext as? BackgroundRefresher.Provider)?.backgroundRefresher ?: return
        refresher.pauseAlerts(System.currentTimeMillis() + intent.getLongExtra(EXTRA_MS, 3 * 3_600_000L))
        intent.getIntExtra(EXTRA_ID, 0).takeIf { it != 0 }?.let { id ->
            androidx.core.app.NotificationManagerCompat.from(context).cancel(intent.getStringExtra(EXTRA_TAG), id)
        }
    }

    companion object {
        const val EXTRA_MS = "pause_ms"
        const val EXTRA_ID = "nid"
        const val EXTRA_TAG = "ntag"

        fun intent(context: Context, tag: String?, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, 4320 + (id and 0xff),
            Intent(context, AlertPauseReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TAG, tag),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
