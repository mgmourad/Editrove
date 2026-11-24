// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.map
import news.openwire.R
import news.openwire.domain.engine.Languages
import news.openwire.domain.engine.TopicPresets
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.BriefingSlot
import news.openwire.domain.model.Presenter
import news.openwire.domain.onboarding.EditionMode
import news.openwire.domain.onboarding.KeywordTopic
import news.openwire.domain.onboarding.LocationProblem
import news.openwire.domain.onboarding.Onboarding
import news.openwire.domain.onboarding.OnboardingEvent
import news.openwire.domain.onboarding.OnboardingState
import news.openwire.domain.onboarding.Place
import news.openwire.domain.onboarding.Step
import news.openwire.i18n.tr
import news.openwire.ui.home.BentoRadius
import news.openwire.ui.home.InnerRadius
import news.openwire.ui.home.frostedGlass
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.settings.LanguageOfferDialog
import news.openwire.ui.settings.findActivity
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Emerald400
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Indigo
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.Obsidian
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400
import news.openwire.ui.theme.TextPrimaryDark
import news.openwire.ui.theme.TextSecondaryDark

/** Where the step panel sits: a bottom sheet (compact), docked beside the graph, or below a table-top fold. */
private enum class PanelMode { SHEET, SIDE, TABLETOP }

/**
 * First-run setup: the network graph above (or beside) a glass panel with each step's controls.
 * Always the dark Black Glass look; [onFinished] hands over to the front page.
 */
