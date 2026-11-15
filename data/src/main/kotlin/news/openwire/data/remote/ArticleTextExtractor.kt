package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

data class ArticleContent(
    val title: String,
    val text: String,
    val imageUrl: String?,
)

/**
 * Pulls an article's full text by fetching the publisher page and extracting
 * the main body with Jsoup. When the page is paywalled (little/no body), it
 * falls back to a reader bypass (smry.ai).
 */
class ArticleTextExtractor(
    private val client: OkHttpClient = OkHttpClient(),
) {

    suspend fun extract(url: String): ArticleContent? {
        val direct = extractFrom(url)
        if (direct != null && direct.text.length > 200) return direct
        return extractFrom("https://smry.ai/$url")
    }

    private suspend fun extractFrom(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            val html = get(url) ?: return@withContext null
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("h1")?.text().orEmpty().ifEmpty { doc.title() }
            val paragraphs = doc.select("article p, main p, .article-body p, p")
                .map { it.text().trim() }
                .filter { it.length > 40 }
                .distinct()
                .take(30)
            val text = paragraphs.joinToString("\n\n")
            if (text.isBlank()) return@withContext null
            val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst("article img")?.attr("src")
            ArticleContent(title = title, text = text, imageUrl = imageUrl)
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}
