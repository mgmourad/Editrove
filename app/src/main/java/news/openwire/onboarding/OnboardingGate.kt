// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import news.openwire.domain.model.UserSettings
import news.openwire.domain.onboarding.Onboarding

/**
 * Whether first-run setup should show. Stored: the setup version completed (so a redesign can
 * be shown again by bumping [Onboarding.VERSION]). Anyone who used Editrove before setup
 * existed (an update, or settings changed from the defaults) counts as set up.
 */
object OnboardingGate {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done_version"

    /** True while setup is on screen (first run, or "Run setup again"). */
    var showing by mutableStateOf(false)
    /** Setup was opened from Settings: it starts from the current choices. */
    var rerun by mutableStateOf(false)

    fun shouldShow(context: Context, settings: UserSettings): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val done = p.getInt(KEY_DONE, 0)
        if (done >= Onboarding.VERSION) return false
        if (done == 0) {
            @Suppress("DEPRECATION")
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val updated = info != null && info.lastUpdateTime > info.firstInstallTime + 5_000
            if (Onboarding.isExistingUser(settings, updated)) { markDone(context); return false }
        }
        return true
    }

    fun markDone(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DONE, Onboarding.VERSION).apply()
}
