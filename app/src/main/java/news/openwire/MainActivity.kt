// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import news.openwire.data.local.ThemeMode
import news.openwire.ui.home.HomeScreen
import news.openwire.ui.home.HomeViewModel
import news.openwire.ui.settings.SettingsViewModel
import news.openwire.ui.theme.OpenWireTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as OpenWireApp).container

    private val vm: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = container.feedRepository,
                    library = container.libraryStore,
                    appearance = container.appearanceStore,
                    settingsRepo = container.settingsRepository,
                    registry = container.domainRegistry,
                    archiveLookup = { container.archiveFor(it) },
                    resolveLink = { container.googleLinks.resolve(it) },
                    loadVideos = { container.shorts.forTopic(it) },
                )
            }
        }
    }

    private val settingsVm: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepo = container.settingsRepository,
                    registry = container.domainRegistry,
                    repository = container.feedRepository,
                    catalog = { container.feedCatalog.catalog },
                    health = container.serviceHealth,
                    finder = container.sourceFinder,
                )
            }
        }
    }

    private val onboardingVm: news.openwire.onboarding.OnboardingViewModel by viewModels {
        viewModelFactory {
            initializer {
                news.openwire.onboarding.OnboardingViewModel(
                    handle = createSavedStateHandle(),
                    settingsRepo = container.settingsRepository,
                    catalog = { container.feedCatalog.catalog },
                    feeds = container.feedRepository,
                    rerun = news.openwire.onboarding.OnboardingGate.rerun,
                )
            }
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(news.openwire.i18n.AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        news.openwire.i18n.L10n.init(this)
        // A language picked in the phone's own per-app setting becomes Editrove's too.
        news.openwire.i18n.AppLocale.systemChoice(this)?.let { sys ->
            if (sys != container.settingsRepository.settings.value.language) container.settingsRepository.update { it.copy(language = sys) }
        }
        // Editrove in another language: make sure the briefing has a voice in it (fetched once).
        container.settingsRepository.settings.value.let { st ->
            val lang = news.openwire.domain.engine.Languages.effective(st.language)
            // …and select it for the briefing (the standard voice for the language).
            lifecycleScope.launch { news.openwire.i18n.VoiceSetup.adopt(this@MainActivity, container.settingsRepository) }
        }
        // Real-time alerts follow their switch (started from here, while the app is in front).
        lifecycleScope.launch {
            container.settingsRepository.settings.collect { st -> news.openwire.realtime.RealtimeAlertService.sync(this@MainActivity, st) }
        }
        settingsVm.onImported = { news.openwire.onboarding.OnboardingGate.markDone(this) }
        if (savedInstanceState == null) handleIntent(intent)
        // First run: setup (which replaces the welcome tour). Kept across recreation (a language switch, a fold).
        val gate = news.openwire.onboarding.OnboardingGate
        if (savedInstanceState?.getBoolean(KEY_SETUP) == true) { gate.showing = true; gate.rerun = savedInstanceState.getBoolean(KEY_RERUN) }
        else if (savedInstanceState == null && gate.shouldShow(this, container.settingsRepository.settings.value)) {
            gate.showing = true
            news.openwire.guide.Guide.markWelcomed(this)
        }
        // The feature tour: once per tour version (people updating see it once too), from the launcher.
        if (!gate.showing && savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            news.openwire.guide.FeatureTour.due(this)
        ) news.openwire.guide.Guide.showing = true
        setContent {
            val mode by vm.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            // Content draws edge-to-edge under transparent system bars; icon contrast follows the theme.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LaunchedEffect(Unit) {
                vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
            val setup = news.openwire.onboarding.OnboardingGate.showing
            var notice by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
            androidx.compose.animation.AnimatedContent(
                setup,
                transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(500)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)) },
                label = "setup",
            ) { onSetup ->
                if (onSetup) {
                    // Setup is always Black Glass.
                    DisposableEffect(Unit) {
                        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
                        onDispose {}
                    }
                    LaunchedEffect(Unit) { if (onboardingVm.state.value.step == news.openwire.domain.onboarding.Step.DONE) onboardingVm.restart(news.openwire.onboarding.OnboardingGate.rerun) }
                    OpenWireTheme(darkTheme = true) {
                        news.openwire.onboarding.OnboardingScreen(
                            vm = onboardingVm,
                            sayLine = { container.briefingPlayer.sayLine(it) },
                            stopLine = { container.briefingPlayer.stopLine() },
                            onLanguage = { tag -> settingsVm.setLanguage(tag); news.openwire.i18n.AppLocale.apply(this@MainActivity, tag) },
                            onFinished = { st -> finishSetup(st)?.let { notice = it } },
                        )
                    }
                } else {
                    OpenWireTheme(darkTheme = dark) {
                        androidx.compose.foundation.layout.Box {
                            HomeScreen(vm, settingsVm)
                            news.openwire.onboarding.SetupNotice(notice, onPlay = { notice = null; playTopBriefing() }, onDismiss = { notice = null })
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SETUP, news.openwire.onboarding.OnboardingGate.showing)
        outState.putBoolean(KEY_RERUN, news.openwire.onboarding.OnboardingGate.rerun)
    }

    /** Setup is done: flags set, and the "briefing is set" line if a time was picked. */
    private fun finishSetup(st: news.openwire.domain.onboarding.OnboardingState): String? {
        val gate = news.openwire.onboarding.OnboardingGate
        gate.markDone(this)
        news.openwire.guide.Guide.markWelcomed(this)
        if (!gate.rerun) container.changelog.markSeen()
        // The delivery check matters only when something will be delivered.
        if (st.choices.slots.isEmpty() && !gate.rerun) news.openwire.delivery.DeliveryCheck.markChecked(this)
        vm.openSettings(false)
        vm.selectTopicId("TOP")
        // Right after first-run setup: the feature tour.
        if (!gate.rerun && news.openwire.guide.FeatureTour.due(this)) news.openwire.guide.Guide.showing = true
        gate.showing = false; gate.rerun = false
        return news.openwire.onboarding.SetupNotice.text(st)
    }

    override fun onStart() {
        super.onStart()
        // Check for a newer release every time the app is opened or returned to (throttled).
        container.updateManager.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Share target, "Open in Editrove" in the text-selection menu, and notification taps. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> vm.openLink(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
            )
            Intent.ACTION_PROCESS_TEXT -> vm.openLink(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            Intent.ACTION_VIEW -> intent.dataString?.let { vm.openLink(it) }
            else -> {
                intent.getStringExtra(EXTRA_TAB)?.let { t -> news.openwire.ui.home.NavTab.entries.firstOrNull { it.name == t }?.let(vm::selectTab) }
                // Opinion alerts open the Analysis & Opinion page; topic alerts their topic.
                intent.getStringExtra(EXTRA_TOPIC)?.let { if (it == "OPINION") vm.selectTab(news.openwire.ui.home.NavTab.OPINION) else vm.selectTopicId(it) }
                // Deep links into Settings (also used for the user guide's screenshots).
                intent.getStringExtra(EXTRA_SETTINGS)?.let { page ->
                    intent.getStringExtra(EXTRA_QUERY)?.let { vm.setSettingsQuery(it) }
                    vm.openSettingsAt(page)
                }
                intent.getStringExtra(EXTRA_BAND)?.let { news.openwire.live.LiveBand.requested = it }
                intent.getStringExtra(EXTRA_URL)?.let { vm.openLink(it, title = intent.getStringExtra(EXTRA_TITLE)) }
                if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
                    intent.removeExtra(EXTRA_AUTOPLAY)
                    playTopBriefing()
                }
            }
        }
    }

    /** Top Stories' briefing: refreshed first, then played (scheduled briefings, setup's notice). */
    private fun playTopBriefing() {
        vm.selectTopicId("TOP")
        vm.selectTab(news.openwire.ui.home.NavTab.AUDIO)
        val player = container.briefingPlayer
        lifecycleScope.launch {
            val top = vm.topics.firstOrNull { it.id == "TOP" } ?: return@launch
            val stories = vm.freshStories(top)
            player.awaitReady()
            if (stories.isNotEmpty()) player.play(top.label, stories)
        }
    }

    companion object {
        private const val KEY_SETUP = "setup_showing"
        private const val KEY_RERUN = "setup_rerun"
        const val EXTRA_TOPIC = "news.openwire.extra.TOPIC"
        const val EXTRA_TAB = "news.openwire.extra.TAB"
        const val EXTRA_SETTINGS = "news.openwire.extra.SETTINGS"
        const val EXTRA_QUERY = "news.openwire.extra.QUERY"
        const val EXTRA_BAND = "news.openwire.extra.BAND"
        const val EXTRA_URL = "news.openwire.extra.URL"
        const val EXTRA_TITLE = "news.openwire.extra.TITLE"
        /** Open the Audio tab and play the latest Top Stories briefing (scheduled briefings). */
        const val EXTRA_AUTOPLAY = "news.openwire.extra.AUTOPLAY"
    }
}
