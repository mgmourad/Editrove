package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** OkHttp-backed snapshot fetcher. No CORS in native HTTP, so no proxy layer. */
class NewsRemoteDataSource(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NewsRemote {

    override suspend fun fetchTopic(file: String): List<ArticleDto> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/data/" + file
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            json.decodeFromString<List<ArticleDto>>(body)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://openwire.news/"
    }
}
