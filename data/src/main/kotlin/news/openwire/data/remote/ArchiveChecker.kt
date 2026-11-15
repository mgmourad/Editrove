// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ArchiveResult(
    val available: Boolean,
    val archiveUrl: String? = null,
    val error: String? = null,
)

/**
 * Checks archive.is and Wayback availability, ported from js/archiveService.js.
 * Bounded timeouts and an in-memory cache keep checks non-blocking.
 */
class ArchiveChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build(),
    private val json: Json = Json,
) {
    private val cache = mutableMapOf<String, ArchiveResult>()

    suspend fun checkArchiveIs(targetUrl: String?): ArchiveResult = withContext(Dispatchers.IO) {
        if (targetUrl.isNullOrEmpty()) return@withContext ArchiveResult(false)
        cache[targetUrl]?.let { return@withContext it }

        val endpoint = "https://archive.is/timemap/$targetUrl"
        val result = try {
            val body = get(endpoint)
            if (body != null && body.contains("rel=\"memento\"")) {
                ArchiveResult(available = true, archiveUrl = "https://archive.is/newest/$targetUrl")
            } else {
                ArchiveResult(available = false)
            }
        } catch (e: Exception) {
            ArchiveResult(available = false, error = "Timed out or unavailable")
        }
        cache[targetUrl] = result
        result
    }

    suspend fun checkWayback(targetUrl: String?): ArchiveResult = withContext(Dispatchers.IO) {
        if (targetUrl.isNullOrEmpty()) return@withContext ArchiveResult(false)
        val key = "wayback:$targetUrl"
        cache[key]?.let { return@withContext it }

        val endpoint = "https://archive.org/wayback/available?url=${java.net.URLEncoder.encode(targetUrl, "UTF-8")}"
        val result = try {
            val body = get(endpoint)
            if (body == null) {
                ArchiveResult(available = false)
            } else {
                parseWayback(body)
            }
        } catch (e: Exception) {
            ArchiveResult(available = false)
        }
        cache[key] = result
        result
    }

    private fun parseWayback(body: String): ArchiveResult {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val closest = root["archived_snapshots"]?.jsonObject?.get("closest")?.jsonObject
            val available = closest?.get("available")?.jsonPrimitive?.booleanOrNull ?: false
            if (available) {
                val url = closest?.get("url")?.jsonPrimitive?.contentOrNull?.replaceFirst("http:", "https:")
                ArchiveResult(available = true, archiveUrl = url)
            } else {
                ArchiveResult(available = false)
            }
        } catch (e: Exception) {
            ArchiveResult(available = false)
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 2800L
    }
}