@Composable
fun OnboardingScreen(
    vm: OnboardingViewModel,
    sayLine: (String) -> Unit,
    stopLine: () -> Unit,
    onLanguage: (String) -> Unit,
    onFinished: (OnboardingState) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state by vm.state.collectAsState()
    // The system animation scale: at 0, no pulses or blooms, and camera moves cut instantly.
    val reduce = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val engine = remember { GraphEngine(reduce) }
    val director = remember { GraphDirector(engine, vm, reduce) }
    var a11yVersion by remember { mutableIntStateOf(0) }
    val haze = rememberHazeState()

    // Labels the graph draws (in the app's language).
    val yourEdition = tr(R.string.onb_your_edition)
    val choosePlace = tr(R.string.onb_choose_place)
    val editionOf = tr(R.string.onb_edition_of, "%s")
    director.briefLabel = tr(R.string.onb_briefing_node)
    director.editionLabels = { s ->
        val p = s.choices.edition
        if (p == null || s.editionMode != EditionMode.CONFIRMED) yourEdition to choosePlace
        else p.city to editionOf.replace("%s", p.country)
    }

    LaunchedEffect(state) { director.apply(state); a11yVersion++ }
    // The finale: save, run the cascade, then hand over.
    LaunchedEffect(state.step) {
        if (state.step == Step.FINISHING) {
            stopLine()
            vm.save()
            delay(director.cascade(short = state.skipped))
            vm.dispatch(OnboardingEvent.Finished)
            onFinished(vm.state.value)
        }
    }
    BackHandler(enabled = state.step != Step.WELCOME && state.step != Step.FINISHING) { vm.dispatch(OnboardingEvent.Back) }

    // Fold posture: the hinge's bounds, in window pixels (this screen fills the window).
    val activity = context.findActivity()
    val foldState = remember(activity) {
        if (activity == null) kotlinx.coroutines.flow.flowOf(null)
        else WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }
    }.collectAsState(initial = null)
    val fold = foldState.value

    var frame by remember { mutableLongStateOf(0L) }
    // Frames only while something moves; a still graph lets the UI go idle.
    LaunchedEffect(engine) {
        while (isActive) {
            val busy = withFrameMillis { t -> engine.tick(t).also { if (it) frame = t } }
            if (!busy) engine.awaitChange()
        }
    }

    val tm = rememberTextMeasurer(cacheSize = 64)
    val styles = remember(density) {
        GraphStyles(
            edition = serif(17.sp, FontWeight.Bold).copy(color = TextPrimaryDark),
            mono = mono(10.sp, FontWeight.SemiBold, 0.12.em).copy(color = TextSecondaryDark),
            topic = sans(12.5.sp, FontWeight.Medium).copy(color = TextPrimaryDark),
            source = sans(12.sp, FontWeight.SemiBold).copy(color = TextPrimaryDark, background = Obsidian.copy(alpha = .85f)),
            editionLine = with(density) { 18.dp.toPx() },
        )
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    BoxWithConstraints(Modifier.fillMaxSize().background(Obsidian)) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val tabletop = fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL
        // A vertical hinge (flat or half-open): the panel docks on the far side, clear of it.
        val book = fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL
        val mode = when { tabletop -> PanelMode.TABLETOP; maxWidth >= 600.dp -> PanelMode.SIDE; else -> PanelMode.SHEET }
        val topBarPx = with(density) { 96.dp.toPx() }
        var sheetPx by remember { mutableIntStateOf(0) }
        // The panel's width beside the graph: the far side of a book-posture hinge, else ~40%.
        val sidePanelDp: Dp = when {
            book && fold != null -> with(density) { (wPx - fold.bounds.right).toDp() } - 24.dp
            else -> (maxWidth * 0.42f).coerceIn(320.dp, 460.dp)
        }
        val graphRect = when (mode) {
            PanelMode.SHEET -> Rect(0f, topBarPx, wPx, hPx - sheetPx)
            PanelMode.TABLETOP -> Rect(0f, topBarPx, wPx, fold!!.bounds.top.toFloat())
            PanelMode.SIDE -> {
                val panel = with(density) { (sidePanelDp + 24.dp).toPx() }
                val edge = if (book && fold != null) (if (rtl) fold.bounds.right.toFloat() else fold.bounds.left.toFloat()) else null
                if (rtl) Rect(edge ?: panel, topBarPx, wPx, hPx) else Rect(0f, topBarPx, edge ?: (wPx - panel), hPx)
            }
        }
        // A little air around the network.
        val inset = with(density) { 16.dp.toPx() }
        val view = Rect(graphRect.left + inset, graphRect.top, graphRect.right - inset, graphRect.bottom - inset)
        // Topic labels need room around the ring; once sources are out, only the chosen few show.
        val outer = state.step >= Step.SOURCES
        LaunchedEffect(view, outer) { engine.labelPad = with(density) { (if (outer) 36.dp else 84.dp).toPx() }; engine.setView(view, force = true); a11yVersion++ }

        val aurora = remember(wPx, hPx) {
            Brush.radialGradient(listOf(Rose.copy(alpha = .10f), Indigo.copy(alpha = .06f), Color.Transparent), center = Offset(wPx * .3f, hPx * .25f), radius = maxOf(wPx, hPx) * .8f)
        }
        Canvas(
            Modifier.fillMaxSize().hazeSource(haze).pointerInput(state.step) {
                detectTapGestures { p ->
                    val reach = 28.dp.toPx()
                    when (state.step) {
                        Step.TOPICS -> engine.hit(p, reach, setOf(NodeKind.TOPIC))?.let { vm.dispatch(OnboardingEvent.ToggleTopic(it.id.removePrefix("t:"))) }
                        Step.SOURCES -> engine.hit(p, reach, setOf(NodeKind.SOURCE))?.let { vm.dispatch(OnboardingEvent.ToggleSource(it.id.removePrefix("s:"))) }
                        else -> Unit
                    }
                }
            },
        ) {
            frame // redraw on every frame the engine asks for
            drawRect(aurora)
            engine.draw(this, tm, styles, density.density)
            // The brand mark's rings on the welcome step.
            if (state.step == Step.WELCOME && !reduce) {
                val c = Offset(engine.sx(0f), engine.sy(0f)); val r = 50f * engine.scale.coerceIn(.55f, 1.6f) * density.density
                for (i in 0 until 3) {
                    val k = ((engine.now + i * 930L) % 2800L) / 2800f
                    drawCircle((if (i % 2 == 0) Rose400 else Indigo400).copy(alpha = (1 - k) * .6f), r + r * k, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f * density.density))
                }
            }
        }

        // The brand mark (welcome): the ET mark at the centre of the network.
        AnimatedVisibility(state.step == Step.WELCOME, enter = fadeIn(), exit = fadeOut(tween(250))) {
            val img = ImageBitmapCache.brand()
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
                Image(
                    img, contentDescription = null,
                    modifier = Modifier
                        .absoluteOffset { frame; val s = (92f * engine.scale.coerceIn(.55f, 1.6f) * density.density).roundToInt(); IntOffset((engine.sx(0f) - s / 2).roundToInt(), (engine.sy(0f) - s / 2).roundToInt()) }
                        .size(with(density) { (92f * engine.scale.coerceIn(.55f, 1.6f) * density.density).toDp() })
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = .22f), CircleShape),
                )
            }
        }

        // Every node as a real 48dp target with its name and state (TalkBack, and fingers).
        GraphTargets(engine, state, a11yVersion, vm)

        TopBar(state, onSkip = { vm.dispatch(OnboardingEvent.Skip) })

        val panel: @Composable () -> Unit = {
            StepPanel(state, vm, director, reduce, sayLine, onLanguage)
        }
        val panelShape = when (mode) { PanelMode.SHEET -> RoundedCornerShape(topStart = BentoRadius, topEnd = BentoRadius); else -> RoundedCornerShape(BentoRadius) }
        AnimatedVisibility(state.step != Step.FINISHING && state.step != Step.DONE, enter = fadeIn(), exit = fadeOut(tween(220)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                val panelMod = when (mode) {
                    PanelMode.SHEET -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = with(density) { (hPx * 0.62f).toDp() })
                    PanelMode.TABLETOP -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(with(density) { (hPx - fold!!.bounds.bottom).toDp() })
                    PanelMode.SIDE -> Modifier.align(Alignment.TopEnd).statusBarsPadding().navigationBarsPadding().padding(12.dp).padding(top = 52.dp).width(sidePanelDp)
                }
                Box(
                    panelMod.onSizeChanged { if (mode == PanelMode.SHEET) sheetPx = it.height }
                        .clip(panelShape)
                        .frostedGlass(haze)
                        .background(Glass.glassSurface.copy(alpha = .55f))
                        .border(1.dp, Brush.verticalGradient(listOf(Glass.specularHighlight, Glass.innerBorder)), panelShape),
                ) {
                    Column(
                        Modifier.fillMaxWidth().then(if (mode == PanelMode.SHEET) Modifier.navigationBarsPadding() else Modifier)
                            .verticalScroll(androidx.compose.runtime.key(state.step) { rememberScrollState() }).padding(horizontal = 22.dp, vertical = 20.dp),
                    ) { panel() }
                }
            }
        }
    }
}

