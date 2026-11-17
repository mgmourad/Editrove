// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.i18n

import android.content.Context
import androidx.annotation.StringRes

/**
 * Editrove's words in Editrove's language, from anywhere (widgets, alerts, view models).
 * Screens restart when the language changes, so a refreshed context is all it takes.
 */
object L10n {
    @Volatile private var context: Context? = null

    fun init(app: Context) { context = AppLocale.wrap(app.applicationContext) }

    /** Empty only in plain JVM tests, where there are no resources (the app sets it up at launch). */
    fun get(@StringRes id: Int, vararg args: Any): String = context?.getString(id, *args).orEmpty()
}

/** Shorthand for [L10n.get]. */
fun tr(@StringRes id: Int, vararg args: Any): String = L10n.get(id, *args)
