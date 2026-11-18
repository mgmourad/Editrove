// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import news.openwire.domain.opinion.SocialPost
import news.openwire.domain.opinion.XPosts
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads single X posts a story embeds: from X's embed data (text, photos, videos), else its
 * official oEmbed endpoint (text only; do-not-track on, no script). Only the post's id is sent.
 */
class XPostSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build(),
) {
    private val cache = ConcurrentHashMap<String, SocialPost>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    suspend fun post(url: String): SocialPost? {
        val key = XPosts.canonical(url) ?: return null
        cache[key]?.let { return it }
        if (key in missing) return null
        return withContext(Dispatchers.IO) {
            val date = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            XPosts.syndicationUrl(key)?.let(::get)?.let { body ->
                XPosts.parseSyndication(body, key) { ms -> runCatching { date.format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())) }.getOrNull() }
            } ?: get(XPosts.oembedUrl(key))?.let { XPosts.parse(it, key) }
        }.also { if (it != null) cache[key] = it else missing += key }
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Editrove/1.0 (Android news reader)").build())
            .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()
}