/** The bundled brand mark. */
private object ImageBitmapCache {
    @Composable fun brand() = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.ic_brand)
}

@Composable
private fun TopBar(state: OnboardingState, onSkip: () -> Unit) {
    val g = Glass
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Editrove", style = serif(20.sp, FontWeight.Bold), color = g.textPrimary)
        Spacer(Modifier.width(16.dp))
        val idx = Onboarding.PROGRESS.indexOf(state.step)
        AnimatedVisibility(state.step != Step.WELCOME && state.step != Step.FINISHING, modifier = Modifier.weight(1f)) {
            // Five nodes on a line: done ones filled, the current one ringed.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = tr(R.string.onb_step, (idx + 1).coerceAtLeast(1), Onboarding.PROGRESS.size - 1) },
            ) {
                Onboarding.PROGRESS.forEachIndexed { i, _ ->
                    if (i > 0) Box(Modifier.width(18.dp).height(2.dp).background(if (i <= idx) Rose else g.hairline))
                    Box(
                        Modifier.size(if (i == idx) 12.dp else 8.dp).clip(CircleShape)
                            .background(if (i < idx) Rose else if (i == idx) Rose.copy(alpha = .25f) else g.hairline)
                            .then(if (i == idx) Modifier.border(2.dp, Rose, CircleShape) else Modifier),
                    )
                }
            }
        }
        if (state.step == Step.WELCOME) Spacer(Modifier.weight(1f))
        if (state.step != Step.FINISHING) {
            Text(
                tr(R.string.onb_skip), style = sans(14.sp, FontWeight.SemiBold), color = g.textSecondary,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onSkip).padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** Invisible 48dp targets over the graph's nodes: labels and on/off states for TalkBack, and easy taps. */
@Composable
private fun GraphTargets(engine: GraphEngine, state: OnboardingState, version: Int, vm: OnboardingViewModel) {
    val kind = when (state.step) { Step.TOPICS -> NodeKind.TOPIC; Step.SOURCES -> NodeKind.SOURCE; else -> return }
    val density = LocalDensity.current
    val size = 48.dp
    val half = with(density) { (size / 2).toPx() }
    val on = tr(R.string.onb_on); val off = tr(R.string.onb_off)
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft) {
        version // re-place when nodes settle somewhere new
        engine.nodes.values.filter { it.kind == kind && it.visible }.forEach { n ->
            val p = engine.restingScreen(n)
            val toggle = {
                if (kind == NodeKind.TOPIC) vm.dispatch(OnboardingEvent.ToggleTopic(n.id.removePrefix("t:")))
                else vm.dispatch(OnboardingEvent.ToggleSource(n.id.removePrefix("s:")))
            }
            Box(
                Modifier.absoluteOffset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }.size(size)
                    .semantics {
                        contentDescription = n.label
                        role = Role.Checkbox
                        toggleableState = if (n.on) ToggleableState.On else ToggleableState.Off
                        stateDescription = if (n.on) on else off
                        onClick { toggle(); true }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = toggle),
            )
        }
    }
}

// ---- Panels -----------------------------------------------------------------------------

@Composable
private fun StepPanel(
    state: OnboardingState,
    vm: OnboardingViewModel,
    director: GraphDirector,
    reduce: Boolean,
    sayLine: (String) -> Unit,
    onLanguage: (String) -> Unit,
) {
    AnimatedContent(
        state.step,
        transitionSpec = {
            if (reduce) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 12 }) togetherWith fadeOut(tween(160))
        },
        label = "step",
    ) { step ->
        Column(Modifier.fillMaxWidth()) {
            when (step) {
                Step.WELCOME -> WelcomePanel { vm.dispatch(OnboardingEvent.Next) }
                Step.EDITION -> EditionPanel(state, vm, onLanguage)
                Step.TOPICS -> TopicsPanel(state, vm)
                Step.SOURCES -> SourcesPanel(state, vm, director)
                Step.PRESENTER -> PresenterPanel(state, vm, director, reduce, sayLine)
                else -> Unit
            }
        }
    }
}

