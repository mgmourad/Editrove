// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.os.Build
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.node.invalidateDraw
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400
import news.openwire.ui.theme.VariableType
import news.openwire.ui.theme.rememberReduceMotion

// "Type on a press": the reader's stages, each started by the content it shows becoming ready.

/** M3 emphasized decelerate / accelerate. */
val EmphasizedDecelerate = CubicBezierEasing(.05f, .7f, .1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(.3f, 0f, .8f, .15f)

private val blurOk = Build.VERSION.SDK_INT >= 31

/** RenderEffect blurs in whole-pixel steps, built once each (animations reuse them; API 31+). */
private object BlurSteps {
    private val cache = arrayOfNulls<RenderEffect>(33)
    fun of(px: Float): RenderEffect? {
        if (!blurOk || px < .5f) return null
        val i = px.toInt().coerceIn(1, 32)
        return cache[i] ?: BlurEffect(i.toFloat(), i.toFloat(), TileMode.Decal).also { cache[i] = it }
    }
}

/**
 * True once the reader is in place: the sheet's opening transition has finished (cover screen),
 * or straight away beside the feed. The headline and kicker start here.
 */
@Composable
fun rememberReaderSettled(): Boolean {
    val scope = LocalImageTransition.current?.sheetScope ?: return true
    val t = scope.transition
    return t.currentState == EnterExitState.Visible && t.targetState == EnterExitState.Visible
}

/** The feed behind an open story: shrinks to 93%, dims and (API 31+) blurs; reverses on close. */
@Composable
fun Modifier.sinkBehind(open: Boolean): Modifier {
    val reduce = rememberReduceMotion()
    val k by animateFloatAsState(
        if (open) 1f else 0f,
        if (reduce) tween(0) else if (open) tween(620, easing = EmphasizedDecelerate) else tween(520, easing = EmphasizedAccelerate),
        label = "sink",
    )
    val density = LocalDensity.current
    return this
        .graphicsLayer {
            val s = 1f - .07f * k
            scaleX = s; scaleY = s
            renderEffect = if (reduce) null else BlurSteps.of(with(density) { 10.dp.toPx() } * k)
        }
        .drawWithCache { onDrawWithContent { drawContent(); if (k > 0f) drawRect(Color.Black.copy(alpha = .38f * k)) } }
}

private val PreviewFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(.25f) }.also { m -> m.timesAssign(ColorMatrix().apply { setToScale(.72f, .72f, .72f, 1f) }) },
)

/**
 * The hero photo developing like a print: a soft, desaturated, darkened preview (the cached
 * thumbnail, scaled up) until the full image is decoded; then the image reveals top to bottom
 * behind a moving mask with a 2 px light line at its edge (1150 ms), the preview crossfading out
 * beneath. A full image that came from memory shows at once.
 */
