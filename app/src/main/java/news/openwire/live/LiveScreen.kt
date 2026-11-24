// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.live

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import news.openwire.R
import news.openwire.data.remote.RadioBrowserSource
import news.openwire.domain.engine.Languages
import news.openwire.domain.live.Channel
import news.openwire.domain.live.LiveCatalog
import news.openwire.domain.live.Station
import news.openwire.i18n.tr
import news.openwire.ui.home.SourceLogo
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.settings.findActivity
import news.openwire.ui.theme.Glass

/**
 * The Live page's own look: a broadcast control room. Night is deep ink with a red on-air
 * light and a cyan signal; day is warm newsprint with the same red and a deep teal. Both
 * keep text at strong contrast.
 */
private data class LivePalette(
    val bg: Color, val surface: Color, val surfaceHi: Color, val text: Color, val dim: Color,
    val red: Color, val signal: Color, val line: Color, val onRed: Color = Color.White,
)

private val Night = LivePalette(
    bg = Color(0xFF070B14), surface = Color(0xFF101828), surfaceHi = Color(0xFF1A2540),
    text = Color(0xFFF2F5FA), dim = Color(0xFF9AA6BC), red = Color(0xFFFF3B3B), signal = Color(0xFF38D6E8),
    line = Color(0x24FFFFFF),
)
private val Day = LivePalette(
    bg = Color(0xFFF4EFE5), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFEAE3D5),
    text = Color(0xFF141820), dim = Color(0xFF545C6B), red = Color(0xFFCC1F28), signal = Color(0xFF006F7E),
    line = Color(0x22000000),
)

private enum class Band { TV, RADIO }

@Composable
fun LiveScreen(
    padding: PaddingValues,
    live: LiveController,
    radio: RadioBrowserSource,
    appLanguage: String,
    country: String,
) {
    val c = if (Glass.isDark) Night else Day
    val context = LocalContext.current
    LaunchedEffect(Unit) { live.connect() }
    val now by live.now.collectAsState()
    val player by live.player.collectAsState()
    var band by rememberSaveable { mutableStateOf(if (LiveBand.take() == "RADIO") Band.RADIO else Band.TV) }
    var lang by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searched by rememberSaveable { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }

    val channels = remember(appLanguage, lang) {
        LiveCatalog.channels(appLanguage).filter { lang == null || it.language == lang }
    }
    val stations = remember(appLanguage, lang) {
        LiveCatalog.ordered(LiveCatalog.STATIONS, appLanguage) { it.language }.filter { lang == null || it.language == lang }
    }
    val local by produceState(emptyList<Station>(), country) { value = radio.country(country) }
    val results by produceState(emptyList<Station>(), searched) { value = radio.search(searched) }
    val languages = remember { (LiveCatalog.CHANNELS.map { it.language } + LiveCatalog.STATIONS.mapNotNull { it.language }).distinct() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            full("head") { Header(c, band) { band = it } }
            full("langs") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(c, tr(R.string.live_all), lang == null) { lang = null }
                    LiveCatalog.ordered(languages, appLanguage) { it }.forEach { l ->
                        Pill(c, Languages.of(l)?.nativeName ?: l, lang == l) { lang = l }
                    }
                }
            }
            if (band == Band.TV) {
                val current = now?.takeIf { it.isVideo }
                if (current != null) full("player") {
                    VideoPanel(c, current, if (fullscreen) null else player, onClose = { live.stop() }, onFullscreen = { fullscreen = true })
                }
                items(channels, key = { it.id }) { ch ->
                    ChannelTile(c, ch, selected = current?.id == ch.id) {
                        if (ch.playsInApp) live.play(ch)
                        else ch.webUrl?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                    }
                }
                full("note") { Note(c, tr(R.string.live_tv_note)) }
            } else {
                val current = now?.takeIf { !it.isVideo }
                if (current != null) full("nowradio") { RadioNow(c, current, onToggle = { live.toggle() }, onStop = { live.stop() }) }
                full("newsradio") { Section(c, tr(R.string.live_news_radio)) }
                stationRows(c, "n", stations, current) { live.play(it) }
                if (local.isNotEmpty()) {
                    full("local") { Section(c, tr(R.string.live_stations_in, java.util.Locale("", country).getDisplayCountry(java.util.Locale(appLanguage)))) }
                    stationRows(c, "l", local.take(20), current) { live.play(it) }
                }
                full("search") {
                    SearchBox(c, query, { query = it }) { searched = query }
                }
                if (searched.isNotBlank()) {
                    if (results.isEmpty()) full("none") { Note(c, tr(R.string.live_no_stations)) }
                    stationRows(c, "s", results, current) { live.play(it) }
                }
                full("rbnote") { Note(c, tr(R.string.live_radio_note)) }
            }
        }
    }

    if (fullscreen && player != null) FullscreenVideo(player!!) { fullscreen = false }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.stationRows(c: LivePalette, prefix: String, list: List<Station>, current: NowLive?, onPlay: (Station) -> Unit) {
    items(list, key = { prefix + it.id }, span = { GridItemSpan(maxLineSpan) }) { st ->
        StationRow(c, st, playing = current?.id == st.id && current.playing) { onPlay(st) }
    }
}