@Composable
private fun Kicker(text: String) = Text(text.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.18.em), color = Rose400)

@Composable
private fun Title(text: String, big: Boolean = false) = Text(
    text, style = serif(if (big) 34.sp else 27.sp, FontWeight.Bold, lineHeight = if (big) 38.sp else 32.sp), color = Glass.textPrimary,
    modifier = Modifier.padding(top = 8.dp).semantics { heading(); liveRegion = LiveRegionMode.Polite },
)

@Composable
private fun Lede(text: String) = Text(text, style = sans(14.5.sp, lineHeight = 22.sp), color = Glass.textSecondary, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val g = Glass
    Row(
        modifier.heightIn(min = 52.dp).clip(CircleShape)
            .background(if (primary) (if (enabled) Rose else Rose.copy(alpha = .35f)) else g.chipFill)
            .then(if (primary) Modifier else Modifier.border(1.dp, g.hairline, CircleShape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let { Icon(it, null, tint = if (primary) Color.White else g.textPrimary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = sans(15.sp, FontWeight.SemiBold), color = if (primary) Color.White.copy(alpha = if (enabled) 1f else .7f) else g.textPrimary)
    }
}

@Composable
private fun Actions(onBack: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        onBack?.let {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Glass.chipFill).border(1.dp, Glass.hairline, CircleShape).clickable(onClick = it)
                    .semantics { contentDescription = tr(R.string.onb_back) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Glass.textPrimary) }
        }
        content()
    }
}

@Composable
private fun WelcomePanel(onStart: () -> Unit) {
    Kicker(tr(R.string.onb_welcome_kicker))
    Title(tr(R.string.onb_welcome_title), big = true)
    Lede(tr(R.string.onb_welcome_lede))
    Actions(null) { PillButton(tr(R.string.onb_start), primary = true, modifier = Modifier.weight(1f), onClick = onStart) }
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, tint = Glass.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(tr(R.string.onb_privacy), style = sans(12.5.sp), color = Glass.textSecondary)
    }
}

