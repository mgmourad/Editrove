// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Black Glass (dark) / Editorial Paper (light) preference. Defaults to following the system. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("openwire_appearance", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY, mode.name).apply()
    }

    private companion object { const val KEY = "theme_mode" }
}
