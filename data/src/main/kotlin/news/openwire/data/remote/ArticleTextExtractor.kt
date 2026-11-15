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
    /** Where the readable text came from (publisher page or an archive snapshot). */
    val via: TextSource = TextSource.PUBLISHER,
    /** The page the text was read from, for the "read at the source" link. */
    val viaUrl: String? = null,
    /** True when only a teaser could be read (paywall and no snapshot). */
    val partial: Boolean = false,
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

enum class TextSource(val label: String) {
    PUBLISHER("Publisher"),
    WAYBACK("Wayback Machine"),
    ARCHIVE_TODAY("archive.today"),
}

/**
 * Pulls an article's readable text for the in-app reader, falling back through
 * sources that serve real HTML (bypass sites such as smry.ai render client-side,
 * so they cannot be read from here):
 *
 * 1. the publisher page (soft/JS paywalls usually ship the full text);
 * 2. the newest Wayback Machine snapshot (raw `id_` capture);
 * 3. the newest archive.today snapshot.
 *
 * The best complete result wins; otherwise the longest teaser is returned, marked partial.
 */
class ArticleTextExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    private companion object {
        /** Site chrome that leaks into `<p>` tags (comment prompts, newsletter and cookie banners…). */
        val BOILERPLATE = listOf(
            "commenting policy", "sign up for", "newsletter", "subscribe to", "all rights reserved",
            "cookie", "click here", "advertisement", "follow us on", "download our app",
            "javascript", "your browser", "copyright ©", "terms of use", "privacy policy",
        )
        val PAYWALL_HINTS = listOf(
            "subscribe to continue", "to continue reading", "already a subscriber", "subscribers only",
            "create a free account", "this article is for subscribers", "log in to continue", "keep this page open until",
        )
    }

    private fun complete(c: ArticleContent?): Boolean =
        c != null && c.text.length >= 900 && c.paragraphs.size >= 4 &&
            PAYWALL_HINTS.none { c.text.lowercase().contains(it) }

    suspend fun extract(url: String): ArticleContent? {
        val direct = extractFrom(url)?.copy(via = TextSource.PUBLISHER, viaUrl = url)
        if (complete(direct)) return direct
        val wayback = waybackSnapshot(url)?.let { snap -> extractFrom(snap.second)?.copy(via = TextSource.WAYBACK, viaUrl = snap.first) }
        if (complete(wayback)) return wayback.mergeMeta(direct)
        val archiveToday = extractFrom("https://archive.ph/newest/$url")?.copy(via = TextSource.ARCHIVE_TODAY, viaUrl = "https://archive.ph/newest/$url")
        if (complete(archiveToday)) return archiveToday.mergeMeta(direct)
        return listOfNotNull(direct, wayback, archiveToday).maxByOrNull { it.text.length }?.copy(partial = true)
    }

    /** Keeps the publisher's own deck/byline/photo when the text came from an archive. */
    private fun ArticleContent?.mergeMeta(publisher: ArticleContent?): ArticleContent? = this?.copy(
        description = publisher?.description ?: description,
        author = publisher?.author ?: author,
        imageUrl = publisher?.imageUrl ?: imageUrl,
    )

    /** (viewer URL, raw capture URL) of the closest Wayback snapshot, if one exists. */
    private suspend fun waybackSnapshot(url: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val api = "https://archive.org/wayback/available?url=" + java.net.URLEncoder.encode(url, "UTF-8")
            val body = get(api) ?: return@withContext null
            val snap = Regex("\"closest\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: return@withContext null
            val viewer = snap.replaceFirst("http:", "https:")
            val raw = Regex("/web/(\\d+)/").replace(viewer) { "/web/${it.groupValues[1]}id_/" }
            viewer to raw
        } catch (e: Exception) {
            null
        }
    }

    private fun isBoilerplate(p: String): Boolean {
        val l = p.lowercase()
        return BOILERPLATE.any { it in l }
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
