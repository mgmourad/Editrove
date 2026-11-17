// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import news.openwire.i18n.tr
import news.openwire.R
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import news.openwire.data.remote.MarketDataSource
import news.openwire.domain.market.MarketQuote
import news.openwire.domain.market.Markets
import news.openwire.ui.theme.Glass

/** Market quotes for the Business tab. */
val LocalMarkets = staticCompositionLocalOf<MarketDataSource?> { null }

private val Up = Color(0xFF34D399)
private val Down = Color(0xFFFB7185)

/**
 * Business tab's second tracker bar: a market watch under the LIVE ticker. Tapping anywhere
 * opens the market popup (or Yahoo Finance when quotes can't be loaded).
 */
@Composable
fun MarketBar() {
    val g = Glass
    val source = LocalMarkets.current ?: return
    val context = LocalContext.current
    var quotes by remember { mutableStateOf<List<MarketQuote>?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (isActive) { quotes = source.quotes(); delay(60_000) }
    }
    val list = quotes
    val listState = rememberLazyListState()
    // Slow drift like the LIVE ticker, resting after half a minute.
    LaunchedEffect(list?.size) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        val until = System.currentTimeMillis() + 30_000
        while (isActive && System.currentTimeMillis() < until) {
            delay(32)
            runCatching { if (listState.canScrollForward) listState.scrollBy(1.0f) else { delay(1500); listState.scrollToItem(0) } }
        }
    }
    GlassCard(
        onClick = {
            if (list.isNullOrEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Markets.YAHOO_MARKETS)))
            else open = true
        },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).background(Up.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(tr(R.string.markets), style = mono(10.sp, FontWeight.SemiBold), color = Up) }
            Spacer(Modifier.width(10.dp))
            if (list == null) Text(tr(R.string.markets_loading), style = sans(12.sp), color = g.textSecondary)
            else if (list.isEmpty()) Text(tr(R.string.markets_unavailable), style = sans(12.sp), color = g.textSecondary)
            else LazyRow(state = listState, modifier = Modifier.weight(1f), userScrollEnabled = false, verticalAlignment = Alignment.CenterVertically) {
                items(list, key = { it.symbol }) { q ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                        Text(q.label.uppercase(), style = mono(11.sp, FontWeight.SemiBold), color = g.textPrimary.copy(alpha = 0.85f))
                        Spacer(Modifier.width(6.dp))
                        Text(Markets.formatPrice(q), style = sans(12.sp, FontWeight.Medium), color = g.textPrimary)
                        Spacer(Modifier.width(5.dp))
                        Text((if (q.up) "▲ " else "▼ ") + Markets.formatChange(q), style = mono(11.sp, FontWeight.SemiBold), color = if (q.up) Up else Down)
                    }
                }
            }
        }
    }
    if (open && list != null) MarketSheet(list, source, onClose = { open = false })
}

@Composable
private fun MarketSheet(initial: List<MarketQuote>, source: MarketDataSource, onClose: () -> Unit) {
    val g = Glass
    val context = LocalContext.current
    var quotes by remember { mutableStateOf(initial) }
    var updated by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) { delay(30_000); source.quotes(maxAgeMs = 0).takeIf { it.isNotEmpty() }?.let { quotes = it; updated = System.currentTimeMillis() } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(g.canvas).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.market_watch), style = mono(10.sp, FontWeight.SemiBold), color = Up)
                    Text(tr(R.string.markets_today), style = serif(24.sp), color = g.textPrimary)
                }
                Text(tr(R.string.action_close), style = sans(13.sp, FontWeight.SemiBold), color = g.accentSoft,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                tr(R.string.markets_updated, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(updated))),
                style = sans(11.sp), color = g.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(quotes, key = { it.symbol }) { q ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Markets.quotePage(q.symbol)))) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(q.label, style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                            val lo = q.dayLow; val hi = q.dayHigh
                            val range = if (lo != null && hi != null) tr(R.string.markets_day_range, fmt(lo), fmt(hi)) else q.name
                            Text(range, style = mono(10.sp), color = g.textSecondary)
                        }
                        Sparkline(q, Modifier.width(72.dp).height(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
                            Text(Markets.formatPrice(q), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                            Text(
                                String.format(java.util.Locale.US, "%+.2f", q.change) + "  " + Markets.formatChange(q),
                                style = mono(10.sp, FontWeight.SemiBold), color = if (q.up) Up else Down,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).background(g.chipFill)
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Markets.YAHOO_MARKETS))) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr(R.string.markets_open_yahoo), style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary) }
            Spacer(Modifier.height(8.dp))
            Text(tr(R.string.markets_disclaimer), style = sans(10.sp), color = g.textMuted)
        }
    }
}

private fun fmt(v: Double) = String.format(java.util.Locale.US, if (v >= 1000) "%,.0f" else "%,.2f", v)

@Composable
private fun Sparkline(q: MarketQuote, modifier: Modifier) {
    val pts = q.series
    if (pts.size < 2) { Spacer(modifier); return }
    val color = if (q.up) Up else Down
    Canvas(modifier) {
        val lo = minOf(pts.min(), q.previousClose); val hi = maxOf(pts.max(), q.previousClose)
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = size.height - ((v - lo) / span * size.height).toFloat()
        val base = y(q.previousClose)
        drawLine(color.copy(alpha = 0.25f), Offset(0f, base), Offset(size.width, base), strokeWidth = 1f)
        val path = Path()
        pts.forEachIndexed { i, v -> val x = size.width * i / (pts.size - 1); if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
}
