// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import news.openwire.domain.live.RadioBrowser
import news.openwire.domain.live.Station
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Stations from the community Radio Browser directory. Only the edition's country code or
 * the words typed in the search box are sent; results are kept for the session.
 */
class RadioBrowserSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build(),
) {
    private val cache = ConcurrentHashMap<String, List<Station>>()

    suspend fun country(code: String): List<Station> = get(RadioBrowser.countryPath(code))

    suspend fun search(name: String): List<Station> =
        if (name.isBlank()) emptyList() else get(RadioBrowser.searchPath(name))

    private suspend fun get(path: String): List<Station> {
        cache[path]?.let { return it }
        return withContext(Dispatchers.IO) {
            for (server in RadioBrowser.SERVERS) {
                val body = runCatching {
                    client.newCall(Request.Builder().url(server + path).header("User-Agent", "Editrove/1.0 (Android news reader)").build())
                        .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                }.getOrNull() ?: continue
                return@withContext RadioBrowser.parse(body).also { cache[path] = it }
            }
            emptyList()
        }
    }
}
