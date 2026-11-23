// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Emerald400
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.ObsidianSurface
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400
import news.openwire.ui.theme.TextPrimaryDark
import news.openwire.ui.theme.TextSecondaryDark

enum class NodeKind { EDITION, TOPIC, SOURCE, BRIEF }
enum class EdgeKind { TOPIC, SOURCE, BRIEF }

class GNode(val id: String, val kind: NodeKind) {
    var label = ""
    var sub = ""
    var x = 0f; var y = 0f
    private var fx = 0f; private var fy = 0f
    var tx = 0f; var ty = 0f
    private var moveStart = 0L; private var moveDur = 0L
    /** Direction from the centre (labels sit on the outside). */
    var angle = -PI.toFloat() / 2
    var visible = false
    var appearAt = 0L
    var on = false
    var local = false
    var keyword = false
    var focus = false
    var set = false

    // Text measured once per label (not in the draw loop).
    internal var labelText: String? = null
    internal var labelLayout: TextLayoutResult? = null
    internal var subText: String? = null
    internal var subLayout: TextLayoutResult? = null

    fun moveTo(x2: Float, y2: Float, now: Long, dur: Long, delay: Long = 0) {
        fx = x; fy = y; tx = x2; ty = y2; moveStart = now + delay; moveDur = dur
        if (dur <= 0) { x = x2; y = y2 }
    }

    /** Advances the move; true while still moving. */
    fun step(now: Long): Boolean {
        if (moveDur <= 0 || (x == tx && y == ty)) return false
        if (now < moveStart) return true
        val k = ((now - moveStart).toFloat() / moveDur).coerceIn(0f, 1f)
        val e = easeInOut(k)
        x = fx + (tx - fx) * e; y = fy + (ty - fy) * e
        if (k >= 1f) { x = tx; y = ty; moveDur = 0 }
        return k < 1f
    }
}

class GEdge(val a: GNode, val b: GNode, val kind: EdgeKind, val bend: Float) {
    var on = false
    var shown = true
    var focus = false
}

/** A short-lived effect: a pulse along an edge, a bloom on a node, or a spark. */
private class Fx {
    var type = 0 // 1 pulse, 2 bloom, 3 spark
    var edge: GEdge? = null
    var node: GNode? = null
    var start = 0L
    var dur = 0L
    var color = Color.White
    var angle = 0f
    var radius = 4f
}

internal fun easeInOut(k: Float): Float = if (k < .5f) 4 * k * k * k else 1 - (-2 * k + 2).let { it * it * it } / 2

/**
 * The setup's network: the edition, topics, sources and the briefing as one graph, drawn in a
 * single Canvas. Positions live in world units (the edition at 0,0); a camera maps them to the
 * screen. Effects come from a small pool, and the frame loop only redraws while something moves.
 */
class GraphEngine(private val reduceMotion: Boolean) {
    val nodes = LinkedHashMap<String, GNode>()
    val edges = ArrayList<GEdge>()
    private val fx = ArrayList<Fx>()
    private val pool = ArrayList<Fx>()
    var now = 0L
        private set

    // Camera: world point at the centre of the view, and pixels per world unit.
    private var cx = 0f; private var cy = 0f; private var sc = 1f
    private var fcx = 0f; private var fcy = 0f; private var fsc = 1f
    private var tcx = 0f; private var tcy = 0f; private var tsc = 1f
    private var camStart = 0L; private var camDur = 0L
    private var view = Rect.Zero
    private var fitBox = Rect(-150f, -150f, 150f, 150f)

    /** 0..1: the whole graph fading out at the end. */
    var fade = 1f
    private var fadeStart = 0L; private var fadeDur = 0L
    /** Rings around the edition while locating. */
    var radar = false
    /** Dormant background network (welcome). */
    var ambientOn = true
    private var nextAmbient = 0L
    var showBriefRings = false

