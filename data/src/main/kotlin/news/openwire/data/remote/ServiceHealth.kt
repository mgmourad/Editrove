// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import news.openwire.domain.engine.BypassServices
import news.openwire.domain.model.Strategy
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Is a bypass service reachable right now? A quick request to its home page,
 * cached for a few minutes. Bot walls (403/429) still count as up — real
 * browsers get through; timeouts, 5xx and "gone" (404/410) count as down.
 */
class ServiceHealth(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Status(val up: Boolean, val code: Int?, val checkedAt: Long, val error: String? = null)

    private val cache = ConcurrentHashMap<Strategy, Status>()

    fun cached(strategy: Strategy): Status? = cache[strategy]

    suspend fun check(strategy: Strategy, force: Boolean = false): Status {
        val service = BypassServices.service(strategy) ?: return Status(true, null, clock())
        cache[strategy]?.takeIf { !force && clock() - it.checkedAt < 5 * 60_000L }?.let { return it }
        val status = withContext(Dispatchers.IO) {
            try {
                client.newCall(
                    Request.Builder().url(service.homeUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36")
                        .build(),
                ).execute().use { r ->
                    val up = r.code < 400 || r.code == 401 || r.code == 403 || r.code == 429
                    Status(up, r.code, clock(), if (up) null else "HTTP ${r.code}")
                }
            } catch (e: Exception) {
                Status(false, null, clock(), e.message ?: "Unreachable")
            }
        }
        cache[strategy] = status
        return status
    }
}
