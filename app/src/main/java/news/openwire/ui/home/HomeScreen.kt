// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import news.openwire.i18n.tr
import news.openwire.i18n.text
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import news.openwire.OpenWireApp
import news.openwire.R
import news.openwire.data.repository.FeedResult
import news.openwire.domain.model.Article
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Glass

/** Width at which the app morphs from the cover (slab) layout to the unfolded list–detail layout. */
private val ExpandedBreakpoint = 600.dp

/** Default edition (SPEC §6.4). */
private const val EDITION = "Washington, D.C."

private val NavTab.icon: ImageVector
    get() = when (this) {
        NavTab.TODAY -> Icons.Outlined.Newspaper
        NavTab.DISCOVER -> Icons.Outlined.Search
        NavTab.AUDIO -> Icons.AutoMirrored.Outlined.VolumeUp
        NavTab.LIVE -> Icons.Outlined.LiveTv
        NavTab.OPINION -> Icons.Outlined.FormatQuote
        NavTab.SAVED -> Icons.Outlined.BookmarkBorder
    }

/** Feed-side state shared by both postures. */
private class FeedUi(
    val savedIds: Set<String>,
    val likedIds: Set<String>,
    val selectedId: String?,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(vm: HomeViewModel, settingsVm: news.openwire.ui.settings.SettingsViewModel) {
    val app = LocalContext.current.applicationContext as OpenWireApp
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val actions = remember {
        ReaderActions(
            router = app.container.articleRouter,
            extractor = app.container.articleTextExtractor,
            scope = scope,
            onToggleSaved = { vm.toggleSaved(it) },
            onToggleLiked = { vm.toggleLiked(it) },
            onTextScale = { vm.setTextScale(it) },
            loadPost = { app.container.xPosts.post(it) },
        )
    }

    val tab by vm.tab.collectAsState()
    // Live TV pauses when you leave its page (radio keeps playing).
    LaunchedEffect(tab) { if (tab != NavTab.LIVE) app.container.live.pauseVideo() }
    val topicIndex by vm.topicIndex.collectAsState()
    val selected by vm.selected.collectAsState()
    val sheetOpen by vm.sheetOpen.collectAsState()
    val saved by vm.saved.collectAsState()
    val liked by vm.liked.collectAsState()
    val savedIds = remember(saved) { saved.mapTo(HashSet()) { it.id } }

    // One scroll position per tab/topic, shared by both postures so folding keeps your place.
    val gridStates = remember { mutableMapOf<String, LazyGridState>() }
    val stateFor: (NavTab) -> LazyGridState = { t -> gridStates.getOrPut("$t-$topicIndex") { LazyGridState() } }

    // The pager is shared by both postures. The nav highlight follows the pager directly
    // (where the swipe is heading), so it moves with your finger instead of after the page settles.
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab.ordinal) { NavTab.entries.size }
    // Back in the app: quietly bring what's on screen up to date.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { vm.onForeground() }
    val shownTab = NavTab.entries[if (pager.isScrollInProgress) pager.targetPage else pager.currentPage]
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            if (page != vm.tab.value.ordinal) vm.selectTab(NavTab.entries[page])
        }
    }
    // Tab changes from elsewhere (notifications, shared links) move the pager too.
    LaunchedEffect(tab) {
        if (!pager.isScrollInProgress && pager.currentPage != tab.ordinal) pager.animateScrollToPage(tab.ordinal, animationSpec = springTween(420))
    }
    val onSelectTab: (NavTab) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (it == shownTab) scope.launch { stateFor(it).animateScrollToItem(0) }
        else {
            vm.selectTab(it)
            scope.launch { pager.animateScrollToPage(it.ordinal, animationSpec = springTween(420)) }
        }
    }

    val settingsOpen by vm.settingsOpen.collectAsState()
    val linkDialog by vm.linkDialog.collectAsState()
    val themeMode by vm.themeMode.collectAsState()
    val settingsTarget by vm.settingsTarget.collectAsState()
    var whatsNew by remember { mutableStateOf(app.container.changelog.pendingWhatsNew()) }

    val briefing = rememberBriefingPlayer()
    val userSettings by vm.settings.collectAsState()

    val previewLoader: suspend (Article) -> String? = remember {
        { a -> (a.originalUrl ?: a.targetUrl)?.let { app.container.articleTextExtractor.preview(it, a.title) } }
    }
    CompositionLocalProvider(LocalTextScale provides userSettings.textScale, LocalArticlePreview provides previewLoader, LocalImageResolver provides app.container.imageResolver, LocalLogoResolver provides app.container.logoResolver, LocalMarkets provides app.container.markets, LocalBriefingPlayer provides briefing) {
    SharedTransitionLayout {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= ExpandedBreakpoint
            // 50/50 feed/reader split beside the 64dp rail (owner preference over the mock's 38/62).
            val feedWidth = (maxWidth - 64.dp) / 2
            val category = categoryLabel(vm, tab, topicIndex)

            if (expanded && userSettings.unfoldedLayout == news.openwire.domain.model.UnfoldedLayout.FULL) {
                // Full view: rail + wide magazine canvas; stories open as full pages.
                val haze = rememberHazeState()
                val openId = if (sheetOpen) selected?.id else null
                val columns = if (maxWidth >= 1000.dp) 4 else 3
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    AmbientBackdrop()
                    Row(Modifier.fillMaxSize()) {
                        SideRail(
                            shownTab, onSelectTab, onToggleTheme = { vm.toggleTheme(it) }, onSettings = { vm.openSettings() },
                            layout = userSettings.unfoldedLayout, onToggleLayout = { vm.toggleUnfoldedLayout() },
                        )
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            CompositionLocalProvider(LocalImageTransition provides ImageTransition(this@SharedTransitionLayout, null, openId)) {
                                TabFeed(
                                    pager = pager, vm = vm, tab = tab, compact = false, stateFor = stateFor,
                                    ui = FeedUi(savedIds, liked, null),
                                    onOpen = { vm.openArticle(it) },
                                    bottomPadding = 40.dp,
                                    columns = columns, magazine = true, horizontalPadding = 28.dp,
                                )
                            }
                            PaneMiniPlayer(briefing, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
                StatusBarGlass(haze, Modifier.align(Alignment.TopCenter))
                BottomFadeGlass(haze, 24.dp, Modifier.align(Alignment.BottomCenter))
                FullReaderHost(
                    visible = sheetOpen && selected != null,
                    article = selected,
                    category = category,
                    backLabel = tab.label,
                    savedIds = savedIds,
                    likedIds = liked,
                    actions = actions,
                    onClose = { vm.closeSheet() },
                )
            } else if (expanded) {
                val haze = rememberHazeState()
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                AmbientBackdrop()
                Row(Modifier.fillMaxSize()) {
                    SideRail(
                        shownTab, onSelectTab, onToggleTheme = { vm.toggleTheme(it) }, onSettings = { vm.openSettings() },
                        layout = userSettings.unfoldedLayout, onToggleLayout = { vm.toggleUnfoldedLayout() },
                    )
                    Box(Modifier.fillMaxHeight().then(if (tab == NavTab.LIVE) Modifier.weight(1f) else Modifier.width(feedWidth))) {
                        TabFeed(
                            pager = pager, vm = vm, tab = tab, compact = false, stateFor = stateFor,
                            ui = FeedUi(savedIds, liked, selected?.id),
                            onOpen = { vm.showInPane(it) },
                            bottomPadding = 32.dp,
                            onFirstArticle = { vm.ensureSelection(it) },
                        )
                        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(Glass.hairline))
                        PaneMiniPlayer(briefing, Modifier.align(Alignment.BottomCenter))
                    }
                    if (tab != NavTab.LIVE) ReaderPane(
                        article = selected,
                        category = category,
                        saved = selected?.id in savedIds,
                        liked = selected?.id in liked,
                        actions = actions,
                        modifier = Modifier.weight(1f).fillMaxHeight().statusBarsPadding().navigationBarsPadding(),
                    )
                }
                }
                StatusBarGlass(haze, Modifier.align(Alignment.TopCenter))
                BottomFadeGlass(haze, 20.dp, Modifier.align(Alignment.BottomCenter))
            } else {
                val openId = if (sheetOpen) selected?.id else null
                CompositionLocalProvider(LocalImageTransition provides ImageTransition(this@SharedTransitionLayout, null, openId)) {
                    CompactLayout(vm, tab, shownTab, pager, stateFor, FeedUi(savedIds, liked, null), onSelectTab)
                }
                ReaderSheetHost(
                    visible = sheetOpen && selected != null,
                    article = selected,
                    category = category,
                    savedIds = savedIds,
                    likedIds = liked,
                    actions = actions,
                    onClose = { vm.closeSheet() },
                )
            }

            // In-app update notification.
            news.openwire.update.UpdateBanner(app.container.updateManager, onOpen = { vm.openUpdates() }, Modifier.align(Alignment.TopCenter))

            // Settings slide up over either posture.
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically(springTween(480)) { it / 3 } + fadeIn(springTween(300)),
                exit = slideOutVertically(springTween(380)) { it / 3 } + fadeOut(tween(220)),
            ) {
                news.openwire.ui.settings.SettingsScreen(
                    vm = settingsVm,
                    themeMode = themeMode,
                    onTheme = { vm.setTheme(it) },
                    onOpenLink = { vm.showLinkDialog() },
                    onClose = { vm.openSettings(false) },
                    updateManager = app.container.updateManager,
                    changelog = app.container.changelog,
                    startPage = settingsTarget,
                )
            }
            AnimatedVisibility(linkDialog, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                LinkDialog(onOpen = { vm.openLink(it) }, onDismiss = { vm.showLinkDialog(false) })
            }
            // Shown once after an update.
            AnimatedVisibility(whatsNew != null, enter = fadeIn(tween(240)), exit = fadeOut(tween(200))) {
                whatsNew?.let { e -> news.openwire.update.WhatsNewDialog(e) { app.container.changelog.markSeen(); whatsNew = null } }
            }
            // First run of every version (after the tour and What's new): can alerts reach the reader?
            val deliveryCtx = LocalContext.current
            val touring = news.openwire.guide.Guide.showing
            LaunchedEffect(whatsNew == null, touring) {
                if (whatsNew == null && !touring && news.openwire.delivery.DeliveryCheck.shouldPrompt(deliveryCtx)) news.openwire.delivery.DeliveryCheck.showing = true
            }
            val alertsOn = userSettings.notificationsEnabled && (userSettings.breakingEnabled || userSettings.notifyTopics.isNotEmpty())
            val turnOn = {
                app.container.settingsRepository.update { it.copy(notificationsEnabled = true, breakingEnabled = true) }
            }
            val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) turnOn() else news.openwire.delivery.DeliveryCheck.open(deliveryCtx, news.openwire.delivery.DeliveryCheck.notificationSettings(deliveryCtx))
            }
            if (news.openwire.delivery.DeliveryCheck.showing && whatsNew == null && !touring) {
                news.openwire.delivery.DeliveryDialog(
                    alertsOn = alertsOn,
                    onTurnOnAlerts = {
                        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(deliveryCtx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else turnOn()
                    },
                ) { news.openwire.delivery.DeliveryCheck.showing = false; news.openwire.delivery.DeliveryCheck.markChecked(deliveryCtx) }
            }
            // The welcome tour sits over everything, Settings included (first launch, or replayed from the guide).
            if (news.openwire.guide.Guide.showing) {
                val ctx = LocalContext.current
                news.openwire.guide.WelcomeTour(onDone = { news.openwire.guide.Guide.showing = false; news.openwire.guide.Guide.markWelcomed(ctx) })
            }
        }
    }
    }
}

