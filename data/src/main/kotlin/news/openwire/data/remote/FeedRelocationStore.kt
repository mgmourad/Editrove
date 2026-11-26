// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import android.content.Context

/** Moved feeds, kept on the phone: once a feed's new address is found, every later fetch uses it. */
class FeedRelocationStore(context: Context) : FeedRelocations {
    private val prefs = context.getSharedPreferences("feed_relocations", Context.MODE_PRIVATE)
    override fun get(url: String): String? = prefs.getString(url, null)
    override fun put(url: String, to: String) { prefs.edit().putString(url, to).apply() }
    override fun remove(url: String) { prefs.edit().remove(url).apply() }
}