@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    val g = Glass
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(InnerRadius)).background(g.chipFill)
            .border(1.dp, g.hairline, RoundedCornerShape(InnerRadius)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(15.5.sp, FontWeight.SemiBold), color = g.textPrimary)
            Text(sub, style = sans(12.5.sp, lineHeight = 18.sp), color = g.textSecondary)
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, keyboard: KeyboardType = KeyboardType.Text, onDone: () -> Unit = {}) {
    val g = Glass
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(InnerRadius)).background(g.chipFill)
            .border(1.dp, g.hairline, RoundedCornerShape(InnerRadius)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = g.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = sans(15.sp), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, enabled = enabled, singleLine = true,
                textStyle = sans(15.sp).copy(color = g.textPrimary), cursorBrush = SolidColor(Rose),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

/** "Dal" in "Dallas": the typed part highlighted. */
private fun highlight(text: String, q: String): AnnotatedString = buildAnnotatedString {
    val i = if (q.isBlank()) -1 else text.lowercase().indexOf(q.trim().lowercase())
    if (i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(color = Rose400, background = Rose.copy(alpha = .16f))) { append(text.substring(i, i + q.trim().length)) }
    append(text.substring(i + q.trim().length))
}

@Composable
private fun SuggestionRow(title: AnnotatedString, sub: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(15.sp, FontWeight.Medium), color = Glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = sans(12.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun EditionPanel(state: OnboardingState, vm: OnboardingViewModel, onLanguage: (String) -> Unit) {
    val g = Glass
    val context = LocalContext.current
    val back = { vm.cancelLocate(); vm.dispatch(OnboardingEvent.Back) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.locate(context, granted) }
    Kicker(tr(R.string.onb_step, 1, 4))
    Title(tr(R.string.onb_edition_title))
    Lede(tr(R.string.onb_edition_lede))
    Spacer(Modifier.height(6.dp))
    when (state.editionMode) {
        EditionMode.CHOOSE -> {
            ChoiceRow(Icons.Outlined.MyLocation, Rose400, tr(R.string.onb_use_location), tr(R.string.onb_use_location_sub)) {
                vm.dispatch(OnboardingEvent.UseLocation)
                // Coarse location only, asked now (not at launch).
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) vm.locate(context, true)
                else ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            ChoiceRow(Icons.Outlined.Search, Indigo400, tr(R.string.onb_type_city), tr(R.string.onb_type_city_sub)) { vm.dispatch(OnboardingEvent.TypeCity) }
            Actions(back) {}
        }
        EditionMode.LOCATING -> {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Rose)
                Spacer(Modifier.width(12.dp))
                Text(tr(R.string.onb_locating), style = sans(15.sp), color = g.textPrimary)
            }
            Actions(back) { PillButton(tr(R.string.onb_other_way), primary = false, modifier = Modifier.weight(1f)) { vm.cancelLocate(); vm.dispatch(OnboardingEvent.TypeCity) } }
        }
        EditionMode.TYPING -> CityTyping(state, vm, back)
        EditionMode.CONFIRMED -> {
            val p = state.choices.edition ?: return
            val lang = Languages.effective(null)
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(InnerRadius)).background(Rose.copy(alpha = .10f))
                    .border(1.dp, Rose.copy(alpha = .35f), RoundedCornerShape(InnerRadius)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Rose.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.MyLocation, null, tint = Rose400, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.city, style = serif(22.sp, FontWeight.Bold), color = g.textPrimary)
                    Text(tr(R.string.onb_edition_of, Languages.countryName(p.country, lang)), style = mono(11.sp, FontWeight.SemiBold, 0.1.em), color = g.textSecondary)
                }
                Text(
                    tr(R.string.onb_change), style = sans(14.sp, FontWeight.SemiBold), color = Rose400,
                    modifier = Modifier.clip(CircleShape).clickable { vm.dispatch(OnboardingEvent.ChangeEdition) }.padding(horizontal = 12.dp, vertical = 14.dp),
                )
            }
            Actions(back) {
                PillButton(tr(R.string.onb_use_edition), primary = true, modifier = Modifier.weight(1f)) { vm.dispatch(OnboardingEvent.Next) }
            }
            // An edition that reads in another language offers it (the same question Settings asks).
            var offered by rememberSaveable { mutableStateOf<String?>(null) }
            val offer = Languages.forCountry(p.country)
            if (offer != null && offer.code != lang && offered != p.country) {
                LanguageOfferDialog(
                    offer = offer, current = Languages.of(lang) ?: Languages.ALL.first(),
                    onSwitch = { offered = p.country; onLanguage(offer.code) },
                    onKeep = { offered = p.country },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CityTyping(state: OnboardingState, vm: OnboardingViewModel, back: () -> Unit) {
    val g = Glass
    var q by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(vm.defaultCountry) }
    val lang = Languages.effective(null)
    state.problem?.let {
        Text(
            tr(if (it == LocationProblem.DENIED) R.string.onb_denied else R.string.onb_no_fix),
            style = sans(13.sp, lineHeight = 19.sp), color = Amber400, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
    val suggestions = remember(q) { vm.citySuggestions(q) }
    Field(q, { q = it }, tr(R.string.onb_city_hint), Icons.Outlined.Search, onDone = {
        suggestions.firstOrNull()?.let { vm.dispatch(OnboardingEvent.ChooseEdition(it.place)) }
    })
    Spacer(Modifier.height(6.dp))
    suggestions.forEach { s ->
        SuggestionRow(
            highlight(if (s.place.region.isBlank() || s.place.region == s.place.city || s.place.city.contains(",")) s.place.city else "${s.place.city}, ${s.place.region}", q),
            tr(R.string.onb_country_edition, s.countryName),
            onClick = { vm.dispatch(OnboardingEvent.ChooseEdition(s.place)) },
        )
    }
    // Any other city: live local news for it, in the chosen country's edition.
    val typed = q.trim()
    if (typed.length >= 2 && suggestions.none { it.place.city.equals(typed, true) }) {
        var open by remember { mutableStateOf(false) }
        val cityName = typed.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        SuggestionRow(
            AnnotatedString(tr(R.string.onb_use_typed, cityName)),
            tr(R.string.onb_live_local, Languages.countryName(country, lang)),
            onClick = { vm.dispatch(OnboardingEvent.ChooseEdition(Place(cityName, cityName, country))) },
        ) {
            Box {
                Row(
                    Modifier.heightIn(min = 40.dp).clip(CircleShape).background(g.chipFill).clickable { open = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(country, style = mono(12.sp, FontWeight.SemiBold), color = g.textPrimary)
                    Icon(Icons.Filled.KeyboardArrowDown, tr(R.string.onb_country), tint = g.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 320.dp).background(Obsidian)) {
                    TopicRegistry.COUNTRIES.sortedBy { Languages.countryName(it.code, lang) }.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(Languages.countryName(c.code, lang), style = sans(14.sp), color = if (c.code == country) Rose400 else g.textPrimary) },
                            onClick = { country = c.code; open = false },
                        )
                    }
                }
            }
        }
    }
    Actions(back) {}
}

/** A toggle chip mirrored on the graph: pressing or focusing it highlights its node. */
@Composable
private fun ToggleChip(label: String, on: Boolean, accent: Color = Rose, onFocus: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    val g = Glass
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onFocus(pressed) }
    Row(
        Modifier.heightIn(min = 40.dp).clip(CircleShape)
            .background(if (on) accent.copy(alpha = .18f) else g.chipFill)
            .border(1.dp, if (on) accent.copy(alpha = .6f) else g.hairline, CircleShape)
            .onFocusChanged { onFocus(it.isFocused) }
            .semantics { role = Role.Checkbox; toggleableState = if (on) ToggleableState.On else ToggleableState.Off }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) accent else g.textSecondary.copy(alpha = .5f)))
        Spacer(Modifier.width(8.dp))
        Text(label, style = sans(13.5.sp, if (on) FontWeight.SemiBold else FontWeight.Medium), color = if (on) g.textPrimary else g.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Counter(text: AnnotatedString, fraction: Float) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = sans(13.sp), color = Glass.textSecondary)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Glass.hairline)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Rose))
        }
    }
}