/** Slide-up reader with a shared photo transition and predictive back (drag to peek the feed). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ReaderSheetHost(
    visible: Boolean,
    article: Article?,
    category: String,
    savedIds: Set<String>,
    likedIds: Set<String>,
    actions: ReaderActions,
    onClose: () -> Unit,
) {
    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) backProgress.snapTo(0f) }
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { backProgress.snapTo(it.progress) }
            onClose()
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, springTween(300)) }
            throw e
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(springTween(520)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(springTween(420)) { it } + fadeOut(tween(300, delayMillis = 120)),
    ) {
        // Keep showing the last story while the sheet slides away.
        var shown by remember { mutableStateOf(article) }
        LaunchedEffect(article) { if (article != null) shown = article }
        shown?.let { a ->
            CompositionLocalProvider(LocalImageTransition provides ImageTransition(this@ReaderSheetHost, this, a.id)) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val p = backProgress.value
                        val s = 1f - 0.1f * p
                        scaleX = s; scaleY = s
                        translationY = 40.dp.toPx() * p
                        shape = RoundedCornerShape((36 * p).dp)
                        clip = p > 0f
                    },
                ) {
                    ReaderSheet(
                        article = a, category = category,
                        saved = a.id in savedIds, liked = a.id in likedIds,
                        actions = actions, onClose = onClose,
                    )
                }
            }
        }
    }
}

private fun categoryLabel(vm: HomeViewModel, tab: NavTab, topicIndex: Int): String = when (tab) {
    NavTab.TODAY, NavTab.AUDIO -> vm.topics.getOrNull(topicIndex)?.label ?: vm.topics.first().label
    NavTab.DISCOVER -> tr(R.string.tab_discover)
    NavTab.SAVED -> tr(R.string.tab_saved)
    NavTab.LIVE -> tr(R.string.tab_live)
    NavTab.OPINION -> tr(R.string.tab_opinion)
}

@Composable
private fun CompactLayout(
    vm: HomeViewModel,
    tab: NavTab,
    shownTab: NavTab,
    pager: androidx.compose.foundation.pager.PagerState,
    stateFor: (NavTab) -> LazyGridState,
    ui: FeedUi,
    onSelectTab: (NavTab) -> Unit,
) {
    val haze = rememberHazeState()
    val state = stateFor(tab)
    // Floating pill compacts while scrolling down and restores on scroll up.
    var navCompact by remember { mutableStateOf(false) }
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 60)) navCompact = true
                else if (available.y > 1f) navCompact = false
                return Offset.Zero
            }
        }
    }
    val player = LocalBriefingPlayer.current
    val audioState = stateFor(NavTab.AUDIO)
    val briefingCardVisible by remember(audioState) {
        derivedStateOf { audioState.layoutInfo.visibleItemsInfo.any { (it.key as? String)?.startsWith("briefing-") == true } }
    }
    val showPlayer = player != null && player.active && !(tab == NavTab.AUDIO && briefingCardVisible)
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            AmbientBackdrop()
            TabFeed(
                pager = pager, vm = vm, tab = tab, compact = true, stateFor = stateFor, ui = ui,
                onOpen = { vm.openArticle(it) },
                bottomPadding = if (showPlayer) 200.dp else 128.dp,
            )
        }
        StatusBarGlass(haze, Modifier.align(Alignment.TopCenter))
        BottomFadeGlass(haze, 110.dp, Modifier.align(Alignment.BottomCenter))
        if (player != null) AnimatedVisibility(
            showPlayer,
            enter = slideInVertically(springTween(420)) { it / 2 } + fadeIn(springTween(300)),
            exit = slideOutVertically(springTween(320)) { it / 2 } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingPlayerPill(player, haze, Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 100.dp))
        }
        BottomNavPill(
            selectedTab = shownTab,
            onSelectTab = onSelectTab,
            compact = navCompact,
            haze = haze,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp),
        )
    }
}

/** The scrolling feed for the active tab, cross-fading between tabs. */
@Composable
private fun TabFeed(
    pager: androidx.compose.foundation.pager.PagerState,
    vm: HomeViewModel,
    tab: NavTab,
    compact: Boolean,
    stateFor: (NavTab) -> LazyGridState,
    ui: FeedUi,
    onOpen: (Article) -> Unit,
    bottomPadding: Dp,
    onFirstArticle: (Article) -> Unit = {},
    columns: Int = 2,
    magazine: Boolean = false,
    horizontalPadding: Dp = 16.dp,
) {
    val topicIndex by vm.topicIndex.collectAsState()
    val feeds by vm.feeds.collectAsState()
    val saved by vm.saved.collectAsState()
    val query by vm.query.collectAsState()
    val topicsList by vm.topicsState.collectAsState()
    val topic = topicsList.getOrElse(topicIndex) { topicsList.first() }
    val userSettings by vm.settings.collectAsState()
    val archive by vm.archive.collectAsState()
    val refreshingIds by vm.refreshing.collectAsState()

    val padding = PaddingValues(
        start = horizontalPadding, end = horizontalPadding,
        // Clear of the top glass fade (status bar + 28dp) so the date sits in front of it.
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 38.dp,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomPadding,
    )

    // Swipe left/right between the bottom-bar pages (pager hoisted so the nav can follow it).
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        key = { NavTab.entries[it].name },
    ) { page ->
        val t = NavTab.entries[page]
        val visible: List<Article> = when (t) {
            NavTab.TODAY, NavTab.AUDIO -> (feeds[topic.id] as? FeedResult.Success)?.articles.orEmpty()
            NavTab.SAVED -> saved
            NavTab.LIVE, NavTab.OPINION -> emptyList()
            NavTab.DISCOVER -> {
                val byTopic = remember(feeds) {
                    feeds.mapNotNull { (id, r) -> (r as? FeedResult.Success)?.let { id to it.articles } }.toMap()
                }
                // Front page across every edition: ranked by placement, coverage, freshness and authority.
                // Computed off the main thread, and only once feeds stop arriving for a moment
                // (opening Discover loads every topic, each in several steps).
                val registry = remember { news.openwire.domain.engine.DomainRegistry() }
                val top by produceState(initialValue = emptyList<Article>(), byTopic) {
                    if (value.isNotEmpty()) kotlinx.coroutines.delay(300)
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        news.openwire.domain.engine.FeedRanker.everywhere(byTopic, System.currentTimeMillis(), registry::normalizeDomain)
                    }
                }
                remember(byTopic, top, query) {
                    if (query.isBlank()) top
                    else byTopic.values.flatten().distinctBy { news.openwire.domain.engine.FeedOrdering.storyKey(it) }.filter { a ->
                        a.title.contains(query, ignoreCase = true) || (a.source?.contains(query, ignoreCase = true) == true)
                    }
                }
            }
        }
        LaunchedEffect(t, visible.firstOrNull()?.id) { visible.firstOrNull()?.let(onFirstArticle) }
        if (t == NavTab.LIVE) {
            val app = LocalContext.current.applicationContext as OpenWireApp
            news.openwire.live.LiveScreen(
                padding = padding, live = app.container.live, radio = app.container.radioBrowser,
                appLanguage = news.openwire.domain.engine.Languages.effective(userSettings.language),
                country = userSettings.edition.country,
            )
            return@HorizontalPager
        }
        if (t == NavTab.OPINION) {
            val app = LocalContext.current.applicationContext as OpenWireApp
            val headlines = remember(feeds) { feeds.values.flatMap { (it as? FeedResult.Success)?.articles.orEmpty() } }
            news.openwire.opinion.OpinionScreen(
                // Just under the top glass fade (the page has its own masthead).
                padding = PaddingValues(
                    start = padding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), end = padding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp, bottom = padding.calculateBottomPadding(),
                ),
                source = app.container.opinion, muted = userSettings.opinionMuted,
                appLanguage = news.openwire.domain.engine.Languages.effective(userSettings.language),
                news = headlines,
                followedAuthors = userSettings.followedAuthors, followedAccounts = userSettings.followedAccounts, tabs = userSettings.opinionTabs,
                actions = remember(vm) {
                    news.openwire.opinion.OpinionActions(
                        openArticle = onOpen,
                        openLink = { vm.openLink(it) },
                        toggleAuthor = vm::toggleFollowAuthor, toggleAccount = vm::toggleFollowAccount,
                        addTab = vm::addOpinionTab, removeTab = vm::removeOpinionTab, toggleMute = vm::toggleOpinionMute,
                    )
                },
            )
            return@HorizontalPager
        }

        val isRefreshing = when (t) {
            NavTab.DISCOVER -> refreshingIds.isNotEmpty()
            NavTab.SAVED, NavTab.LIVE, NavTab.OPINION -> false
            else -> topic.id in refreshingIds
        }
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        EditorialGrid(stateFor(t), padding, Modifier.fillMaxSize(), columns = columns) {
            fullRow("masthead") {
                Masthead(userSettings.edition.city, onEdition = { vm.openSettings() }, onSettings = { vm.openSettings() })
            }
            fun feed(articles: List<Article>, category: String, ticker: Boolean, title: String) = feedItems(
                archive = archive,
                articles = articles, category = category, compact = compact,
                selectedId = ui.selectedId, savedIds = ui.savedIds, likedIds = ui.likedIds,
                onOpen = onOpen, onToggleSaved = { vm.toggleSaved(it) }, onToggleLiked = { vm.toggleLiked(it) },
                showTicker = ticker, sectionTitle = title, magazine = magazine,
                marketWatch = ticker && topic.id == "BUSINESS",
            )
            when (t) {
                NavTab.TODAY, NavTab.AUDIO -> {
                    fullRow("pills") { CategoryPills(topicsList, topicIndex, onSelect = { vm.selectTopic(it) }) }
                    when (val r = feeds[topic.id]) {
                        is FeedResult.Success -> {
                            fullRow("controls") {
                                FeedControls(
                                    sortMode = userSettings.sortMode,
                                    updatedAt = r.updatedAt,
                                    refreshing = r.refreshing || topic.id in refreshingIds,
                                    failed = r.failedSources,
                                    onSort = { vm.setSortMode(it) },
                                    onRefresh = { vm.refresh() },
                                )
                            }
                            val ordered = news.openwire.domain.engine.FeedOrdering.apply(r.articles, userSettings.sortMode)
                            if (t == NavTab.AUDIO) fullRow("briefing-${topic.id}") { HeadlineBriefingCard(topic.label, ordered, userSettings.briefing, onSettings = { vm.openSettingsAt("briefing") }, fresh = { vm.freshStories(topic) }) }
                            feed(ordered, topic.label, ticker = t == NavTab.TODAY, title = if (t == NavTab.AUDIO) tr(R.string.feed_listen_next) else tr(R.string.feed_bento))
                        }
                        is FeedResult.Error -> fullRow("err-${topic.id}") {
                            MessageCard(tr(R.string.msg_unable_load, topic.label), r.message, tr(R.string.action_try_again)) { vm.retry(topic) }
                        }
                        is FeedResult.Empty -> fullRow("empty-${topic.id}") {
                            MessageCard(tr(R.string.msg_no_stories), tr(R.string.msg_no_stories_body), tr(R.string.action_refresh)) { vm.retry(topic) }
                        }
                        null, FeedResult.Loading -> skeletonItems()
                    }
                }
                NavTab.DISCOVER -> {
                    fullRow("search") { SearchPill(query, vm::setQuery) }
                    val pastedUrl = news.openwire.domain.engine.SharedUrl.extract(query).takeIf { query.contains('.') && !query.trim().contains(' ') }
                    if (!pastedUrl.isNullOrEmpty()) fullRow("open-link") {
                        MessageCard(tr(R.string.msg_open_link), pastedUrl + "\n" + tr(R.string.msg_open_link_body), tr(R.string.action_open)) { vm.openLink(pastedUrl) }
                    }
                    if (visible.isEmpty()) {
                        fullRow("discover-empty") {
                            if (query.isBlank()) MessageCard(tr(R.string.msg_gathering), tr(R.string.msg_gathering_body))
                            else MessageCard(tr(R.string.msg_no_match, query), tr(R.string.msg_no_match_body))
                        }
                    } else {
                        feed(visible, tr(R.string.tab_discover), ticker = false, title = if (query.isBlank()) tr(R.string.feed_top_across) else tr(R.string.feed_results))
                    }
                }
                NavTab.SAVED -> {
                    if (visible.isEmpty()) {
                        fullRow("saved-empty") {
                            MessageCard(tr(R.string.msg_no_saved), tr(R.string.msg_no_saved_body))
                        }
                    } else {
                        feed(visible, tr(R.string.tab_saved), ticker = false, title = tr(R.string.feed_saved_stories))
                    }
                }
                NavTab.LIVE, NavTab.OPINION -> Unit // drawn by their own screens above
            }
        }
        }
    }
}

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit) {
    val g = Glass
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).glassSurface(CircleShape, fill = g.glassPill, elevation = 8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = g.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(tr(R.string.search_hint), style = sans(14.sp), color = g.textMuted)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = sans(14.sp).copy(color = g.textPrimary),
                cursorBrush = SolidColor(g.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Audio tab: an anchor-read briefing of the current edition (shared player keeps playing while you scroll). */
@Composable
private fun HeadlineBriefingCard(
    topicLabel: String,
    articles: List<Article>,
    prefs: news.openwire.domain.model.BriefingPrefs,
    onSettings: () -> Unit,
    fresh: suspend () -> List<Article>,
) {
    val g = Glass
    val haptics = LocalHapticFeedback.current
    val player = LocalBriefingPlayer.current ?: return
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    val mine = player.title == tr(R.string.briefing_title, topicLabel)
    val playing = mine && player.playing
    val count = minOf(prefs.count, articles.size)
    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.audio_briefing_kicker), style = mono(10.sp, FontWeight.SemiBold, 0.16.sp), color = g.accentSoft, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape).background(g.chipFill).clickable(onClick = onSettings).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Tune, null, tint = g.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${prefs.detail.text} · ${"%.2g".format(prefs.speed).trimEnd('0').trimEnd('.')}×", style = sans(11.sp, FontWeight.Medium), color = g.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when (prefs.detail) {
                news.openwire.domain.model.BriefingDetail.HEADLINES -> tr(R.string.briefing_card_headlines, topicLabel, count)
                news.openwire.domain.model.BriefingDetail.SUMMARIES -> tr(R.string.briefing_card_summaries, topicLabel, count)
                news.openwire.domain.model.BriefingDetail.FULL -> tr(R.string.briefing_card_full, topicLabel, count)
            },
            style = serif(22.sp, FontWeight.Bold, lineHeight = 26.sp), color = g.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (mine && player.progress.isNotBlank()) player.progress + " · " + tr(R.string.briefing_keep_scrolling)
            else tr(R.string.briefing_card_body),
            style = sans(12.sp, lineHeight = 18.sp), color = g.textPrimary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.glowShadow(g.accent).clip(CircleShape).background(g.accent)
                    .clickable(enabled = player.ready && !updating) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (mine) player.toggle()
                        else scope.launch {
                            // Never brief from stale stories: refetch first if they're more than a few minutes old.
                            updating = true
                            val latest = runCatching { fresh() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: articles
                            updating = false
                            player.play(topicLabel, latest)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .animateContentSize(springTween(260)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr(if (updating) R.string.briefing_getting_latest else if (playing) R.string.action_pause else if (mine) R.string.action_resume else R.string.briefing_play), style = sans(12.sp, FontWeight.SemiBold), color = Color.White)
            }
            if (mine) {
                Spacer(Modifier.width(6.dp))
                SkipButton(Icons.Filled.SkipPrevious, tr(R.string.previous_story)) { player.skipPrevious() }
                SkipButton(Icons.Filled.SkipNext, tr(R.string.next_story)) { player.skipNext() }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(16.dp)) { Waveform(playing) }
        }
        // Something is on its way: the latest stories, the voice, or the next story's text.
        androidx.compose.animation.AnimatedVisibility(updating || (mine && player.loading)) {
            SweepBar(Modifier.padding(top = 14.dp))
        }
        if (mine && player.storyTitle != null) {
            Spacer(Modifier.height(10.dp))
            Text(tr(R.string.briefing_now, player.storyTitle.orEmpty()), style = sans(12.sp, FontWeight.Medium), color = g.textPrimary.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Navigation chrome --------------------------------------------------------------

@Composable
private fun BottomNavPill(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
    compact: Boolean,
    haze: HazeState,
    modifier: Modifier = Modifier,
) {
    val g = Glass
    val scale by animateFloatAsState(if (compact) 0.9f else 1f, springTween(380), label = "navScale")
    val alpha by animateFloatAsState(if (compact) 0.85f else 1f, tween(300), label = "navAlpha")
    // Six pages: tighter spacing on narrow (cover) screens so every tab fits.
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 420
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .glassSurface(CircleShape, fill = Color.Transparent, elevation = 16.dp)
            .frostedGlass(haze)
            .padding(horizontal = if (narrow) 6.dp else 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 1.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            val active = tab == selectedTab
            val bg by animateColorAsState(if (active) g.accent else Color.Transparent, springTween(320), label = "navBg")
            val tint by animateColorAsState(if (active) Color.White else g.textPrimary.copy(alpha = 0.7f), springTween(320), label = "navTint")
            Row(
                modifier = Modifier
                    .glowShadow(g.accent, if (active) 8.dp else 0.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = if (active) (if (narrow) 13.dp else 18.dp) else (if (narrow) 10.dp else 14.dp), vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
                AnimatedVisibility(
                    active,
                    enter = expandHorizontally(springTween(360)) + fadeIn(springTween(360)),
                    exit = shrinkHorizontally(springTween(300)) + fadeOut(tween(120)),
                ) {
                    Text(tab.label, style = sans(13.5.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1, modifier = Modifier.padding(start = if (narrow) 6.dp else 8.dp).widthIn(max = 96.dp))
                }
            }
        }
    }
}

@Composable
private fun SideRail(
    selectedTab: NavTab,
    onSelectTab: (NavTab) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onSettings: () -> Unit,
    layout: news.openwire.domain.model.UnfoldedLayout,
    onToggleLayout: () -> Unit,
) {
    val g = Glass
    val systemDark = isSystemInDarkTheme()
    Box(Modifier.width(64.dp).fillMaxHeight()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand),
                contentDescription = "Editrove",
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(24.dp).height(1.dp).background(g.hairline))
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                NavTab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    val bg by animateColorAsState(if (active) g.accent.copy(alpha = 0.15f) else Color.Transparent, springTween(320), label = "railBg")
                    val border by animateColorAsState(if (active) g.accent.copy(alpha = 0.30f) else Color.Transparent, springTween(320), label = "railBorder")
                    val tint by animateColorAsState(if (active) g.accentSoft else g.textPrimary.copy(alpha = 0.6f), springTween(320), label = "railTint")
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(16.dp))
                            .clickable { onSelectTab(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // Split view ↔ Full view.
            val full = layout == news.openwire.domain.model.UnfoldedLayout.FULL
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (full) g.accent.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable(onClick = onToggleLayout),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(full, transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), 0.7f)) togetherWith fadeOut(tween(120)) }, label = "layoutIcon") { f ->
                    Icon(
                        if (f) androidx.compose.material.icons.Icons.Outlined.VerticalSplit else androidx.compose.material.icons.Icons.Outlined.ViewDay,
                        contentDescription = tr(if (f) R.string.cd_split_view else R.string.cd_full_view),
                        tint = if (f) g.accentSoft else g.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Settings, contentDescription = tr(R.string.settings), tint = g.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(16.dp))
            // Where the prototype shows a profile photo: Editrove has no accounts, so this
            // slot toggles Black Glass ↔ Editorial Paper instead.
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape)
                    .clickable { onToggleTheme(systemDark) },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    g.isDark,
                    transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), 0.6f)) togetherWith fadeOut(tween(150)) },
                    label = "themeIcon",
                ) { dark ->
                    Icon(
                        if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = tr(if (dark) R.string.cd_to_paper else R.string.cd_to_glass),
                        tint = if (dark) g.indigo else Amber400,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(g.hairline))
    }
}


