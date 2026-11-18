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
        if (savedInstanceState == null) handleIntent(intent)
        // A fresh install opened from the launcher starts with the welcome tour.
        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            news.openwire.guide.Guide.shouldWelcome(this)
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
            OpenWireTheme(darkTheme = dark) {
                HomeScreen(vm, settingsVm)
            }
        }
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
                intent.getStringExtra(EXTRA_TOPIC)?.let { vm.selectTopicId(it) }
                // Deep links into Settings (also used for the user guide's screenshots).
                intent.getStringExtra(EXTRA_SETTINGS)?.let { page ->
                    intent.getStringExtra(EXTRA_QUERY)?.let { vm.setSettingsQuery(it) }
                    vm.openSettingsAt(page)
                }
                intent.getStringExtra(EXTRA_BAND)?.let { news.openwire.live.LiveBand.requested = it }
                intent.getStringExtra(EXTRA_URL)?.let { vm.openLink(it, title = intent.getStringExtra(EXTRA_TITLE)) }
                if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
                    intent.removeExtra(EXTRA_AUTOPLAY)
                    vm.selectTopicId("TOP")
                    vm.selectTab(news.openwire.ui.home.NavTab.AUDIO)
                    val player = (application as OpenWireApp).container.briefingPlayer
                    lifecycleScope.launch {
                        // Refresh first, then play right away.
                        val top = vm.topics.firstOrNull { it.id == "TOP" } ?: return@launch
                        val stories = vm.freshStories(top)
                        player.awaitReady()
                        if (stories.isNotEmpty()) player.play(top.label, stories)
                    }
                }
            }
        }
    }

    companion object {
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
