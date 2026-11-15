// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class ArticleContent(
    val title: String,
    val text: String,
    val imageUrl: String?,
    /** The publisher's standfirst / deck (og:description), when present. */
    val description: String? = null,
    /** Byline from the page metadata, when present. */
    val author: String? = null,
) {
    val paragraphs: List<String> get() = text.split("\n\n").filter { it.isNotBlank() }
    val wordCount: Int get() = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** Reading time at ~230 wpm, never less than one minute. */
    val readMinutes: Int get() = maxOf(1, (wordCount + 229) / 230)

    /** Narration length at ~160 wpm (typical TTS pace), in seconds. */
    val narrationSeconds: Int get() = maxOf(30, wordCount * 60 / 160)

    /**
     * A sentence suitable for an editorial pull quote: the first direct quotation
     * of reasonable length found in the body (never invented).
     */
    val pullQuote: String?
        get() = QUOTE.findAll(text)
            .map { it.groupValues[1].trim().trimEnd(',', ';', ':', ' ') }
            .firstOrNull { it.length in 50..220 }
            ?.let { "“$it”" }

    private companion object {
        val QUOTE = Regex("[“\"]([^”\"]{50,220})[”\"]")
    }
}

/**
 * Pulls an article's full text by fetching the publisher page and extracting
 * the main body with Jsoup. When the page is paywalled (little/no body), it
 * falls back to a reader bypass (smry.ai).
 */
class ArticleTextExtractor(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private companion object {
        /** Site chrome that leaks into `<p>` tags (comment prompts, newsletter and cookie banners…). */
        val BOILERPLATE = listOf(
            "commenting policy", "sign up for", "newsletter", "subscribe to", "all rights reserved",
            "cookie", "click here", "advertisement", "follow us on", "download our app",
            "javascript", "your browser", "copyright ©", "terms of use", "privacy policy",
        )
    }

    suspend fun extract(url: String): ArticleContent? {
        val direct = extractFrom(url)
        if (direct != null && direct.text.length > 200) return direct
        return extractFrom("https://smry.ai/$url") ?: direct
    }

    private suspend fun extractFrom(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            val html = get(url) ?: return@withContext null
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("h1")?.text().orEmpty().ifEmpty { doc.title() }
            val paragraphs = doc.select("article p, main p, .article-body p, p")
                .map { it.text().trim() }
                .filter { it.length > 40 && !isBoilerplate(it) }
                .distinct()
                .take(30)
            val text = paragraphs.joinToString("\n\n")
            if (text.isBlank()) return@withContext null
            val imageUrl = doc.meta("og:image") ?: doc.selectFirst("article img")?.attr("src")
            ArticleContent(
                title = title,
                text = text,
                imageUrl = imageUrl,
                description = (doc.meta("og:description") ?: doc.meta("description"))
                    ?.takeIf { it.length > 30 },
                author = (doc.meta("author") ?: doc.meta("article:author"))
                    ?.takeIf { it.isNotBlank() && !it.startsWith("http") && it.length < 80 },
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun isBoilerplate(p: String): Boolean {
        val l = p.lowercase()
        return BOILERPLATE.any { it in l }
    }

    private fun Document.meta(key: String): String? =
        (selectFirst("meta[property=$key]") ?: selectFirst("meta[name=$key]"))
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

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
