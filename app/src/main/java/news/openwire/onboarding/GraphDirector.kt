// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.onboarding.EditionMode
import news.openwire.domain.onboarding.OnboardingState
import news.openwire.domain.onboarding.Step
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400

/**
 * Turns setup state changes into graph moves: which nodes exist and where, which are on, and
 * the motion that goes with each user action (a pulse along the edge, a bloom, sparks).
 * Big motion only follows the reader's own actions and the one finale.
 */
class GraphDirector(private val engine: GraphEngine, private val vm: OnboardingViewModel, private val reduce: Boolean) {
    private var last: OnboardingState? = null
    /** Bumped when nodes settle somewhere new (the accessibility layer re-places itself). */
    var version = 0
        private set
    var editionLabels: (OnboardingState) -> Pair<String, String> = { "" to "" }
    var briefLabel = ""

    private fun d(ms: Long) = if (reduce) 0L else ms

    fun apply(s: OnboardingState) {
        val old = last
        last = s
        val stepChanged = old?.step != s.step
        // Edition node: label, the radar while locating, a bloom when confirmed.
        val ed = engine.node("ed", NodeKind.EDITION)
        val (label, sub) = editionLabels(s)
        ed.label = label; ed.sub = sub; ed.set = s.choices.edition != null && s.editionMode == EditionMode.CONFIRMED
        engine.radar = s.step == Step.EDITION && s.editionMode == EditionMode.LOCATING
        if (old != null && old.choices.edition != s.choices.edition && s.choices.edition != null) { engine.bloom(ed, Rose400); engine.bloom(ed, Indigo400, 180) }
        engine.ambientOn = s.step == Step.WELCOME || s.step == Step.EDITION
        engine.quietOff = s.step >= Step.SOURCES
        engine.ringRadius = if (s.step >= Step.TOPICS) R_TOPICS else 0f

        when (s.step) {
            Step.WELCOME -> { hideFrom(NodeKind.TOPIC); hideFrom(NodeKind.SOURCE); hideBrief(); ed.visible = false; if (stepChanged) engine.fit(BOX_WELCOME, d(900)) }
            Step.EDITION -> {
                if (!ed.visible) { ed.visible = true; ed.appearAt = engine.now }
                hideFrom(NodeKind.TOPIC); hideFrom(NodeKind.SOURCE); hideBrief()
                if (stepChanged) engine.fit(BOX_EDITION, d(900))
            }
            Step.TOPICS -> {
                ed.visible = true
                topics(s, old); hideFrom(NodeKind.SOURCE); hideBrief()
                if (stepChanged) engine.fit(BOX_TOPICS, d(1000))
            }
            Step.SOURCES -> {
                ed.visible = true
                topics(s, old); sources(s, old); hideBrief()
                if (stepChanged) engine.fit(BOX_SOURCES, d(1000))
            }
            Step.PRESENTER -> {
                ed.visible = true
                topics(s, old); sources(s, old); brief(s, stepChanged)
                if (stepChanged) engine.fit(BOX_PRESENTER, d(1000))
            }
            Step.FINISHING -> {
                ed.visible = true
                topics(s, old); sources(s, old); brief(s, false)
            }
            Step.DONE -> Unit
        }
        version++
        engine.invalidate()
    }

    private fun hideFrom(kind: NodeKind) { engine.nodes.values.forEach { if (it.kind == kind) it.visible = false } }
    private fun hideBrief() { engine.nodes["brief"]?.visible = false; engine.showBriefRings = false }