@Composable
private fun Header(c: LivePalette, band: Band, onBand: (Band) -> Unit) {
    val pulse by rememberInfiniteTransition(label = "onair").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tally")
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).alpha(pulse).clip(CircleShape).background(c.red))
            Spacer(Modifier.width(8.dp))
            Text(tr(R.string.live_kicker), style = mono(11.sp, FontWeight.SemiBold, 0.2.em), color = c.red)
        }
        Spacer(Modifier.height(6.dp))
        Text(tr(R.string.tab_live), style = serif(40.sp, FontWeight.Bold, lineHeight = 44.sp), color = c.text)
        Text(tr(R.string.live_sub), style = sans(14.sp, lineHeight = 20.sp), color = c.dim)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape).padding(4.dp),
        ) {
            BandButton(c, Icons.Outlined.Tv, tr(R.string.live_tv), band == Band.TV) { onBand(Band.TV) }
            BandButton(c, Icons.Outlined.Radio, tr(R.string.live_radio), band == Band.RADIO) { onBand(Band.RADIO) }
        }
    }
}

@Composable
private fun BandButton(c: LivePalette, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(if (on) c.red else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) c.onRed else c.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = sans(14.sp, FontWeight.SemiBold), color = if (on) c.onRed else c.text)
    }
}

@Composable
private fun Pill(c: LivePalette, label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = sans(13.sp, if (on) FontWeight.SemiBold else FontWeight.Medium),
        color = if (on) c.bg else c.text,
        modifier = Modifier.clip(CircleShape).background(if (on) c.text else c.surface).border(1.dp, c.line, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun Section(c: LivePalette, title: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(16.dp).height(2.dp).background(c.signal))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.16.em), color = c.signal)
    }
}

@Composable
private fun Note(c: LivePalette, text: String) =
    Text(text, style = sans(11.sp, lineHeight = 16.sp), color = c.dim, modifier = Modifier.padding(vertical = 8.dp))

@Composable
private fun LiveBadge(c: LivePalette) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(c.red).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(c.onRed))
        Spacer(Modifier.width(4.dp))
        Text(tr(R.string.live), style = mono(9.sp, FontWeight.Bold, 0.1.em), color = c.onRed)
    }
}

@Composable
private fun ChannelTile(c: LivePalette, ch: Channel, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.red else c.line), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        ChannelThumbnail(c, ch, selected)
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(ch.name, style = sans(14.sp, FontWeight.SemiBold, lineHeight = 18.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Languages.of(ch.language)?.nativeName ?: ch.language, style = sans(11.sp), color = c.dim)
        }
    }
}

/**
 * A channel's thumbnail: its high-resolution logo filling the frame (over a soft, enlarged copy
 * of itself, so any shape fills 16:9), a half-dark veil for depth that still lets the logo read,
 * the LIVE STREAM sticker top right, and the play mark in the middle on a soft shadow.
 */
