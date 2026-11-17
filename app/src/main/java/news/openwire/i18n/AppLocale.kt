// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Editrove's own language, separate from the phone's. Android 13+ keeps it per app (it also
 * shows under the phone's Settings › Apps › Editrove › Language); older versions keep it here
 * and apply it to each screen.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "tag"

    /** "" follows the phone. */
    fun apply(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            Locale.setDefault(if (tag.isEmpty()) LocaleList.getDefault()[0] else Locale.forLanguageTag(tag))
            activity.recreate()
        }
    }

    /** The language picked in the phone's own per-app setting (Android 13+), "" for none. */
    fun systemChoice(context: Context): String? {
        if (Build.VERSION.SDK_INT < 33) return null
        val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        return if (list.isEmpty) "" else list[0].language
    }

    /** Wraps a screen's context in the chosen language (Android 12 and older). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return base.createConfigurationContext(config)
    }
}