@Composable
fun DevelopingImage(preview: String?, full: String?, modifier: Modifier, start: Boolean = true, onFailed: () -> Unit) {
    val reduce = rememberReduceMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reveal = remember(full) { Animatable(if (reduce) 1f else 0f) }
    var ready by remember(full) { mutableStateOf(false) }
    // Every story develops the same way, cached or not, once the page is in place to be seen.
    LaunchedEffect(ready, start) {
        if (!ready || !start || reveal.value >= 1f) return@LaunchedEffect
        if (reduce) reveal.snapTo(1f) else reveal.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
    }
    // Without a thumbnail, the photo itself (soft and dim) is its own preview.
    val soft = preview ?: full
    Box(modifier) {
        if (soft != null && reveal.value < 1f) {
            AsyncImage(
                model = soft, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = PreviewFilter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - reveal.value * reveal.value
                    renderEffect = if (alpha > 0f) BlurSteps.of(14.dp.toPx()) else null
                    scaleX = 1.04f; scaleY = 1.04f
                },
            )
        }
        if (full != null) AsyncImage(
            model = full, contentDescription = null, contentScale = ContentScale.Crop,
            onState = { s ->
                if (s is AsyncImagePainter.State.Success && !ready) ready = true
                else if (s is AsyncImagePainter.State.Error) onFailed()
            },
            modifier = Modifier.fillMaxSize().drawWithCache {
                val h = size.height; val w = size.width
                val line = Brush.horizontalGradient(if (rtl) listOf(Indigo400, Rose400) else listOf(Rose400, Indigo400))
                val glow = Brush.verticalGradient(listOf(Color.Transparent, Rose.copy(alpha = .22f)), startY = 0f, endY = 18.dp.toPx())
                val lw = 2.dp.toPx(); val gh = 18.dp.toPx()
                onDrawWithContent {
                    val k = reveal.value
                    if (!ready) return@onDrawWithContent
                    if (k >= 1f) { drawContent(); return@onDrawWithContent }
                    val y = h * k
                    clipRect(bottom = y) { this@onDrawWithContent.drawContent() }
                    translate(top = y - gh) { drawRect(glow, size = Size(w, gh)) }
                    drawLine(line, Offset(0f, y), Offset(w, y), lw)
                }
            },
        )
    }
}