@Composable
private fun ChannelThumbnail(c: LivePalette, ch: Channel, selected: Boolean) {
    val logos = news.openwire.ui.home.LocalLogoResolver.current
    val fallback = news.openwire.ui.home.faviconUrl(ch.domain, 256)
    val logo by produceState<String?>(null, ch.domain) { value = logos?.logo(ch.domain) ?: fallback }
    var src by remember(logo) { mutableStateOf(logo) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Brush.verticalGradient(listOf(c.surfaceHi, c.surface))),
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth
        val l = src
        if (l == null) {
            news.openwire.ui.home.SourceLogo(ch.domain, ch.name, size = 46.dp)
        } else {
            val onError: (coil.compose.AsyncImagePainter.State.Error) -> Unit = { if (src != fallback) src = fallback else src = null }
            // Brand-coloured backdrop: the logo, enlarged and softened.
            coil.compose.AsyncImage(
                model = l, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, onError = onError,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f; alpha = .85f }
                    .blur(36.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
            )
            // The logo itself, as large as the frame allows.
            coil.compose.AsyncImage(
                model = l, contentDescription = ch.name, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(vertical = w * .07f, horizontal = w * .12f),
            )
        }
        // Half-dark veil: depth without hiding the logo.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .38f), Color.Black.copy(alpha = .52f)))))
        // LIVE STREAM, top right.
        Image(
            painterResource(R.drawable.live_sticker), contentDescription = tr(R.string.live),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = w * .05f, end = w * .04f).width(w * .3f),
        )
        // External channels say where they open.
        if (!ch.playsInApp) Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(4.dp)).background(c.bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("YouTube", style = mono(9.sp, FontWeight.SemiBold), color = c.text)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = c.text, modifier = Modifier.size(10.dp))
        }
        // The play mark on a soft drop shadow (blurred on Android 12+, a faint offset copy below).
        val play = w * (if (selected) .2f else .17f)
        Box(Modifier.size(play * 1.6f), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.live_play), contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black),
                modifier = Modifier.height(play).graphicsLayer { translationY = 2.dp.toPx(); alpha = if (android.os.Build.VERSION.SDK_INT >= 31) .6f else .3f }
                    .blur(7.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded),
            )
            Image(painterResource(R.drawable.live_play), contentDescription = tr(R.string.live_play_channel, ch.name), modifier = Modifier.height(play))
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPanel(c: LivePalette, now: NowLive, player: Player?, onClose: () -> Unit, onFullscreen: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black).border(1.dp, c.line, RoundedCornerShape(20.dp))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { setShowNextButton(false); setShowPreviousButton(false); setShowRewindButton(false); setShowFastForwardButton(false) } },
                update = { v ->
                    v.player = player
                    v.setFullscreenButtonClickListener { onFullscreen() }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveBadge(c)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(now.title, style = sans(14.sp, FontWeight.SemiBold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when { now.error != null -> tr(R.string.live_unavailable); now.buffering -> tr(R.string.live_tuning); else -> tr(R.string.live_on_now) },
                    style = sans(11.sp), color = if (now.error != null) c.red else c.dim,
                )
            }
            Icon(
                Icons.Filled.Close, tr(R.string.live_stop), tint = c.text,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp).size(20.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideo(player: Player, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = before ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK); setShowNextButton(false); setShowPreviousButton(false) } },
            update = { v -> v.player = player; v.setFullscreenButtonState(true); v.setFullscreenButtonClickListener { onExit() } },
            modifier = Modifier.fillMaxSize().background(Color.Black),
        )
    }
}

@Composable
private fun RadioNow(c: LivePalette, now: NowLive, onToggle: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(c.surfaceHi, c.surface)))
            .border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Equalizer(c, now.playing && !now.buffering)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.live_now_playing).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = c.red)
            Text(now.title, style = serif(20.sp, FontWeight.Bold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now.error != null || now.buffering) Text(
                if (now.error != null) tr(R.string.live_unavailable) else tr(R.string.live_tuning),
                style = sans(11.sp), color = if (now.error != null) c.red else c.dim,
            )
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(c.red).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) { Icon(if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, tr(if (now.playing) R.string.action_pause else R.string.live_play), tint = c.onRed) }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.Close, tr(R.string.live_stop), tint = c.dim, modifier = Modifier.clip(CircleShape).clickable(onClick = onStop).padding(10.dp).size(20.dp))
    }
}

@Composable
private fun Equalizer(c: LivePalette, active: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(30.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(520, 380, 640, 450).forEachIndexed { i, ms ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar$i")
            Box(Modifier.width(4.dp).fillMaxHeight(if (active) h else 0.25f).clip(RoundedCornerShape(2.dp)).background(c.signal))
        }
    }
}

@Composable
private fun StationRow(c: LivePalette, st: Station, playing: Boolean, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, if (playing) c.red else c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(c, st)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(st.name, style = sans(14.sp, FontWeight.SemiBold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(st.language?.let { Languages.of(it)?.nativeName }, st.tags.take(2).joinToString(" · ").ifBlank { null }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = sans(11.sp), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (playing) c.red else c.surfaceHi),
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = if (playing) c.onRed else c.text, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SearchBox(c: LivePalette, value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(tr(R.string.live_search_hint), style = sans(14.sp), color = c.dim)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                cursorBrush = SolidColor(c.red),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The station's own icon (from the directory), its site's logo, or a lettered badge. */
@Composable
private fun StationLogo(c: LivePalette, st: Station) {
    val size = 34.dp
    when {
        st.favicon != null -> coil.compose.SubcomposeAsyncImage(
            model = st.favicon, contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(c.surfaceHi),
            error = { Monogram(c, st.name, size) },
        )
        st.domain != null && st.id.startsWith("rb_").not() -> SourceLogo(st.domain, st.name, size = size)
        else -> Monogram(c, st.name, size)
    }
}

@Composable
private fun Monogram(c: LivePalette, name: String, size: Dp) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(c.red, c.signal))),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = sans(15.sp, FontWeight.Bold), color = Color.White) }
}

/** A band to open the Live page on (from a deep link), used once. */
object LiveBand {
    @Volatile var requested: String? = null
    fun take(): String? = requested.also { requested = null }
}
