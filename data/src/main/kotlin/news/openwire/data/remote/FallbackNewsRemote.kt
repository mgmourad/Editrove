package news.openwire.data.remote

import android.util.Log

/** Tries [primary], then [fallback] on any failure. */
class FallbackNewsRemote(
    private val primary: NewsRemote,
    private val fallback: NewsRemote,
) : NewsRemote {

    override suspend fun fetchTopic(file: String): List<ArticleDto> =
        try {
            primary.fetchTopic(file)
        } catch (e: Exception) {
            Log.w(TAG, "Primary fetch failed for $file, using fallback", e)
            fallback.fetchTopic(file)
        }

    companion object {
        private const val TAG = "OpenWire"
    }
}