/** The source logo's ring drawing itself (700 ms), rose to indigo. */
@Composable
fun SourceRing(start: Boolean, size: Dp, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    val k by animateFloatAsState(if (start) 1f else 0f, if (reduce) tween(0) else tween(700, easing = FastOutSlowInEasing), label = "ring")
    Box(
        Modifier.size(size).drawWithCache {
            val brush = Brush.sweepGradient(listOf(Indigo400, Rose, Indigo400))
            val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
            val inset = 1.5.dp.toPx() / 2
            onDrawBehind {
                drawCircle(Glass_Slate, radius = this.size.minDimension / 2 - inset)
                if (k > 0f) rotate(-90f) {
                    drawArc(brush, 0f, 360f * k, false, topLeft = Offset(inset, inset), size = Size(this.size.width - 2 * inset, this.size.height - 2 * inset), style = stroke)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val Glass_Slate = news.openwire.ui.theme.Slate900

/** A number counting up to [target] once it's known. */
@Composable
fun CountUp(target: Int?, render: @Composable (Int?) -> Unit) {
    val reduce = rememberReduceMotion()
    var shown by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(target) {
        if (target == null) { shown = null; return@LaunchedEffect }
        if (reduce || target <= 1) { shown = target; return@LaunchedEffect }
        val steps = target.coerceAtMost(12)
        for (i in 1..steps) { shown = (target * i) / steps; delay(520L / steps) }
    }
    render(shown)
}

private val RX_RTL_HEAD = Regex("[\\u0590-\\u08FF]")

/**
 * The headline setting itself, word by word (55 ms apart, 900 ms each): each word crossfades from a
 * thin, small-optical-size cut (wght 220 / opsz 8), blurred, 0.35 em low and loosely set, to the
 * display cut (wght 640 / opsz 72), sharp and in place.
 *
 * Smoothness: both cuts are laid out once; every frame only moves layers (alpha, offset, scale,
 * blur). The clock is read inside `graphicsLayer`, so nothing recomposes or re-measures while it
 * plays. TalkBack reads the headline once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingHeadline(text: String, start: Boolean, size: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit, color: Color, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    val boldStyle = remember(size, lineHeight) {
        TextStyle(fontFamily = VariableType.newsreader(640, 72), fontWeight = FontWeight(640), fontSize = size, lineHeight = lineHeight, letterSpacing = (-0.01).em)
    }
    val thinStyle = remember(size, lineHeight) {
        TextStyle(fontFamily = VariableType.newsreader(220, 8), fontWeight = FontWeight(220), fontSize = size, lineHeight = lineHeight, letterSpacing = 0.05.em)
    }
    // One clock for every word, read only in the draw phase.
    val clock = remember(text) { androidx.compose.runtime.mutableLongStateOf(if (reduce) Long.MAX_VALUE / 4 else -1L) }
    val total = words.size * 55L + 900L
    LaunchedEffect(text, start) {
        if (!start || reduce) return@LaunchedEffect
        val t0 = withFrameMillis { it }
        while (isActive && clock.longValue < total) withFrameMillis { clock.longValue = it - t0 }
        clock.longValue = total
    }
    val rtl = RX_RTL_HEAD.containsMatchIn(text.take(60))
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(modifier.clearAndSetSemantics { contentDescription = text; heading() }) {
            words.forEachIndexed { i, w ->
                val token = if (i < words.size - 1) "$w " else w
                fun p(): Float {
                    val t = clock.longValue
                    if (t < 0) return 0f
                    return EmphasizedDecelerate.transform(((t - i * 55L) / 900f).coerceIn(0f, 1f))
                }
                Box {
                    // The display cut decides the word's box; the thin cut sits over it and fades away.
                    Text(
                        token, style = boldStyle, color = color, softWrap = false, maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            // Sharp from the start (no blur layer): it fades in as the thin cut fades out.
                            val k = p()
                            alpha = k * k
                            translationY = (1f - k) * .35f * size.toPx()
                        },
                    )
                    Text(
                        token, style = thinStyle, color = color, softWrap = false, maxLines = 1,
                        modifier = Modifier.matchParentSize().wrapContentSize(Alignment.BottomStart, unbounded = true).graphicsLayer {
                            val k = p()
                            alpha = if (k >= 1f) 0f else (1f - k) * (k * 4f).coerceAtMost(1f) * .9f
                            translationY = (1f - k) * .35f * size.toPx()
                            // The only blur per word, and only while it's still soft (cheap on the GPU).
                            renderEffect = if (k > 0f && k < .6f) BlurSteps.of(9.dp.toPx() * (1f - k / .6f)) else null
                        },
                    )
                }
            }
        }
    }
}

/**
 * A section of the article sliding up into place, [index] × 70 ms after [start]
 * (a cascade down the page). Only the layer moves: nothing re-measures.
 */
@Composable
fun Modifier.cascadeIn(index: Int, start: Boolean): Modifier {
    val reduce = rememberReduceMotion()
    val k = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(start) {
        if (!start || reduce || k.value >= 1f) return@LaunchedEffect
        delay(70L * index.coerceAtMost(12))
        k.animateTo(1f, tween(560, easing = EmphasizedDecelerate))
    }
    return graphicsLayer {
        val v = k.value
        alpha = v
        translationY = (1f - v) * 18.dp.toPx()
    }
}

/** Shimmer bars standing in for text not written yet. */
@Composable
fun ShimmerLines(lines: Int = 3) {
    val reduce = rememberReduceMotion()
    val x = if (reduce) null else rememberInfiniteTransition(label = "shimmer").animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val accent = Glass.accent
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(lines) { i ->
            Box(
                Modifier.fillMaxWidth(if (i == lines - 1) .62f else 1f).height(10.dp).clip(CircleShape).drawWithCache {
                    val base = Glass_Track
                    val band = Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = .20f), Color.Transparent), startX = 0f, endX = size.width * .5f)
                    onDrawBehind {
                        drawRect(base)
                        x?.let { v -> translate(left = size.width * v.value - size.width * .25f) { drawRect(band, size = Size(size.width * .5f, size.height)) } }
                    }
                },
            )
        }
    }
}

private val Glass_Track = Color.White.copy(alpha = .07f)

/** A little orbit loader: two dots circling (the summary being written). */
@Composable
fun OrbitLoader(running: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val a = if (running && !reduce) rememberInfiniteTransition(label = "orbit").animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "a") else null
    Box(
        modifier.size(14.dp).drawBehind {
            val r = size.minDimension / 2 - 2.dp.toPx()
            drawCircle(Rose400.copy(alpha = .25f), r, style = Stroke(1.dp.toPx()))
            val ang = Math.toRadians((a?.value ?: 0f).toDouble())
            val c = center
            drawCircle(Rose, 2.dp.toPx(), Offset(c.x + r * kotlin.math.cos(ang).toFloat(), c.y + r * kotlin.math.sin(ang).toFloat()))
            drawCircle(Amber400, 1.5.dp.toPx(), Offset(c.x - r * kotlin.math.cos(ang).toFloat(), c.y - r * kotlin.math.sin(ang).toFloat()))
        },
    )
}

