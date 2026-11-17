// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.live

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import news.openwire.MainActivity
import news.openwire.domain.live.Channel
import news.openwire.domain.live.Station

/**
 * Plays live TV and radio. Keeps going with the screen off or the app in the background,
 * with controls in the notification and on the lock screen.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LivePlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // Takes audio focus (the audio briefing pauses, and this pauses for it in turn).
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val open = PendingIntent.getActivity(
            this, 4700,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "LIVE").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).setId("editrove-live").build()
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).build()
                .apply { setSmallIcon(news.openwire.notifications.R.drawable.ic_stat_openwire) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** What's on now. */
data class NowLive(
    val id: String,
    val title: String,
    val isVideo: Boolean,
    val playing: Boolean,
    val buffering: Boolean,
    val error: String? = null,
)

/** The app's handle on the live player (connects to [LivePlaybackService] on first use). */
class LiveController(context: Context, private val beforePlay: () -> Unit = {}) {
    private val app = context.applicationContext
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _now = MutableStateFlow<NowLive?>(null)
    val now: StateFlow<NowLive?> = _now.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    /** The player for video surfaces (null until connected). */
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
        override fun onPlayerError(error: PlaybackException) {
            _now.value = _now.value?.copy(playing = false, buffering = false, error = error.errorCodeName)
        }
    }

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        if (item == null) { _now.value = null; return }
        _now.value = NowLive(
            id = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            isVideo = item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO,
            playing = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING),
            buffering = p.playbackState == Player.STATE_BUFFERING,
            error = p.playerError?.errorCodeName,
        )
    }

    /** Connects now (so a page can show what's already playing). */
    fun connect() = withController { }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending += block
        if (connecting) return
        connecting = true
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, LivePlaybackService::class.java))).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            _player.value = c
            publish(c)
            pending.toList().forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    fun play(channel: Channel) {
        val url = channel.stream ?: return
        start(channel.id, channel.name, url, video = true, hls = true)
    }

    fun play(station: Station) =
        start(station.id, station.name, station.url, video = false, hls = station.url.contains(".m3u8"), artwork = station.favicon)

    private fun start(id: String, title: String, url: String, video: Boolean, hls: Boolean, artwork: String? = null) {
        beforePlay()
        _now.value = NowLive(id, title, video, playing = true, buffering = true)
        withController { c ->
            val meta = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(if (video) "Editrove · Live TV" else "Editrove · Radio")
                .setMediaType(if (video) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setIsPlayable(true)
                .apply { artwork?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                .build()
            val item = MediaItem.Builder().setMediaId(id).setUri(url).setMediaMetadata(meta)
                .apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(12_000).build())
                .build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            beforePlay()
            // A live stream resumes at the live edge, not where it was paused.
            if (c.isCurrentMediaItemLive) c.seekToDefaultPosition()
            c.prepare(); c.play()
        }
    }

    fun stop() = withController { c -> c.stop(); c.clearMediaItems(); _now.value = null }

    /** Pauses video when its page goes away (radio keeps playing). */
    fun pauseVideo() { if (_now.value?.isVideo == true) controller?.pause() }
}
