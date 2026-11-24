// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import news.openwire.R
import news.openwire.data.repository.RefreshTicker
import news.openwire.data.repository.SourceRef
import news.openwire.domain.motion.PullEvent
import news.openwire.domain.motion.PullMachine
import news.openwire.domain.motion.PullPhase
import news.openwire.domain.motion.PullPhysics
import news.openwire.domain.motion.PullState
import news.openwire.i18n.tr
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400
import news.openwire.ui.theme.rememberReduceMotion

/** How far the current pull is towards ready (0–1): the masthead's one "ink" detail reads it. */
val LocalPullInk = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** No-overshoot spring for the band (settle, cancel and close). */
private val BandSpring = spring<Float>(dampingRatio = 1f, stiffness = 300f)
private val GlintEasing = CubicBezierEasing(.65f, 0f, .35f, 1f)

@Stable
private class RuleState {
    var pull by mutableStateOf(PullState())
    /** The band's height (dp): read only in layout and draw. */
    val offset: MutableFloatState = mutableFloatStateOf(0f)
    /** 0–1: the rule is solid (ready, and through the refresh). */
    val solid = Animatable(0f)
    /** 0–1: the finish flash. */
    val flash = Animatable(0f)
    /** 1 → 0: the rule pulling back to the centre at the finish. */
    val span = Animatable(1f)
    var dragging = false
    var sawRefresh = false
}

/**
 * Pull to refresh, "the rule and the wire": resistance 140 × (1 − e^(−d/160)) dp; at 72 dp the
 * rule reaches both edges and ticks once; release holds at 64 dp with no bounce while each source
 * reports in; the finish flashes the rule and closes the band. Replaces Material's spinner.
 *
 * @param topInset where the band opens (the top of the scrolling content, under the status bar).
 */
@Composable
fun RuleRefreshBox(
    refreshing: Boolean,
    ticker: kotlinx.coroutines.flow.StateFlow<RefreshTicker>,
    newCount: () -> Int,
    onRefresh: () -> Unit,
    topInset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val st = remember { RuleState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val reduce = rememberReduceMotion()

    fun settleTo(target: Float) = scope.launch {
        if (reduce) { st.offset.floatValue = target; return@launch }
        animate(st.offset.floatValue, target, animationSpec = BandSpring) { v, _ -> st.offset.floatValue = v }
    }

    fun send(e: PullEvent) {
        val before = st.pull.phase
        val next = PullMachine.reduce(st.pull, e)
        st.pull = next
        if (next.tick) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
        if (st.dragging && e is PullEvent.Drag) st.offset.floatValue = next.offset
        when {
            next.phase == PullPhase.REFRESHING && before != PullPhase.REFRESHING -> {
                st.sawRefresh = false
                settleTo(PullPhysics.HOLD)
                scope.launch { st.span.snapTo(1f); st.flash.snapTo(0f); if (reduce) st.solid.snapTo(1f) else st.solid.animateTo(1f, tween(180)) }
                if (e == PullEvent.Release) onRefresh()
            }
            next.phase == PullPhase.IDLE && before != PullPhase.IDLE && e == PullEvent.Release -> {
                settleTo(0f)
                scope.launch { st.solid.snapTo(0f) }
            }
            next.phase == PullPhase.READY && before != PullPhase.READY -> scope.launch { if (reduce) st.solid.snapTo(1f) else st.solid.animateTo(1f, tween(160)) }
            before == PullPhase.READY && next.phase == PullPhase.PULLING -> scope.launch { if (reduce) st.solid.snapTo(0f) else st.solid.animateTo(0f, tween(160)) }
        }
    }

    // A refresh started elsewhere (the status line) shows the same band; its end closes it.
    LaunchedEffect(refreshing) {
        if (refreshing) {
            st.sawRefresh = true
            if (st.pull.phase != PullPhase.REFRESHING && st.pull.phase != PullPhase.FINISHING) send(PullEvent.RefreshStarted)
        } else if (st.pull.phase == PullPhase.REFRESHING) {
            if (!st.sawRefresh) delay(600) // a refresh that never started (nothing to refresh here)
            if (refreshing) return@LaunchedEffect
            send(PullEvent.RefreshDone)
            // Flash solid rose (~220 ms), pull the rule back to the centre (340 ms) as the band closes.
            if (!reduce) {
                st.flash.animateTo(1f, tween(90))
                st.flash.animateTo(0f, tween(130))
                launch { st.span.animateTo(0f, tween(340, easing = FastOutSlowInEasing)) }
            }
            if (reduce) st.offset.floatValue = 0f
            else animate(st.offset.floatValue, 0f, animationSpec = BandSpring) { v, _ -> st.offset.floatValue = v }
            st.solid.snapTo(0f); st.span.snapTo(1f)
            send(PullEvent.Settled)
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up while the band is open closes it first.
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                val p = st.pull.phase
                if ((p != PullPhase.PULLING && p != PullPhase.READY) || st.pull.drag <= 0f) return Offset.Zero
                st.dragging = true
                val dp = available.y / density.density
                val used = maxOf(dp, -st.pull.drag)
                send(PullEvent.Drag(used))
                return Offset(0f, used * density.density)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                val p = st.pull.phase
                if (p != PullPhase.IDLE && p != PullPhase.PULLING && p != PullPhase.READY) return Offset.Zero
                st.dragging = true
                send(PullEvent.Drag(available.y / density.density))
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val p = st.pull.phase
                val wasPulling = st.dragging && (p == PullPhase.PULLING || p == PullPhase.READY)
                st.dragging = false
                if (!wasPulling) return Velocity.Zero
                send(PullEvent.Release)
                return available
            }
        }
    }

    val ink = remember { androidx.compose.runtime.derivedStateOf { PullPhysics.progress(st.offset.floatValue).takeIf { st.pull.phase.let { it == PullPhase.PULLING || it == PullPhase.READY } } ?: 0f } }
    Box(modifier.nestedScroll(connection)) {
        RuleBand(st, ticker, topInset, reduce)
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = st.offset.floatValue * density.density }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalPullInk provides ink) { content() }
        }
        Announcer(st, ticker, newCount)
    }
}