/**
 * Key-point text arriving the way a model writes: one character every ~16 ms, each landing
 * in the rose accent with a soft glow and cooling to the body colour. Text that arrives all at once plays
 * the same reveal. The layout is the final text from the start (unrevealed letters are clear).
 */
@Composable
fun WrittenText(text: String, startAt: Int, style: TextStyle, settled: Color, onDone: () -> Unit = {}) {
    val reduce = rememberReduceMotion()
    var n by remember(text) { mutableIntStateOf(if (reduce) Int.MAX_VALUE / 2 else 0) }
    LaunchedEffect(text) {
        if (reduce) { onDone(); return@LaunchedEffect }
        delay(startAt * 16L)
        val t0 = withFrameMillis { it }
        while (isActive && n < text.length + GLOW) withFrameMillis { n = ((it - t0) / 16L).toInt() }
        onDone()
    }
    // Letters land in the app's own rose accent and cool to the body colour.
    val glow = Glass.accent
    val shown: AnnotatedString = buildAnnotatedString {
        val settledEnd = (n - GLOW).coerceIn(0, text.length)
        val glowEnd = n.coerceIn(0, text.length)
        withStyle(SpanStyle(color = settled)) { append(text, 0, settledEnd) }
        if (glowEnd > settledEnd) withStyle(SpanStyle(color = glow, shadow = Shadow(glow.copy(alpha = .6f), blurRadius = 10f))) { append(text, settledEnd, glowEnd) }
        if (glowEnd < text.length) withStyle(SpanStyle(color = Color.Transparent)) { append(text, glowEnd, text.length) }
    }
    Text(shown, style = style, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = text })
}

/** Characters still glowing behind the writing edge (about 0.2 s of cooling). */
private const val GLOW = 12

/**
 * A paragraph revealing from the top down behind a soft mask, [index] × 140 ms after the body
 * arrives (the first screenful only; text further down is simply there).
 */
fun Modifier.revealDown(index: Int, reduce: Boolean): Modifier =
    if (reduce || index > 8) this else this.then(RevealDownElement(index))

private data class RevealDownElement(val index: Int) : androidx.compose.ui.node.ModifierNodeElement<RevealDownNode>() {
    override fun create() = RevealDownNode(index)
    override fun update(node: RevealDownNode) { node.index = index }
}

