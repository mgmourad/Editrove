package news.openwire.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Reads bundled snapshot assets (offline-first fallback). */
class AssetNewsDataSource(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NewsRemote {

    override suspend fun fetchTopic(file: String): List<ArticleDto> = withContext(Dispatchers.IO) {
        val body = context.assets.open("data/$file").bufferedReader().use { it.readText() }
        json.decodeFromString<List<ArticleDto>>(body)
    }
}