    private fun topics(s: OnboardingState, old: OnboardingState?) {
        val list = vm.topicNodes(s)
        val ed = engine.nodes.getValue("ed")
        val keep = list.map { "t:" + it.id }.toSet()
        engine.nodes.keys.filter { it.startsWith("t:") && it !in keep }.forEach(engine::removeNode)
        val n = list.size
        list.forEachIndexed { i, t ->
            val id = "t:" + t.id
            val isNew = id !in engine.nodes
            val node = engine.node(id, NodeKind.TOPIC)
            node.label = t.label; node.local = t.local; node.keyword = t.keyword
            val a = (-PI / 2 + (i + .5) * 2 * PI / n).toFloat() // half a step: nothing sits under the edition label
            node.angle = a
            val tx = cos(a) * R_TOPICS; val ty = sin(a) * R_TOPICS
            val e = engine.edge(ed, node, EdgeKind.TOPIC, .12f)
            if (isNew) {
                node.x = 0f; node.y = 0f
                node.moveTo(tx, ty, engine.now, d(700), d(60L * i))
                node.appearAt = engine.now + d(60L * i)
            } else if (node.tx != tx || node.ty != ty) node.moveTo(tx, ty, engine.now, d(700))
            node.visible = true
            val on = t.id in s.choices.topics
            val was = old?.choices?.topics?.contains(t.id) == true
            node.on = on; e.on = on
            // A reader's own toggle on: the pulse runs out to it, then it blooms and throws sparks.
            if (on && !was && old?.step == Step.TOPICS && s.step == Step.TOPICS) {
                engine.pulse(e, Rose)
                engine.bloom(node, Rose400, 600)
                engine.sparks(node, 3, 620)
            }
        }
    }

    private fun sources(s: OnboardingState, old: OnboardingState?) {
        val list = vm.sourceNodes(s)
        val keep = list.map { "s:" + it.key }.toSet()
        engine.nodes.keys.filter { it.startsWith("s:") && it !in keep }.forEach(engine::removeNode)
        // Each source sits near its topics (the circular mean of their angles), spaced evenly.
        data class P(val key: String, val angle: Float)
        val placed = list.map { src ->
            val angles = src.topics.mapNotNull { engine.nodes["t:$it"]?.angle }
            val a = if (angles.isEmpty()) (PI / 2).toFloat() else atan2(angles.sumOf { sin(it).toDouble() }, angles.sumOf { cos(it).toDouble() }).toFloat()
            P(src.key, a)
        }.sortedBy { it.angle }
        val n = placed.size
        engine.sourceScale = (28f / maxOf(n, 1)).coerceIn(.6f, 1f)
        val start = placed.firstOrNull()?.angle ?: 0f
        val ed = engine.nodes.getValue("ed")
        placed.forEachIndexed { i, p ->
            val src = list.first { it.key == p.key }
            val id = "s:" + src.key
            val isNew = id !in engine.nodes
            val node = engine.node(id, NodeKind.SOURCE)
            node.label = src.name
            val a = start + i * 2 * PI.toFloat() / maxOf(n, 1)
            node.angle = a
            val tx = cos(a) * R_SOURCES; val ty = sin(a) * R_SOURCES * 1.02f
            val from = src.topics.firstOrNull()?.let { engine.nodes["t:$it"] } ?: ed
            val links = src.topics.mapNotNull { engine.nodes["t:$it"] }.ifEmpty { listOf(ed) }
            val edges = links.map { engine.edge(it, node, EdgeKind.SOURCE, .1f) }
            if (isNew) {
                node.x = from.x; node.y = from.y
                node.moveTo(tx, ty, engine.now, d(650), d(18L * i))
                node.appearAt = engine.now + d(18L * i)
            } else if (node.tx != tx || node.ty != ty) node.moveTo(tx, ty, engine.now, d(650))
            node.visible = true
            val on = src.key in s.choices.sources
            val was = old?.choices?.sources?.contains(src.key) == true
            node.on = on
            edges.forEach { it.on = on }
            if (on && !was && old?.step == Step.SOURCES && s.step == Step.SOURCES) {
                edges.forEach { engine.pulse(it, Indigo400, 520) }
                engine.bloom(node, Indigo400, 480)
            }
        }
    }