    // Ambient network, built once.
    private val ambX = FloatArray(52); private val ambY = FloatArray(52)
    private val ambA = IntArray(104); private val ambB = IntArray(104); private var ambLinks = 0
    private val ambPulse = Fx()

    private val path = Path()
    // Strokes are made once per screen density, not per frame.
    private var strokePx = -1f
    private var s1 = Stroke(1f); private var s12 = Stroke(1f); private var s13 = Stroke(1f); private var s14 = Stroke(1f)
    private var s16 = Stroke(1f); private var s2 = Stroke(1f); private var sDash = Stroke(1f)
    private var sOn = Stroke(1f); private var sFocus = Stroke(1f)
    private fun strokes(px: Float) {
        if (px == strokePx) return
        strokePx = px
        s1 = Stroke(px); s12 = Stroke(1.2f * px); s13 = Stroke(1.3f * px); s14 = Stroke(1.4f * px); s16 = Stroke(1.6f * px); s2 = Stroke(2f * px)
        sDash = Stroke(width = px, pathEffect = dash)
        sOn = Stroke(width = 1.6f * px, cap = StrokeCap.Round); sFocus = Stroke(width = 2.4f * px, cap = StrokeCap.Round)
    }
    private val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))

    init {
        var seed = 7L
        fun rnd(): Float { seed = (seed * 16807) % 2147483647; return seed / 2147483647f }
        for (i in 0 until 52) {
            val a = rnd() * 2 * PI.toFloat(); val r = 120 + rnd() * 400
            ambX[i] = cos(a) * r * 1.25f; ambY[i] = sin(a) * r * 1.2f
        }
        for (i in 0 until 52) {
            var b1 = -1; var b2 = -1; var d1 = Float.MAX_VALUE; var d2 = Float.MAX_VALUE
            for (j in 0 until 52) if (j != i) {
                val d = (ambX[j] - ambX[i]).let { it * it } + (ambY[j] - ambY[i]).let { it * it }
                if (d < d1) { d2 = d1; b2 = b1; d1 = d; b1 = j } else if (d < d2) { d2 = d; b2 = j }
            }
            if (i < b1) { ambA[ambLinks] = i; ambB[ambLinks] = b1; ambLinks++ }
            if (b2 >= 0 && i < b2) { ambA[ambLinks] = i; ambB[ambLinks] = b2; ambLinks++ }
        }
        node("ed", NodeKind.EDITION).visible = true
    }

    fun node(id: String, kind: NodeKind): GNode = nodes.getOrPut(id) { GNode(id, kind) }

    fun edge(a: GNode, b: GNode, kind: EdgeKind, bend: Float): GEdge =
        edges.firstOrNull { it.a === a && it.b === b } ?: GEdge(a, b, kind, bend).also { edges += it }

    fun removeNode(id: String) {
        val n = nodes.remove(id) ?: return
        edges.removeAll { it.a === n || it.b === n }
        fx.removeAll { (it.node === n || it.edge?.let { e -> e.a === n || e.b === n } == true).also { r -> if (r) pool += it } }
    }

    // ---- Camera ----

    fun setView(r: Rect) { if (r != view) { val first = view == Rect.Zero; view = r; fit(fitBox, if (first) 0 else 450) } }

    /** Frames [box] (world units) inside the view, animated over [dur] ms. */
    fun fit(box: Rect, dur: Long = 900) {
        fitBox = box
        if (view.width <= 0f || view.height <= 0f) return
        val s = min(view.width / box.width, view.height / box.height)
        val ncx = box.center.x; val ncy = box.center.y
        if (dur <= 0 || reduceMotion) { cx = ncx; cy = ncy; sc = s; tcx = ncx; tcy = ncy; tsc = s; camDur = 0; return }
        fcx = cx; fcy = cy; fsc = sc; tcx = ncx; tcy = ncy; tsc = s; camStart = now; camDur = dur
    }

    fun sx(x: Float) = view.center.x + (x - cx) * sc
    fun sy(y: Float) = view.center.y + (y - cy) * sc
    /** Where a node will rest on screen (for the accessibility layer). */
    fun restingScreen(n: GNode): Offset = Offset(view.center.x + (n.tx - tcx) * tsc, view.center.y + (n.ty - tcy) * tsc)
    val scale: Float get() = sc

    // ---- Effects ----

    private fun obtain(): Fx = if (pool.isEmpty()) Fx() else pool.removeAt(pool.lastIndex)

    fun pulse(e: GEdge?, color: Color, dur: Long = 620, delay: Long = 0, radius: Float = 4.2f) {
        if (reduceMotion || e == null) return
        fx += obtain().apply { type = 1; edge = e; node = null; start = now + delay; this.dur = dur; this.color = color; this.radius = radius }
    }

    fun bloom(n: GNode?, color: Color, delay: Long = 0) {
        if (reduceMotion || n == null) return
        fx += obtain().apply { type = 2; node = n; edge = null; start = now + delay; dur = 900; this.color = color }
    }

    fun sparks(n: GNode?, count: Int = 3, delay: Long = 0) {
        if (reduceMotion || n == null) return
        val a0 = atan2(n.y, n.x)
        for (i in 0 until count) {
            fx += obtain().apply { type = 3; node = n; edge = null; start = now + delay + i * 70; dur = 700; color = Rose400; angle = a0 + (i - (count - 1) / 2f) * .42f }
        }
    }

    fun fadeOut(dur: Long, delay: Long = 0) {
        if (reduceMotion) { fade = 0f; return }
        fadeStart = now + delay; fadeDur = dur
    }

    /** Advances time; true when the frame needs drawing. */
    fun tick(t: Long): Boolean {
        val first = now == 0L
        now = t
        var busy = first
        nodes.values.forEach { if (it.step(t)) busy = true }
        if (camDur > 0) {
            val k = ((t - camStart).toFloat() / camDur).coerceIn(0f, 1f); val e = easeInOut(k)
            cx = fcx + (tcx - fcx) * e; cy = fcy + (tcy - fcy) * e; sc = fsc + (tsc - fsc) * e
            if (k >= 1f) camDur = 0
            busy = true
        }
        if (fadeDur > 0) {
            val k = ((t - fadeStart).toFloat() / fadeDur).coerceIn(0f, 1f)
            if (t >= fadeStart) fade = 1f - k
            if (k >= 1f) fadeDur = 0
            busy = true
        }
        if (fx.isNotEmpty()) {
            busy = true
            val it = fx.iterator()
            while (it.hasNext()) { val f = it.next(); if (t > f.start + f.dur) { it.remove(); pool += f } }
        }
        if (ambientOn && !reduceMotion) {
            if (t >= nextAmbient && ambLinks > 0) {
                ambPulse.start = t; ambPulse.dur = 1400; ambPulse.angle = (((t / 900) * 7919) % ambLinks).toFloat()
                nextAmbient = t + 900
            }
            if (t <= ambPulse.start + ambPulse.dur) busy = true
        }
        if (radar || showBriefRings || nodes.values.any { it.visible && it.appearAt > t - 400 }) busy = true
        return busy
    }

    // ---- Drawing (screen space; nothing allocated per frame) ----

    private fun bezier(e: GEdge): Unit {
        val ax = sx(e.a.x); val ay = sy(e.a.y); val bx = sx(e.b.x); val by = sy(e.b.y)
        val mx = (ax + bx) / 2; val my = (ay + by) / 2
        path.reset(); path.moveTo(ax, ay); path.quadraticTo(mx - (by - ay) * e.bend, my + (bx - ax) * e.bend, bx, by)
    }

    private fun pointOn(e: GEdge, k: Float, out: FloatArray) {
        val ax = sx(e.a.x); val ay = sy(e.a.y); val bx = sx(e.b.x); val by = sy(e.b.y)
        val qx = (ax + bx) / 2 - (by - ay) * e.bend; val qy = (ay + by) / 2 + (bx - ax) * e.bend
        val u = 1 - k
        out[0] = u * u * ax + 2 * u * k * qx + k * k * bx; out[1] = u * u * ay + 2 * u * k * qy + k * k * by
    }

    private val pt = FloatArray(2)

    fun edgeColor(k: EdgeKind) = when (k) { EdgeKind.TOPIC -> Rose; EdgeKind.SOURCE -> Indigo400; EdgeKind.BRIEF -> Amber400 }

    fun draw(scope: DrawScope, tm: TextMeasurer, styles: GraphStyles, px: Float) = with(scope) {
        val a = fade
        if (a <= 0f) return@with
        strokes(px)
        // Dormant network behind everything.
        if (ambientOn) {
            for (i in 0 until ambLinks) {
                drawLine(Color.White.copy(alpha = .045f * a), Offset(sx(ambX[ambA[i]]), sy(ambY[ambA[i]])), Offset(sx(ambX[ambB[i]]), sy(ambY[ambB[i]])), strokeWidth = px)
            }
            for (i in 0 until 52) drawCircle(Color.White.copy(alpha = .09f * a), 1.8f * px, Offset(sx(ambX[i]), sy(ambY[i])))
            if (!reduceMotion && ambLinks > 0 && now <= ambPulse.start + ambPulse.dur) {
                val i = ambPulse.angle.toInt().coerceIn(0, ambLinks - 1)
                val k = easeInOut(((now - ambPulse.start).toFloat() / ambPulse.dur).coerceIn(0f, 1f))
                val x = sx(ambX[ambA[i]] + (ambX[ambB[i]] - ambX[ambA[i]]) * k); val y = sy(ambY[ambA[i]] + (ambY[ambB[i]] - ambY[ambA[i]]) * k)
                drawCircle(Indigo400.copy(alpha = .45f * a * (1 - k)), 2f * px, Offset(x, y))
            }
        }
        // Edges: rose edition→topic, indigo topic→source, amber topic→briefing; faint and dashed when off.
        for (e in edges) {
            if (!e.shown || !e.a.visible || !e.b.visible) continue
            bezier(e)
            val c = edgeColor(e.kind)
            if (e.on || e.focus) drawPath(path, c.copy(alpha = (if (e.focus) .95f else .55f) * a), style = if (e.focus) sFocus else sOn)
            else drawPath(path, Color.White.copy(alpha = .13f * a), style = sDash)
        }
        // Pulses under the nodes.
        for (f in fx) if (f.type == 1 && now >= f.start) {
            val e = f.edge ?: continue
            val k = easeInOut(((now - f.start).toFloat() / f.dur).coerceIn(0f, 1f))
            pointOn(e, k, pt)
            drawCircle(f.color.copy(alpha = .28f * a), f.radius * 2.6f * px, Offset(pt[0], pt[1]))
            drawCircle(f.color.copy(alpha = a), f.radius * px, Offset(pt[0], pt[1]))
        }
        for (n in nodes.values) if (n.visible) drawNode(this, n, tm, styles, px, a)
        for (f in fx) if (f.type != 1 && now >= f.start) {
            val n = f.node ?: continue
            val k = ((now - f.start).toFloat() / f.dur).coerceIn(0f, 1f)
            if (f.type == 2) drawCircle(f.color.copy(alpha = (1 - k) * .7f * a), (12 + 36 * easeInOut(k)) * sc.coerceIn(.55f, 1.4f) * px, Offset(sx(n.x), sy(n.y)), style = s2)
            else {
                val d = easeInOut(k) * 70
                drawCircle(f.color.copy(alpha = (1 - k) * a), 2.6f * px, Offset(sx(n.x + cos(f.angle) * d), sy(n.y + sin(f.angle) * d)))
            }
        }
    }

    private fun ring(scope: DrawScope, x: Float, y: Float, r0: Float, color: Color, period: Long, offset: Long, px: Float, a: Float) {
        if (reduceMotion) return
        val k = (((now + offset) % period).toFloat() / period)
        scope.drawCircle(color.copy(alpha = (1 - k) * .55f * a), r0 + r0 * .9f * k, Offset(x, y), style = s14)
    }

    private fun drawNode(scope: DrawScope, n: GNode, tm: TextMeasurer, st: GraphStyles, px: Float, a0: Float) = with(scope) {
        val appear = if (reduceMotion) 1f else ((now - n.appearAt).toFloat() / 350f).coerceIn(0f, 1f)
        val a = a0 * appear
        if (a <= 0f) return@with
        val x = sx(n.x); val y = sy(n.y)
        // Radii keep a readable size on screen whatever the zoom.
        val z = sc.coerceIn(.55f, 1.4f)
        when (n.kind) {
            NodeKind.EDITION -> {
                val r = 31f * z * px
                if (radar) for (i in 0 until 3) ring(this, x, y, 34f * z * px, Rose400, 1800, i * 600L, px, a)
                drawCircle((if (n.set) Rose else Color.White).copy(alpha = .09f * a), 44f * z * px, Offset(x, y))
                drawCircle(ObsidianSurface.copy(alpha = a), r, Offset(x, y))
                drawCircle((if (n.set) Rose400 else Color.White).copy(alpha = (if (n.set) .85f else .28f) * a), r, Offset(x, y), style = s16)
                // A small globe.
                val g = 12f * z * px; val gc = (if (n.set) Rose400 else TextSecondaryDark).copy(alpha = a)
                drawCircle(gc, g, Offset(x, y), style = s13)
                drawOval(gc, Offset(x - g * .43f, y - g), androidx.compose.ui.geometry.Size(g * .86f, g * 2), style = s13)
                drawLine(gc, Offset(x - g, y), Offset(x + g, y), 1.3f * px)
                text(this, tm, n, n.label, st.edition, x, y + 46f * z * px, center = true, isSub = false, a = a)
                text(this, tm, n, n.sub, st.mono, x, y + 46f * z * px + st.editionLine, center = true, isSub = true, a = a)
            }
            NodeKind.TOPIC -> {
                val accent = when { n.local -> Indigo400; n.keyword -> Emerald400; else -> Rose }
                if (n.on) drawCircle(accent.copy(alpha = .22f * a), 17f * z * px, Offset(x, y))
                drawCircle((if (n.on) accent else ObsidianSurface).copy(alpha = a), 8.5f * z * px, Offset(x, y))
                drawCircle((if (n.on) Color.White else TextSecondaryDark).copy(alpha = (if (n.on) .6f else .5f) * a), 8.5f * z * px, Offset(x, y), style = s12)
                val c = cos(n.angle); val s = sin(n.angle); val d = 21f * z * px
                val anchor = if (c > .12f) 1 else if (c < -.12f) -1 else 0
                labelAt(this, tm, n, st.topic, x + c * d, y + s * d, anchor, s, if (n.on) 1f else .78f, a)
            }
            NodeKind.SOURCE -> {
                val r = 6f * z * px
                if (n.on || n.focus) drawCircle(Indigo400.copy(alpha = (if (n.focus) .35f else .18f) * a), 13f * z * px, Offset(x, y))
                drawCircle((if (n.on) Indigo400 else ObsidianSurface).copy(alpha = a), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = .35f * a), r, Offset(x, y), style = s1)
                if (n.focus) {
                    val s = sin(n.angle)
                    labelAt(this, tm, n, st.source, x, y + (if (s > 0) 16f else -16f) * z * px, 0, s, 1f, a)
                }
            }
            NodeKind.BRIEF -> {
                val r = 30f * z * px
                if (showBriefRings) for (i in 0 until 2) ring(this, x, y, r, Amber400, 2800, i * 1400L, px, a)
                drawCircle(Amber400.copy(alpha = .16f * a), r * 1.35f, Offset(x, y))
                drawCircle(ObsidianSurface.copy(alpha = a), r, Offset(x, y))
                drawCircle(Amber400.copy(alpha = .9f * a), r, Offset(x, y), style = s16)
                for (i in BARS.indices) {
                    val bx = x + (i - 2) * 4.5f * z * px; val h = BARS[i] * z * px
                    drawLine(Amber400.copy(alpha = a), Offset(bx, y - h), Offset(bx, y + h), 2f * px, cap = StrokeCap.Round)
                }
                text(this, tm, n, n.label, st.topic, x, y + r + 8f * px, center = true, isSub = false, a = a)
            }
        }
    }

    private fun measure(tm: TextMeasurer, n: GNode, s: String, style: TextStyle, sub: Boolean): TextLayoutResult {
        if (sub) {
            if (n.subText != s || n.subLayout == null) { n.subText = s; n.subLayout = tm.measure(s, style, maxLines = 1, constraints = Constraints(maxWidth = 1200)) }
            return n.subLayout!!
        }
        if (n.labelText != s || n.labelLayout == null) { n.labelText = s; n.labelLayout = tm.measure(s, style, maxLines = 1, constraints = Constraints(maxWidth = 1200)) }
        return n.labelLayout!!
    }

    private fun text(scope: DrawScope, tm: TextMeasurer, n: GNode, s: String, style: TextStyle, x: Float, y: Float, center: Boolean, isSub: Boolean, a: Float) {
        if (s.isEmpty()) return
        val l = measure(tm, n, s, style, isSub)
        scope.drawText(l, topLeft = Offset(if (center) x - l.size.width / 2f else x, y), alpha = a)
    }

    private fun labelAt(scope: DrawScope, tm: TextMeasurer, n: GNode, style: TextStyle, x: Float, y: Float, anchor: Int, s: Float, alpha: Float, a: Float) {
        if (n.label.isEmpty()) return
        val l = measure(tm, n, n.label, style, false)
        val w = l.size.width.toFloat(); val h = l.size.height.toFloat()
        val lx = when (anchor) { 1 -> x; -1 -> x - w; else -> x - w / 2 }
        val ly = if (anchor == 0) (if (s < 0) y - h else y) else y - h / 2
        scope.drawText(l, topLeft = Offset(lx, ly), alpha = alpha * a)
    }

    /** The node at a screen point (its dot or its label), within a 24dp-ish reach. */
    fun hit(p: Offset, reach: Float, kinds: Set<NodeKind>): GNode? {
        var best: GNode? = null; var bestD = Float.MAX_VALUE
        for (n in nodes.values) {
            if (!n.visible || n.kind !in kinds) continue
            val x = sx(n.x); val y = sy(n.y)
            var d = sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y))
            n.labelLayout?.let { l ->
                // The label counts as part of the node.
                val c = cos(n.angle); val dx = if (c > .12f) 1 else if (c < -.12f) -1 else 0
                val lx0 = if (dx == 1) x else if (dx == -1) x - l.size.width else x - l.size.width / 2f
                if (n.kind == NodeKind.TOPIC && p.x in lx0..(lx0 + l.size.width) && kotlin.math.abs(p.y - (y + sin(n.angle) * 21f * sc)) < l.size.height) d = 0f
            }
            if (d < reach && d < bestD) { best = n; bestD = d }
        }
        return best
    }
}

/** Text styles for the graph (built once from the app's type tokens). */
class GraphStyles(val edition: TextStyle, val mono: TextStyle, val topic: TextStyle, val source: TextStyle, val editionLine: Float)

internal val GraphLabel = TextPrimaryDark
private val BARS = floatArrayOf(5f, 9f, 12f, 9f, 5f)