/** TalkBack: "Refreshing", then "Updated, N new stories" (and anything offline); never the wire line by line. */
@Composable
private fun Announcer(st: RuleState, ticker: kotlinx.coroutines.flow.StateFlow<RefreshTicker>, newCount: () -> Int) {
    val t by ticker.collectAsState()
    val said = when (st.pull.phase) {
        PullPhase.REFRESHING -> tr(R.string.refresh_announce_start)
        PullPhase.FINISHING -> buildString {
            val n = newCount()
            append(if (n > 0) tr(if (n == 1) R.string.refresh_announce_new_one else R.string.refresh_announce_new_many, n) else tr(R.string.refresh_announce_done))
            val off = t.failed.size
            if (off > 0) append(", ").append(tr(if (off > 1) R.string.status_offline_many else R.string.status_offline_one, off))
        }
        else -> ""
    }
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = said })
}

/** The band above the masthead: label, the rule (and its glint), and the wire's one-line ticker. */
@Composable
private fun RuleBand(st: RuleState, tickerFlow: kotlinx.coroutines.flow.StateFlow<RefreshTicker>, topInset: Dp, reduce: Boolean) {
    val g = Glass
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val phase = st.pull.phase
    val refreshing = phase == PullPhase.REFRESHING || phase == PullPhase.FINISHING
    Column(
        Modifier.fillMaxWidth().padding(top = topInset).padding(horizontal = 20.dp)
            // The band is as tall as the pull; everything sits on its bottom edge.
            .layout { m, c ->
                val h = (st.offset.floatValue * density).roundToInt().coerceAtLeast(0)
                val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(p.width, h) { p.place(0, h - p.height) }
            }
            .graphicsLayer { alpha = (st.offset.floatValue / 24f).coerceIn(0f, 1f); clip = false }
            .clearAndSetSemantics { },
    ) {
        // "Pull for the latest edition" ↔ "Release to refresh": crossfade with a 5 dp slide.
        Box(Modifier.fillMaxWidth().height(16.dp)) {
            if (!refreshing) {
                Text(
                    tr(R.string.refresh_pull), style = mono(10.sp, tracking = 0.08.sp), color = g.textMuted, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { val k = st.solid.value; alpha = 1f - k; translationY = -5.dp.toPx() * k },
                )
                Text(
                    tr(R.string.refresh_release), style = mono(10.sp, FontWeight.Medium, tracking = 0.08.sp), color = Rose400, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { val k = st.solid.value; alpha = k; translationY = 5.dp.toPx() * (1f - k) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Rule(st, refreshing, rtl, reduce)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(22.dp)) { if (refreshing) { val ticker by tickerFlow.collectAsState(); Wire(ticker) } }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Rule(st: RuleState, refreshing: Boolean, rtl: Boolean, reduce: Boolean) {
    // The glint: a short bright segment along the dim track, 1250 ms a pass (refresh only).
    val glint = if (refreshing && !reduce) rememberInfiniteTransition(label = "glint")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1250, easing = GlintEasing), RepeatMode.Restart), label = "g") else null
    Spacer(
        Modifier.fillMaxWidth().height(9.dp).drawWithCache {
            val w = size.width; val y = size.height / 2
            val fade = Brush.horizontalGradient(0f to Color.Transparent, .5f to Rose, 1f to Color.Transparent, startX = 0f, endX = w)
            val seg = w * .22f
            val glintBrush = Brush.horizontalGradient(0f to Color.Transparent, .5f to Rose200Glint, 1f to Color.Transparent, startX = 0f, endX = seg)
            val hair = 1.dp.toPx(); val glow1 = 3.dp.toPx(); val glow2 = 7.dp.toPx()
            onDrawBehind {
                val solid = st.solid.value
                val flash = st.flash.value
                // Progress while pulling; full width once ready and through the refresh; then back to the centre.
                val k = if (refreshing) st.span.value else PullPhysics.progress(st.offset.floatValue)
                if (k <= 0f) return@onDrawBehind
                scale(scaleX = k, scaleY = 1f, pivot = Offset(w / 2, y)) {
                    // Soft glow under a solid rule (no blur filter: works the same on every Android).
                    val glowA = (if (refreshing) .35f * flash else solid) * 1f
                    if (glowA > 0f) {
                        drawLine(Rose.copy(alpha = .10f * glowA), Offset(0f, y), Offset(w, y), glow2, StrokeCap.Round)
                        drawLine(Rose.copy(alpha = .22f * glowA), Offset(0f, y), Offset(w, y), glow1, StrokeCap.Round)
                    }
                    if (refreshing) {
                        // The dim track, flashing solid at the finish.
                        drawLine(Rose.copy(alpha = .28f + .72f * flash), Offset(0f, y), Offset(w, y), hair)
                    } else {
                        drawLine(fade, Offset(0f, y), Offset(w, y), hair, alpha = 1f - solid)
                        drawLine(Rose, Offset(0f, y), Offset(w, y), hair, alpha = solid)
                    }
                }
                glint?.let { g ->
                    val p = g.value
                    var x = -seg + (w + seg) * p
                    if (rtl) x = w - seg - x
                    translateGlint(x) { drawLine(glintBrush, Offset(0f, y), Offset(seg, y), 2.dp.toPx(), StrokeCap.Round) }
                }
            }
        },
    )
}

/** The glint's bright core: rose-200, an existing token. */
private val Rose200Glint = news.openwire.ui.theme.Rose200

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translateGlint(x: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    translate(left = x, top = 0f, block = block)

/** One line per source as it answers, rolling up; the live count on the far side. */
@Composable
private fun Wire(ticker: RefreshTicker) {
    val g = Glass
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = ticker.lines.lastOrNull(),
            transitionSpec = {
                (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))) togetherWith
                    (slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "wire",
        ) { line ->
            val text = buildAnnotatedString {
                if (line == null) { withStyle(SpanStyle(color = g.textSecondary)) { append(tr(R.string.refresh_checking)) }; return@buildAnnotatedString }
                val name = when (line.kind) {
                    SourceRef.Kind.SEARCH -> tr(R.string.refresh_news_search)
                    SourceRef.Kind.EDITION -> tr(R.string.refresh_edition)
                    SourceRef.Kind.FEED -> line.name
                }
                if (line.ok) {
                    withStyle(SpanStyle(color = g.textPrimary)) { append(name) }
                    append("  ")
                    withStyle(SpanStyle(color = g.textMuted, fontFamily = news.openwire.ui.theme.JetBrainsMonoFamily, fontSize = 10.sp)) { append(tr(R.string.refresh_ms, line.elapsedMs)) }
                } else {
                    withStyle(SpanStyle(color = Amber400)) { append(name) }
                    append("  ")
                    withStyle(SpanStyle(color = g.textMuted)) { append(tr(R.string.refresh_no_answer)) }
                }
            }
            Text(text, style = sans(11.5.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (ticker.total > 0) {
            Spacer(Modifier.width(8.dp))
            Text(tr(R.string.refresh_count, ticker.returned.coerceAtMost(maxOf(ticker.total, ticker.returned)), maxOf(ticker.total, ticker.returned)), style = mono(10.sp, tracking = 0.sp), color = g.textMuted)
        }
    }
}
