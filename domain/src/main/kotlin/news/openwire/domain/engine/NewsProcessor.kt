// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SortDirection { NEWEST, OLDEST }

/**
 * Pure news-processing logic, ported from js/newsService.js: title cleaning,
 * Dice-similarity dedup, date sorting, date parsing, and relative time.
 */
object NewsProcessor {

    private val RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME
    private val RFC_WITH_OFFSET = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss XXX", Locale.ENGLISH)
    private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    /** US timezone abbreviations seen in RSS dates, mapped to UTC offsets. */
    private val TZ_ABBREVIATIONS = mapOf(
        "EDT" to "-04:00", "EST" to "-05:00",
        "CDT" to "-05:00", "CST" to "-06:00",
        "MDT" to "-06:00", "MST" to "-07:00",
        "PDT" to "-07:00", "PST" to "-08:00",
    )

    /** Strips a trailing "- Publisher" suffix and normalizes for comparison. */
    fun normalizeTitleForComparison(title: String?): String {
        if (title.isNullOrBlank()) return ""
        var clean = title.trim()
        val lastDash = clean.lastIndexOf(" - ")
        if (lastDash > 0) clean = clean.substring(0, lastDash).trim()
        return clean
            .lowercase()
            .replace(RX_1, " ")
            .replace(RX_2, " ")
            .trim()
    }

    /** Dice coefficient between two headline strings (0.0 to 1.0). */
    fun titleSimilarity(titleA: String?, titleB: String?): Double {
        val normA = normalizeTitleForComparison(titleA)
        val normB = normalizeTitleForComparison(titleB)
        if (normA.isEmpty() || normB.isEmpty()) return 0.0
        if (normA == normB) return 1.0
        val wordsA = normA.split(' ').filter { it.length > 2 }.toSet()
        val wordsB = normB.split(' ').filter { it.length > 2 }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        val intersection = wordsA.count { it in wordsB }
        return (2.0 * intersection) / (wordsA.size + wordsB.size)
    }

    /** True if two articles are the same story (URL match or high title similarity). */
    fun isDuplicate(a: Article?, b: Article?): Boolean {
        if (a == null || b == null) return false
        val urlA = a.originalUrl
        val urlB = b.originalUrl
        if (urlA != null && urlB != null) {
            val na = urlA.substringBefore('?').trimEnd('/').lowercase()
            val nb = urlB.substringBefore('?').trimEnd('/').lowercase()
            if (na == nb) return true
        }
        return titleSimilarity(a.title, b.title) >= 0.68
    }

    /** Removes duplicate stories, keeping the first occurrence. */
    fun deduplicate(articles: List<Article>): List<Article> {
        val result = mutableListOf<Article>()
        for (article in articles) {
            if (result.none { isDuplicate(it, article) }) result.add(article)
        }
        return result
    }

    fun sortByDate(articles: List<Article>, direction: SortDirection = SortDirection.NEWEST): List<Article> {
        val sorted = articles.sortedWith(compareBy { it.publishedAt ?: 0L })
        return if (direction == SortDirection.NEWEST) sorted.reversed() else sorted
    }

    /**
     * Parses an RSS/ISO date string to epoch millis, or null. Accepts ISO-8601,
     * RFC-1123 (GMT/UT), and RFC-1123 with a US timezone abbreviation (EDT/EST/...).
     */
    fun parseDate(input: String?): Long? {
        if (input.isNullOrBlank()) return null
        val s = input.trim()

        try { return Instant.parse(s).toEpochMilli() } catch (_: Exception) {}
        try { return Instant.from(RFC_1123.parse(s)).toEpochMilli() } catch (_: Exception) {}

        val last = s.substringAfterLast(' ')
        val offset = TZ_ABBREVIATIONS[last.uppercase()]
        if (offset != null) {
            val replaced = s.dropLast(last.length) + offset
            try {
                return OffsetDateTime.parse(replaced, RFC_WITH_OFFSET).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        return null
    }

    /** Formats epoch millis as a relative time string ("5m ago", "Yesterday", ...). */
    fun formatRelativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis(), lang: String = Languages.effective(null)): String {
        if (epochMillis == null) return ""
        val minutes = (now - epochMillis) / 60_000
        if (minutes < 7 * 24 * 60) return Languages.relativeTime(lang, minutes.coerceAtLeast(0))
        return MONTH_DAY.withLocale(java.util.Locale(lang)).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("[^a-z0-9]")
private val RX_2 = Regex("\\s+")
