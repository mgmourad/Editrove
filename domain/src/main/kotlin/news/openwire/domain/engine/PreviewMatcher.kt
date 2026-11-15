// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.text.Normalizer
import news.openwire.domain.model.Article

/**
 * Public-excerpt cleaning and keyword matching for custom topics. Port of
 * js/previewService.js: the preview index only enriches stories a topic's own
 * feeds supplied; it never adds stories.
 */
object PreviewMatcher {

    data class Preview(val originalUrl: String?, val googleNewsUrl: String?, val summary: String)

    private val DROP_BLOCKS = Regex("<(script|style|nav|aside|footer|header|form|noscript|ul|ol)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE)
    private val NAMED = mapOf("amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ")

    /** Strips markup (and scripts/menus/lists), decodes entities, caps at 800 chars. */
    fun cleanPreviewText(markup: String?): String {
        if (markup.isNullOrEmpty()) return ""
        val filtered = markup.replace(DROP_BLOCKS, " ").replace(Regex("<!--[\\s\\S]*?-->"), " ").replace(Regex("<[^>]*>"), " ")
        val decoded = filtered
            .replace(Regex("&#(x[\\da-fA-F]+|\\d+);")) { m ->
                val v = m.groupValues[1]
                val code = if (v.startsWith("x", true)) v.drop(1).toIntOrNull(16) else v.toIntOrNull()
                if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
            }
            .replace(Regex("&(amp|quot|apos|lt|gt|nbsp);")) { NAMED[it.groupValues[1]] ?: it.value }
        return decoded.replace(Regex("\\s+"), " ").trim().take(800)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

    /** Plain words/phrases (and `a OR b`); advanced Google operators are left to Google. */
    fun literalKeywords(keywords: List<String>): List<String> = keywords.flatMap { term ->
        if (Regex("\\b(?:AND|NOT)\\b|\\w+:|^-").containsMatchIn(term)) emptyList()
        else term.split(Regex("\\s+OR\\s+")).map { it.trim().removePrefix("\"").removePrefix("“").removeSuffix("\"").removeSuffix("”").trim() }
    }.filter { it.isNotEmpty() }

    /** First keyword found as a whole word/phrase in the title or summary. */
    fun matchKeyword(article: Article, keywords: List<String>): String? {
        val fields = listOf(" ${normalize(article.title)} ", " ${normalize(article.summary.orEmpty())} ")
        for (k in literalKeywords(keywords)) {
            val term = normalize(k)
            if (term.isNotEmpty() && fields.any { it.contains(" $term ") }) return k
        }
        return null
    }

    fun filterKeywordArticles(articles: List<Article>, keywords: List<String>): List<Article> =
        if (literalKeywords(keywords).isEmpty()) articles else articles.filter { matchKeyword(it, keywords) != null }

    fun enrichPreviews(articles: List<Article>, previews: List<Preview>): List<Article> {
        val byUrl = HashMap<String, String>()
        for (p in previews) {
            if (p.summary.isEmpty()) continue
            listOfNotNull(p.originalUrl, p.googleNewsUrl).filter { it.startsWith("http", true) }
                .forEach { byUrl[FeedWeaver.normalizeArticleUrl(it)] = p.summary }
        }
        return articles.map { a ->
            if (!a.summary.isNullOrEmpty()) a
            else a.copy(summary = byUrl[FeedWeaver.normalizeArticleUrl(a.originalUrl)] ?: a.summary)
        }
    }
}