private class RevealDownNode(var index: Int) : Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
    private val k = Animatable(0f)
    override fun onAttach() {
        coroutineScope.launchReveal(k, index) { invalidateDraw() }
    }

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        val v = k.value
        if (v >= 1f) { drawContent(); return }
        if (v <= 0f) return
        val soft = 28.dp.toPx()
        val edge = (size.height + soft) * v
        // Fully shown above the soft edge; fading through it; nothing below.
        clipRect(bottom = (edge - soft).coerceAtLeast(0f)) { this@draw.drawContent() }
        val steps = 4
        for (i in 0 until steps) {
            val top = edge - soft + soft * i / steps
            clipRect(top = top.coerceAtLeast(0f), bottom = (top + soft / steps).coerceAtMost(size.height)) {
                // Fades by quarter steps through the soft edge (no layer, no allocation).
                drawContentAlpha(this@draw, 1f - (i + 1f) / (steps + 1f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContentAlpha(scope: androidx.compose.ui.graphics.drawscope.ContentDrawScope, alpha: Float) {
    drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), RevealPaint.also { it.alpha = alpha })
    scope.drawContent()
    drawContext.canvas.restore()
}

private val RevealPaint = androidx.compose.ui.graphics.Paint()

private fun kotlinx.coroutines.CoroutineScope.launchReveal(k: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, index: Int, invalidate: () -> Unit) =
    launch {
        delay(140L * index)
        k.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) { invalidate() }
        invalidate()
    }

/**
 * The reader's top edge: a rose-to-indigo comet looping while the article loads, then the
 * reading-progress line.
 */
@Composable
fun CometLine(loading: Boolean, progress: () -> Float, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val x = if (loading && !reduce) rememberInfiniteTransition(label = "comet").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "x") else null
    Box(
        modifier.fillMaxWidth().height(2.dp).drawWithCache {
            val w = size.width; val h = size.height
            val tail = w * .28f
            val comet = Brush.horizontalGradient(
                if (rtl) listOf(Indigo400, Rose, Color.Transparent) else listOf(Color.Transparent, Rose, Indigo400),
                startX = 0f, endX = tail,
            )
            val fill = Brush.horizontalGradient(listOf(Rose, Indigo400), startX = 0f, endX = w)
            onDrawBehind {
                if (x != null) {
                    var left = -tail + (w + tail) * x.value
                    if (rtl) left = w - tail - left
                    translate(left = left) { drawRect(comet, size = Size(tail, h)) }
                } else if (!loading) {
                    val p = progress().coerceIn(0f, 1f)
                    if (p > 0f) {
                        if (rtl) translate(left = w * (1f - p)) { drawRect(fill, size = Size(w * p, h)) }
                        else drawRect(fill, size = Size(w * p, h))
                    }
                }
            }
        },
    )
}

/**
 * A paragraph that settles onto the page when the reader scrolls it into view: a soft ink wipe
 * from its top to its bottom (a feathered mask, not a hard edge) while it rises a few dp, over
 * about a second. Paragraphs already on screen settle top-down, a little after one another.
 * Each plays once; with animations off, text is simply there.
 */
/**
 * Whether the article body may start revealing: opened once the key points have finished
 * writing (plus a pause), so the eye has finished with the summary before the text arrives.
 */
val LocalBodyGate = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.State<Boolean>> { androidx.compose.runtime.mutableStateOf(true) }

/**
 * A paragraph that settles onto the page only when both are true: the body gate is open (the
 * summary is done, and a beat has passed) and the reader has scrolled the paragraph well into
 * view. A soft ink wipe top to bottom, a small rise and (Android 12+) blur to focus, ~1.4 s.
 * Paragraphs that come into view together settle one after another, top first.
 */