    private fun brief(s: OnboardingState, entering: Boolean) {
        val b = engine.node("brief", NodeKind.BRIEF)
        b.label = briefLabel
        if (!b.visible) { b.x = 0f; b.y = Y_BRIEF; b.moveTo(0f, Y_BRIEF, engine.now, 0); b.visible = true; b.appearAt = engine.now }
        engine.showBriefRings = true
        val topics = s.choices.topics.mapNotNull { engine.nodes["t:$it"] }
        engine.edges.removeAll { it.kind == EdgeKind.BRIEF && it.a !in topics }
        topics.forEachIndexed { i, t ->
            val e = engine.edge(t, b, EdgeKind.BRIEF, -.18f)
            e.on = true
            if (entering) engine.pulse(e, Amber400, 700, d(70L * i))
        }
        if (entering) engine.bloom(b, Amber400, d(topics.size * 70L + 600))
    }

    /** A new sample line: the briefing node answers with amber pulses from every topic. */
    fun presenterChanged() {
        val b = engine.nodes["brief"] ?: return
        engine.edges.filter { it.kind == EdgeKind.BRIEF }.forEachIndexed { k, e -> engine.pulse(e, Amber400, 700, d(50L * k)) }
        engine.bloom(b, Amber400, d(700))
        engine.invalidate()
    }

    /** Focus from a source chip: its node and edges light up. */
    fun focusSource(key: String?) {
        engine.nodes.values.forEach { if (it.kind == NodeKind.SOURCE) it.focus = it.id == "s:$key" }
        engine.edges.forEach { if (it.kind == EdgeKind.SOURCE) it.focus = it.b.id == "s:$key" }
        engine.invalidate()
    }

    /**
     * The finale: pulses run edition → topics → sources → briefing, then the whole network
     * draws in toward the masthead. Returns how long it takes (ms).
     */
    fun cascade(short: Boolean = false): Long {
        // Skip plays the same finale at about a third of the length.
        val f = if (short) .35f else 1f
        fun d(ms: Long) = this.d((ms * f).toLong())
        engine.fit(BOX_PRESENTER, d(700))
        val ed = engine.nodes.getValue("ed")
        engine.bloom(ed, Rose400); engine.bloom(ed, Indigo400, d(120))
        val tEdges = engine.edges.filter { it.kind == EdgeKind.TOPIC && it.on }
        val sEdges = engine.edges.filter { it.kind == EdgeKind.SOURCE && it.on }
        val bEdges = engine.edges.filter { it.kind == EdgeKind.BRIEF }
        var t = 0L
        tEdges.forEachIndexed { i, e -> engine.pulse(e, Rose, 520, d(t + i * 45L)); engine.bloom(e.b, Rose400, d(t + i * 45L + 520)) }
        t += d(tEdges.size * 45L + 560)
        sEdges.take(if (short) 24 else 80).forEachIndexed { i, e -> engine.pulse(e, Indigo400, 480, d(t + i * 12L)) }
        t += d(minOf(sEdges.size, if (short) 24 else 80) * 12L + 520)
        bEdges.forEachIndexed { i, e -> engine.pulse(e, Amber400, 620, d(t + i * 40L)) }
        t += d(bEdges.size * 40L + 650)
        engine.nodes["brief"]?.let { engine.bloom(it, Amber400, t); engine.bloom(it, Rose400, t + d(80)) }
        t += d(250)
        engine.nodes.values.forEach { it.moveTo(it.tx * .08f, it.ty * .08f - 40f, engine.now, d(900), t) }
        engine.fadeOut(d(700), t + d(500))
        engine.invalidate()
        return t + d(1000)
    }

    companion object {
        const val R_TOPICS = 150f
        const val R_SOURCES = 272f
        const val Y_BRIEF = 430f
        val BOX_WELCOME = Rect(-150f, -150f, 150f, 150f)
        val BOX_EDITION = Rect(-150f, -140f, 150f, 150f)
        val BOX_TOPICS = Rect(-185f, -190f, 185f, 200f)
        val BOX_SOURCES = Rect(-290f, -290f, 290f, 290f)
        val BOX_PRESENTER = Rect(-290f, -290f, 290f, 480f)
    }
}

/** The local topic is the city: its id never changes, only its label. */
internal val LOCAL_TOPIC = TopicRegistry.LOCAL_ID
