// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import news.openwire.R
import news.openwire.data.remote.ArticleMedia
import news.openwire.i18n.tr
import news.openwire.ui.theme.Glass

private val MediaShape = RoundedCornerShape(20.dp)

/** A photo or video from inside the story, in the reader's flow. */
@Composable
internal fun StoryMedia(m: ArticleMedia) {
    var failed by remember(m.url) { mutableStateOf(false) }
    if (failed) return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        when (m.kind) {
            ArticleMedia.Kind.IMAGE -> StoryPhoto(m) { failed = true }
            ArticleMedia.Kind.VIDEO -> StoryVideo(m)
            ArticleMedia.Kind.EXTERNAL -> ExternalVideo(m)
        }
        m.caption?.let {
            Text(it, style = sans(12.sp, lineHeight = 18.sp), color = Glass.textSecondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
        }
    }
}

@Composable
private fun StoryPhoto(m: ArticleMedia, onError: () -> Unit) {
    ZoomablePhoto(m.url, m.caption, Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(MediaShape).border(1.dp, Glass.hairline, MediaShape), ContentScale.FillWidth, onError)
}

/** A photo that opens full screen when tapped. */
@Composable
internal fun ZoomablePhoto(url: String, description: String?, modifier: Modifier, scale: ContentScale = ContentScale.Crop, onError: () -> Unit = {}) {
    var zoomed by remember { mutableStateOf(false) }
    AsyncImage(model = url, contentDescription = description, contentScale = scale, onError = { onError() }, modifier = modifier.clickable { zoomed = true })
    if (zoomed) {
        Dialog(onDismissRequest = { zoomed = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { zoomed = false }, contentAlignment = Alignment.Center) {
                AsyncImage(model = url, contentDescription = description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StoryVideo(m: ArticleMedia) {
    InlineVideo(m.url, m.poster, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MediaShape).border(1.dp, Glass.hairline, MediaShape))
}

/** A poster with a play button; tapped, the video plays right there (mp4 or HLS). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun InlineVideo(url: String, poster: String?, modifier: Modifier) {
    var playing by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Color.Black)) {
        if (playing) {
            val context = LocalContext.current
            val player = remember(url) {
                ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { setShowNextButton(false); setShowPreviousButton(false) } },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            poster?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().clickable { playing = true }, contentAlignment = Alignment.Center) { PlayBadge(Modifier) }
        }
    }
}

/** YouTube and Vimeo play in their own app or page. */
@Composable
private fun ExternalVideo(m: ArticleMedia) {
    val context = LocalContext.current
    val site = if ("vimeo.com" in m.url) "Vimeo" else "YouTube"
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MediaShape).background(Glass.chipFill).border(1.dp, Glass.hairline, MediaShape)
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
    ) {
        m.poster?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        PlayBadge(Modifier.align(Alignment.Center))
        Row(
            Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(R.string.reader_watch_on, site), style = mono(10.sp, FontWeight.SemiBold, 0.06.em), color = Color.White)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun PlayBadge(modifier: Modifier) {
    Box(modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, tr(R.string.reader_play_video), tint = Color.White, modifier = Modifier.size(36.dp))
    }
}
