// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import news.openwire.domain.market.MarketQuote
import news.openwire.domain.market.Markets
import okhttp3.OkHttpClient
import okhttp3.Request

/** Delayed market quotes from Yahoo Finance's public chart endpoint (no account, no key). */
class MarketDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build(),
) {
    @Volatile private var last: Pair<Long, List<MarketQuote>>? = null

    /** The watch list, in order; instruments that fail are skipped. Cached for [maxAgeMs]. */
    suspend fun quotes(maxAgeMs: Long = 30_000): List<MarketQuote> {
        last?.let { (at, q) -> if (System.currentTimeMillis() - at < maxAgeMs && q.isNotEmpty()) return q }
        val fresh = withContext(Dispatchers.IO) {
            coroutineScope {
                Markets.WATCH.map { inst ->
                    async {
                        runCatching {
                            client.newCall(Request.Builder().url(Markets.chartUrl(inst.symbol)).header("User-Agent", "Mozilla/5.0 (Linux; Android 15) Editrove").build())
                                .execute().use { r -> if (r.isSuccessful) r.body?.string()?.let { Markets.parse(it, inst) } else null }
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        }
        if (fresh.isNotEmpty()) last = System.currentTimeMillis() to fresh
        return fresh.ifEmpty { last?.second.orEmpty() }
    }
}
