// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.R

/**
 * Keeps the audio briefing playing in the background and on the lock screen.
 *
 * A media-playback foreground service owns a [MediaSession] (lock screen, notification
 * shade, Bluetooth/headset buttons, Android Auto-style controllers) with the Editrove
 * art, the current headline, and previous / play-pause / next / stop. Previous and next
 * move between stories at whatever detail level the briefing uses.
 */
class BriefingService : Service() {

    private val player get() = (application as OpenWireApp).container.briefingPlayer
    private lateinit var session: MediaSession
    private var wake: PowerManager.WakeLock? = null
    private var foreground = false
    private val art: Bitmap? by lazy { BitmapFactory.decodeResource(resources, R.drawable.briefing_art) }

    private val noisy = object : BroadcastReceiver() {
        // Headphones unplugged / Bluetooth dropped: pause rather than blare from the speaker.
        override fun onReceive(context: Context, intent: Intent) { if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) player.pause() }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Audio briefing", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Playback controls for the audio briefing"
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "EditroveBriefing").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = player.resume()
                override fun onPause() = player.pause()
                override fun onSkipToNext() = player.skipNext()
                override fun onSkipToPrevious() = player.skipPrevious()
                override fun onStop() = player.stop()
            })
            setSessionActivity(openApp())
            isActive = true
        }
        player.onChange = { update() }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_NEXT -> player.skipNext()
            ACTION_PREV -> player.skipPrevious()
            ACTION_STOP -> player.stop()
        }
        update()
        return START_NOT_STICKY
    }

    private fun update() {
        val p = player
        if (!p.active) {
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            session.isActive = false
            stopSelf()
            return
        }
        session.isActive = true
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, p.storyTitle ?: p.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Editrove · ${p.progress}")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, p.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, p.progress)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (p.storyIndex + 1).coerceAtLeast(0).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, p.storyCount.toLong())
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(if (p.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        val n = notification(p)
        if (p.playing) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
            foreground = true
            acquireWake()
        } else {
            // Paused: keep the controls, but let the user swipe them away.
            if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
            getSystemService(NotificationManager::class.java)?.notify(ID, n)
            releaseWake()
        }
    }

    private fun notification(p: BriefingPlayer): Notification {
        fun action(icon: Int, label: String, act: String) = Notification.Action.Builder(Icon.createWithResource(this, icon), label, service(act)).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(news.openwire.notifications.R.drawable.ic_stat_openwire)
            .setLargeIcon(art)
            .setContentTitle(p.storyTitle ?: p.title)
            .setContentText("Editrove · ${p.progress}")
            .setSubText(p.title)
            .setContentIntent(openApp())
            .setDeleteIntent(service(ACTION_STOP))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(p.playing)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous story", ACTION_PREV))
            .addAction(
                if (p.playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE),
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next story", ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun service(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, BriefingService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this, 7, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, NavTab.AUDIO.name).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Keeps the CPU awake to sequence speech, cues and stings while the screen is off. */
    private fun acquireWake() {
        if (wake?.isHeld == true) return
        wake = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Editrove:briefing")?.apply { acquire(3 * 60 * 60 * 1000L) }
    }

    private fun releaseWake() {
        wake?.takeIf { it.isHeld }?.release()
        wake = null
    }

    override fun onDestroy() {
        running = false
        player.onChange = null
        runCatching { unregisterReceiver(noisy) }
        releaseWake()
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "briefing"
        private const val ID = 4201
        private const val ACTION_TOGGLE = "news.editrove.briefing.TOGGLE"
        private const val ACTION_NEXT = "news.editrove.briefing.NEXT"
        private const val ACTION_PREV = "news.editrove.briefing.PREV"
        private const val ACTION_STOP = "news.editrove.briefing.STOP"

        @Volatile private var running = false

        /** Starts the playback service if it isn't already running (it then follows the player on its own). */
        fun start(context: Context) {
            if (running) return
            runCatching { context.startForegroundService(Intent(context, BriefingService::class.java)) }
        }
    }
}