/** "12 of 117 sources": the formatted line with its first number in bold. */
private fun countText(line: String, n: Int) = buildAnnotatedString {
    val i = line.indexOf(n.toString())
    if (i < 0) { append(line); return@buildAnnotatedString }
    append(line.substring(0, i)); withStyle(SpanStyle(color = TextPrimaryDark, fontWeight = FontWeight.Bold)) { append(n.toString()) }
    append(line.substring(i + n.toString().length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicsPanel(state: OnboardingState, vm: OnboardingViewModel) {
    val g = Glass
    val topics = vm.topicNodes(state)
    val n = state.choices.topics.size
    Kicker(tr(R.string.onb_step, 2, 4))
    Title(tr(R.string.onb_topics_title))
    Lede(tr(R.string.onb_topics_lede, state.choices.edition?.city.orEmpty()))
    Counter(countText(tr(R.string.onb_wired_template, n, topics.size), n), n / topics.size.coerceAtLeast(1).toFloat())
    Spacer(Modifier.height(12.dp))
    // Keyword topics: ready-made ones as you type, or the name itself (up to three here).
    var q by rememberSaveable { mutableStateOf("") }
    val full = state.choices.keywordTopics.size >= Onboarding.MAX_KEYWORD_TOPICS
    Field(q, { q = it.take(28) }, tr(if (full) R.string.onb_add_topic_full else R.string.onb_add_topic_hint), Icons.Outlined.Search, enabled = !full)
    val presets = remember(q) { TopicPresets.suggest(q, 4) }
    if (q.trim().length >= 2 && !full) {
        presets.forEach { p ->
            SuggestionRow(highlight(p.name, q), p.keywords.take(3).joinToString(", ") + "…", onClick = {
                vm.dispatch(OnboardingEvent.AddKeywordTopic(KeywordTopic(p.name, p.keywords))); q = ""
            })
        }
        SuggestionRow(AnnotatedString(tr(R.string.onb_add_free, q.trim())), tr(R.string.onb_add_free_sub), onClick = {
            val name = q.trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
            vm.dispatch(OnboardingEvent.AddKeywordTopic(KeywordTopic(name, emptyList()))); q = ""
        })
    }
    state.choices.keywordTopics.lastOrNull()?.let { k ->
        Text(
            if (k.keywords.isNotEmpty()) tr(R.string.onb_pulls, k.name, k.keywords.take(4).joinToString(", ")) else tr(R.string.onb_pulls_own, k.name),
            style = sans(12.5.sp, lineHeight = 18.sp), color = Emerald400, modifier = Modifier.padding(top = 10.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    // The same topics as the map, as a list (and for TalkBack).
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        topics.forEach { t ->
            ToggleChip(t.label, t.id in state.choices.topics, accent = when { t.local -> Indigo400; t.keyword -> Emerald400; else -> Rose }) {
                vm.dispatch(OnboardingEvent.ToggleTopic(t.id))
            }
        }
    }
    if (n < Onboarding.MIN_TOPICS) Text(tr(R.string.onb_min_topics), style = sans(12.5.sp), color = Amber400, modifier = Modifier.padding(top = 10.dp))
    Actions({ vm.dispatch(OnboardingEvent.Back) }) {
        PillButton(tr(R.string.onb_continue), primary = true, enabled = n >= Onboarding.MIN_TOPICS, modifier = Modifier.weight(1f)) { vm.dispatch(OnboardingEvent.Next) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourcesPanel(state: OnboardingState, vm: OnboardingViewModel, director: GraphDirector) {
    val g = Glass
    val sources = vm.sourceNodes(state)
    val keys = sources.map { it.key }
    val n = keys.count { it in state.choices.sources }
    Kicker(tr(R.string.onb_step, 3, 4))
    Title(tr(R.string.onb_sources_title))
    Lede(tr(R.string.onb_sources_lede))
    Counter(countText(tr(R.string.onb_sources_template, n, vm.librarySize), n), n / vm.librarySize.coerceAtLeast(1).toFloat())
    val all = keys.isNotEmpty() && keys.all { it in state.choices.sources }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
        ToggleChip(tr(if (all) R.string.onb_clear_all else R.string.onb_select_all), on = false) { vm.dispatch(OnboardingEvent.SetSources(keys, !all)) }
    }
    Spacer(Modifier.height(10.dp))
    val topics = vm.topicNodes(state).associateBy { it.id }
    val groups = state.choices.topics.filter { !it.startsWith(news.openwire.domain.onboarding.KEYWORD_PREFIX) }
        .mapNotNull { id -> topics[id]?.let { t -> t to sources.filter { id in it.topics } } }
    groups.forEach { (t, list) ->
        if (list.isEmpty()) return@forEach
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.label.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Indigo400, modifier = Modifier.weight(1f))
            Text(tr(R.string.onb_in_library, list.size), style = sans(11.5.sp), color = g.textSecondary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { s ->
                ToggleChip(s.name, s.key in state.choices.sources, accent = Indigo400, onFocus = { f -> director.focusSource(if (f) s.key else null) }) {
                    vm.dispatch(OnboardingEvent.ToggleSource(s.key))
                }
            }
        }
    }
    val added = sources.filter { it.added }
    if (added.isNotEmpty()) {
        Text(tr(R.string.onb_your_feeds).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Indigo400, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            added.forEach { s -> ToggleChip(s.name, s.key in state.choices.sources, accent = Indigo400) { vm.dispatch(OnboardingEvent.ToggleSource(s.key)) } }
        }
    }
    // Local and keyword topics read news search: no sources needed.
    if (state.choices.topics.any { id -> id.startsWith(news.openwire.domain.onboarding.KEYWORD_PREFIX) || sources.none { id in it.topics } }) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.RssFeed, null, tint = g.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr(R.string.onb_no_sources_note), style = sans(12.5.sp, lineHeight = 18.sp), color = g.textSecondary)
        }
    }
    Spacer(Modifier.height(14.dp))
    var url by rememberSaveable { mutableStateOf("") }
    val check by vm.feedCheck.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Field(url, { url = it }, tr(R.string.onb_feed_hint), Icons.Outlined.RssFeed, keyboard = KeyboardType.Uri, onDone = { if (url.isNotBlank()) vm.addFeed(url) }) }
        Spacer(Modifier.width(8.dp))
        if (check is OnboardingViewModel.FeedCheck.Checking) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Indigo400)
        else PillButton(tr(R.string.onb_add), primary = false) { if (url.isNotBlank()) { vm.addFeed(url); url = "" } }
    }
    (check as? OnboardingViewModel.FeedCheck.Failed)?.let { Text(it.reason, style = sans(12.sp, lineHeight = 17.sp), color = Amber400, modifier = Modifier.padding(top = 6.dp)) }
    Actions({ director.focusSource(null); vm.dispatch(OnboardingEvent.Back) }) {
        PillButton(tr(R.string.onb_continue), primary = true, modifier = Modifier.weight(1f)) { director.focusSource(null); vm.dispatch(OnboardingEvent.Next) }
    }
}

