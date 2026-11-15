// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Share target, "OpenWire" in the text-selection menu, and notification taps. */
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
                intent.getStringExtra(EXTRA_TOPIC)?.let { vm.selectTopicId(it) }
                intent.getStringExtra(EXTRA_URL)?.let { vm.openLink(it, title = intent.getStringExtra(EXTRA_TITLE)) }
            }
        }
    }

    companion object {
        const val EXTRA_TOPIC = "news.openwire.extra.TOPIC"
        const val EXTRA_URL = "news.openwire.extra.URL"
        const val EXTRA_TITLE = "news.openwire.extra.TITLE"
    }
}
