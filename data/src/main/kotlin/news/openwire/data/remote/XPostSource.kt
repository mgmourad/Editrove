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
 * Reads single X posts through X's official oEmbed endpoint (do-not-track on, no script),
 * for posts a story embeds. Only the post's address is sent.
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
            runCatching {
                client.newCall(Request.Builder().url(XPosts.oembedUrl(key)).header("User-Agent", "Editrove/1.0 (Android news reader)").build())
                    .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
            }.getOrNull()?.let { XPosts.parse(it, key) }
        }.also { if (it != null) cache[key] = it else missing += key }
    }
}