@Composable
fun Modifier.revealOnScroll(): Modifier {
    val reduce = rememberReduceMotion()
    if (reduce) return this
    val view = androidx.compose.ui.platform.LocalView.current
    val gate by LocalBodyGate.current
    val k = remember { Animatable(0f) }
    // Where the paragraph's top sits on screen (0 top … 1 bottom), or -1 while still below the fold.
    var inView by remember { mutableStateOf(-1f) }
    LaunchedEffect(gate, inView >= 0f) {
        if (!gate || inView < 0f || k.value > 0f) return@LaunchedEffect
        delay((inView * 420f).toLong()) // a staircase down the screen
        k.animateTo(1f, tween(1400, easing = SettleEasing))
    }
    return this
        .then(
            Modifier.onGloballyPositioned { c ->
                if (inView >= 0f) return@onGloballyPositioned
                val h = view.height.toFloat().takeIf { it > 0f } ?: return@onGloballyPositioned
                // Unclipped position: an off-screen paragraph is really off screen (boundsInWindow
                // clips it to an empty box at the top, which made everything "visible" at once).
                val top = c.positionInWindow().y
                if (c.isAttached && top < h * .82f && top + c.size.height > 0f) inView = (top / h).coerceIn(0f, 1f)
            },
        )
        .graphicsLayer {
            val v = k.value
            translationY = (1f - v) * 22.dp.toPx()
            alpha = if (v <= 0f) 0f else 1f
            renderEffect = if (v > 0f && v < .85f) BlurSteps.of(5.dp.toPx() * (1f - v / .85f)) else null
            compositingStrategy = if (v in 0.001f..0.999f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithCache {
            val feather = size.height * .6f + 32.dp.toPx()
            onDrawWithContent {
                val v = k.value
                drawContent()
                if (v >= 1f || v <= 0f) return@onDrawWithContent
                val edge = -feather + (size.height + feather) * v
                val mask = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = edge, endY = edge + feather)
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }
}

/** A gentle S-curve: eases in, travels evenly, eases out. */
private val SettleEasing = CubicBezierEasing(.4f, 0f, .2f, 1f)

/**
 * The develop effect for anything that isn't a single photo (a publisher's logo canvas): soft,
 * dim and desaturated until [ready], then the sharp version wipes in top to bottom behind the
 * same light line as a photo. [content] is drawn twice (the soft layer and the sharp one).
 */
@Composable
fun DevelopReveal(ready: Boolean, start: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(ready, start) {
        if (!ready || !start || reveal.value >= 1f) return@LaunchedEffect
        reveal.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
    }
    Box(modifier) {
        if (reveal.value < 1f) Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - reveal.value * reveal.value
                renderEffect = if (alpha > 0f) BlurSteps.of(14.dp.toPx()) else null
                scaleX = 1.04f; scaleY = 1.04f
            }.drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = .35f)) },
        ) { content() }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val line = Brush.horizontalGradient(if (rtl) listOf(Indigo400, Rose400) else listOf(Rose400, Indigo400))
                val glow = Brush.verticalGradient(listOf(Color.Transparent, Rose.copy(alpha = .22f)), startY = 0f, endY = 18.dp.toPx())
                val lw = 2.dp.toPx(); val gh = 18.dp.toPx()
                onDrawWithContent {
                    val k = reveal.value
                    if (k <= 0f) return@onDrawWithContent
                    if (k >= 1f) { drawContent(); return@onDrawWithContent }
                    val y = size.height * k
                    clipRect(bottom = y) { this@onDrawWithContent.drawContent() }
                    translate(top = y - gh) { drawRect(glow, size = Size(size.width, gh)) }
                    drawLine(line, Offset(0f, y), Offset(size.width, y), lw)
                }
            },
        ) { content() }
    }
}

/** Gravity: starts still and keeps accelerating (a page let go). */
val FreeFall = CubicBezierEasing(.55f, 0f, .9f, .45f)

/** The story the reader just let fall back into the feed: its card takes the landing. */
object Landing {
    var id by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var at by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    fun mark(articleId: String) { id = articleId; at = System.nanoTime() }
}

/**
 * The card a closed story falls back into: as the page lands (about 420 ms after closing), it
 * gives a little under the impact and springs back, once.
 */
@Composable
fun Modifier.landing(articleId: String): Modifier {
    val reduce = rememberReduceMotion()
    if (reduce) return this
    val mine = Landing.id == articleId
    val k = remember(articleId) { Animatable(0f) }
    val at = Landing.at
    LaunchedEffect(mine, at) {
        if (!mine) return@LaunchedEffect
        delay(400)
        k.snapTo(0f)
        k.animateTo(1f, tween(90, easing = LinearEasing)) // impact
        k.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = .42f, stiffness = 420f)) // rebound
    }
    return graphicsLayer {
        val v = k.value
        scaleY = 1f - .045f * v
        scaleX = 1f + .015f * v
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
        translationY = 4.dp.toPx() * v
    }
}