internal fun presenterName(p: Presenter) = tr(when (p) {
    Presenter.ANCHOR -> R.string.presenter_anchor
    Presenter.MODERN -> R.string.presenter_modern
    Presenter.CORRESPONDENT -> R.string.presenter_correspondent
    Presenter.CONVERSATIONAL -> R.string.presenter_conversational
})

private fun presenterHint(p: Presenter) = tr(when (p) {
    Presenter.ANCHOR -> R.string.presenter_anchor_hint
    Presenter.MODERN -> R.string.presenter_modern_hint
    Presenter.CORRESPONDENT -> R.string.presenter_correspondent_hint
    Presenter.CONVERSATIONAL -> R.string.presenter_conversational_hint
})

internal fun slotShort(k: BriefingSlot.Kind) = tr(when (k) {
    BriefingSlot.Kind.MORNING -> R.string.onb_morning
    BriefingSlot.Kind.AFTERNOON -> R.string.onb_afternoon
    BriefingSlot.Kind.EVENING -> R.string.onb_evening
})

internal fun slotTime(s: BriefingSlot): String =
    java.time.LocalTime.of(s.minuteOfDay / 60, s.minuteOfDay % 60).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

/** A presenter's little waveform (moves only when it's the chosen one). */
@Composable
private fun Wave(p: Presenter, on: Boolean, reduce: Boolean) {
    val bars = when (p) {
        Presenter.ANCHOR -> floatArrayOf(.5f, .7f, .6f, .75f, .55f, .7f, .6f, .65f)
        Presenter.MODERN -> floatArrayOf(.7f, .95f, .5f, .9f, .6f, 1f, .55f, .9f)
        Presenter.CORRESPONDENT -> floatArrayOf(.3f, .8f, .4f, .9f, .35f, .6f, .85f, .3f)
        Presenter.CONVERSATIONAL -> floatArrayOf(.6f, .95f, .5f, .85f, 1f, .6f, .9f, .55f)
    }
    val t = if (on && !reduce) androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
        .animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "w").value else .5f
    Canvas(Modifier.width(56.dp).height(18.dp)) {
        val w = size.width / bars.size
        bars.forEachIndexed { i, h ->
            val k = if (on && !reduce) (.55f + .45f * kotlin.math.abs(kotlin.math.sin((t * 3.14f) + i))) else 1f
            val bh = size.height * h * k
            drawLine(if (on) Rose else TextSecondaryDark, Offset(i * w + w / 2, (size.height - bh) / 2), Offset(i * w + w / 2, (size.height + bh) / 2), strokeWidth = w * .5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresenterPanel(state: OnboardingState, vm: OnboardingViewModel, director: GraphDirector, reduce: Boolean, sayLine: (String) -> Unit) {
    val g = Glass
    val context = LocalContext.current
    val c = state.choices
    Kicker(tr(R.string.onb_step, 4, 4))
    Title(tr(R.string.onb_presenter_title))
    Lede(tr(R.string.onb_presenter_lede))
    Spacer(Modifier.height(12.dp))
    // Four presenters, two to a row.
    Presenter.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { p ->
                val on = p == c.presenter
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(InnerRadius))
                        .background(if (on) Rose.copy(alpha = .12f) else g.chipFill)
                        .border(1.dp, if (on) Rose.copy(alpha = .6f) else g.hairline, RoundedCornerShape(InnerRadius))
                        .semantics { role = Role.RadioButton; stateDescription = if (on) tr(R.string.onb_on) else tr(R.string.onb_off) }
                        .clickable { if (!on) { vm.dispatch(OnboardingEvent.SetPresenter(p)); director.presenterChanged() } }
                        .padding(12.dp),
                ) {
                    Wave(p, on, reduce)
                    Spacer(Modifier.height(8.dp))
                    Text(presenterName(p), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                    Text(presenterHint(p), style = sans(11.5.sp, lineHeight = 16.sp), color = g.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    // The opening, as BriefingScript writes it; typed out.
    val line = remember(c.presenter, c.seed) { vm.sampleOpening(state) }
    var shown by remember(line) { mutableIntStateOf(if (reduce) line.length else 0) }
    LaunchedEffect(line) { while (shown < line.length) { delay(18); shown = (shown + 2).coerceAtMost(line.length) } }
    val top = Languages.topicName("TOP", Languages.effective(null)) ?: "Top Stories"
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(InnerRadius)).background(Amber400.copy(alpha = .07f))
            .border(1.dp, Amber400.copy(alpha = .3f), RoundedCornerShape(InnerRadius)).padding(14.dp)
            .semantics { contentDescription = line },
    ) {
        Text(tr(R.string.onb_opens, presenterName(c.presenter), top).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.12.em), color = Amber400)
        Spacer(Modifier.height(6.dp))
        Text("“" + line.take(shown) + if (shown < line.length) "▍" else "”", style = serif(16.sp, FontWeight.Normal, lineHeight = 23.sp), color = g.textPrimary)
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(tr(R.string.onb_play), primary = false, modifier = Modifier.weight(1.2f), icon = Icons.AutoMirrored.Outlined.VolumeUp) { sayLine(line) }
        PillButton(tr(R.string.onb_another), primary = false, modifier = Modifier.weight(1f)) { vm.dispatch(OnboardingEvent.AnotherLine); director.presenterChanged() }
    }
    Text(tr(R.string.onb_when).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = g.textSecondary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
    val slots = vm.slotDefaults
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { s -> ToggleChip(slotShort(s.kind) + " · " + slotTime(s), s.kind in c.slots, accent = Amber400) { vm.dispatch(OnboardingEvent.ToggleSlot(s.kind)) } }
    }
    if (c.slots.isNotEmpty() && !news.openwire.briefing.BriefingReminders.canExact(context)) {
        Text(
            tr(R.string.exact_hint), style = sans(12.sp, FontWeight.Medium, lineHeight = 17.sp), color = Rose400,
            modifier = Modifier.padding(top = 8.dp).clickable {
                runCatching { context.startActivity(android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }
            },
        )
    }
    // Briefing times need notifications (Android 13+): asked here, as Settings does; a "no" keeps the time saved.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.dispatch(OnboardingEvent.Next) }
    Actions({ vm.dispatch(OnboardingEvent.Back) }) {
        PillButton(tr(R.string.onb_build), primary = true, modifier = Modifier.weight(1f)) {
            if (c.slots.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else vm.dispatch(OnboardingEvent.Next)
        }
    }
}