/** Quick-URL opener (SPEC §6.5): paste any article link; the paywall engine routes it. */
@Composable
private fun LinkDialog(onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val g = Glass
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        clipboard.getText()?.text?.let { clip -> if (news.openwire.domain.engine.SharedUrl.extract(clip).isNotEmpty()) text = clip }
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.padding(24.dp).fillMaxWidth().widthIn(max = 520.dp)
                .background(g.deviceBg.copy(alpha = 0.94f), RoundedCornerShape(BentoRadius))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
            contentPadding = PaddingValues(22.dp),
        ) {
            Text(tr(R.string.open_link_kicker), style = mono(10.sp, FontWeight.SemiBold, 0.16.sp), color = g.accentSoft)
            Spacer(Modifier.height(4.dp))
            Text(tr(R.string.open_link_title), style = serif(22.sp, FontWeight.Bold), color = g.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(tr(R.string.open_link_body), style = sans(12.sp, lineHeight = 18.sp), color = g.textSecondary)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(16.dp), fill = g.glassPill, elevation = 0.dp).padding(14.dp)) {
                if (text.isEmpty()) Text("https://…", style = sans(14.sp), color = g.textMuted)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = sans(14.sp).copy(color = g.textPrimary), cursorBrush = SolidColor(g.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(g.chipFill).clickable(onClick = onDismiss).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.action_cancel), style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary) }
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(g.accent).clickable { onOpen(text) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(tr(R.string.action_open), style = sans(13.sp, FontWeight.SemiBold), color = Color.White) }
            }
        }
    }
}


/** Unfolded posture: the mini-player floats at the bottom of the feed pane. */
@Composable
private fun PaneMiniPlayer(player: BriefingPlayer, modifier: Modifier) {
    AnimatedVisibility(
        player.active,
        enter = slideInVertically(springTween(420)) { it } + fadeIn(),
        exit = slideOutVertically(springTween(320)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        FloatingPlayerPill(player, haze = null, modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp))
    }
}
