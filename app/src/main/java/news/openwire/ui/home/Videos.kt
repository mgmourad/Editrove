// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import news.openwire.R
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.video.ShortVideo
import news.openwire.i18n.tr
import news.openwire.ui.theme.Glass

/** Opens the video player on a list at a position (provided by the home screen). */
val LocalPlayVideo = compositionLocalOf<(List<ShortVideo>, Int) -> Unit> { { _, _ -> } }

/**
 * "Video moments": tall 9:16 cards of news Shorts for the current tab, the headline set over the
 * picture and the channel at the foot, swiped sideways.
 */
@Composable
fun VideoMoments(videos: List<ShortVideo>, compact: Boolean) {
    if (videos.isEmpty()) return
    val play = LocalPlayVideo.current
    val g = Glass
    HighlightSection(VideoGlow) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.video_moments), style = serif(24.sp, FontWeight.Bold), color = g.textPrimary)
                Text(tr(R.string.video_moments_sub), style = sans(12.sp), color = g.textSecondary)
            }
            Text(tr(R.string.video_via_youtube), style = mono(9.sp, tracking = 0.08.em), color = g.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        val w = if (compact) 156.dp else 184.dp
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
                Box(
                    Modifier.width(w).aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))
                        .background(g.chipFill).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(20.dp))
                        .clickable { play(videos, i) },
                ) {
                    AsyncImage(v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .45f to Color.Transparent, 1f to Color.Black.copy(alpha = .82f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(
                            v.title, maxLines = 4, overflow = TextOverflow.Ellipsis,
                            style = sans(14.sp, FontWeight.Bold, lineHeight = 18.sp).copy(shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 6f)),
                            color = Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(v.channelAvatar, contentDescription = null, modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White))
                            Spacer(Modifier.width(6.dp))
                            Text(v.channel, style = sans(11.5.sp, FontWeight.Medium), color = Color.White.copy(alpha = .9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Full-screen vertical player: one Short per screen, swipe up for the next. Plays through
 * YouTube's privacy-enhanced embed (youtube-nocookie.com); Editrove's own controls on the right
 * (sound, love, save, share). Only the Short on screen holds a player.
 */
@Composable
fun ShortsPlayer(
    videos: List<ShortVideo>,
    start: Int,
    liked: Set<String>,
    saved: Set<String>,
    onToggleLiked: (ShortVideo) -> Unit,
    onToggleSaved: (ShortVideo) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        var muted by rememberSaveable { mutableStateOf(false) }
        val pager = rememberPagerState(initialPage = start) { videos.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(state = pager, beyondViewportPageCount = 0, key = { videos[it].id }) { page ->
                val v = videos[page]
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (pager.settledPage == page) ShortEmbed(v.id, muted, Modifier.fillMaxSize())
                    // The story line and channel, bottom left.
                    Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)))))
                    Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 88.dp, bottom = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(v.channelAvatar, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(v.channel, style = sans(15.sp, FontWeight.SemiBold), color = Color.White)
                                Text(NewsProcessor.formatRelativeTime(v.publishedAt), style = sans(11.sp), color = Color.White.copy(alpha = .7f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(v.title, style = sans(16.sp, FontWeight.Medium, lineHeight = 22.sp), color = Color.White, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    // Editrove's controls, right side.
                    Column(
                        Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val context = LocalContext.current
                        val a = remember(v.id) { v.asArticle() }
                        RailButton(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, tr(if (muted) R.string.video_unmute else R.string.video_mute), false) { muted = !muted }
                        RailButton(if (a.id in liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, tr(R.string.video_love), a.id in liked) { onToggleLiked(v) }
                        RailButton(if (a.id in saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, tr(R.string.video_save), a.id in saved) { onToggleSaved(v) }
                        RailButton(Icons.Outlined.Share, tr(R.string.video_share), false) {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_SUBJECT, v.title).putExtra(android.content.Intent.EXTRA_TEXT, "${v.title}\n${v.url}")
                            context.startActivity(android.content.Intent.createChooser(send, null))
                        }
                    }
                }
            }
            // Back, top left; the source of the stream, top right — on a dark band that fades into the
            // picture, so it reads over any video (and covers YouTube's own title strip).
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .85f), .55f to Color.Black.copy(alpha = .55f), 1f to Color.Transparent))
                    .statusBarsPadding().height(96.dp),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.reader_back), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text(tr(R.string.video_via_youtube), style = mono(10.sp, tracking = 0.08.em), color = Color.White.copy(alpha = .7f))
            }
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape).background(Color.Black.copy(alpha = .38f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) Glass.accent else Color.White, modifier = Modifier.size(26.dp)) }
}

/**
 * One Short through YouTube's privacy-enhanced embed (no cookies until played). Autoplays,
 * loops, no YouTube controls (Editrove's are on top); sound follows [muted].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ShortEmbed(id: String, muted: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                val html = """
                    <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head>
                    <body><iframe id="p" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen
                    src="https://www.youtube-nocookie.com/embed/$id?autoplay=1&playsinline=1&controls=0&loop=1&playlist=$id&rel=0&modestbranding=1&iv_load_policy=3&enablejsapi=1&mute=${if (muted) 1 else 0}"></iframe>
                    <script>function cmd(f){var p=document.getElementById('p');p&&p.contentWindow.postMessage(JSON.stringify({event:'command',func:f,args:[]}),'*')}</script>
                    </body></html>
                """.trimIndent()
                // A real https origin (Editrove's own page) so YouTube accepts the embed.
                loadDataWithBaseURL("https://mgmourad.github.io/Editrove/", html, "text/html", "utf-8", null)
                tag = muted
            }
        },
        update = { w ->
            if (w.tag != muted) { w.tag = muted; w.evaluateJavascript("cmd('${if (muted) "mute" else "unMute"}')", null) }
        },
        onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() },
        modifier = modifier,
    )
}
