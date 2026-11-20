// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URLEncoder
import news.openwire.domain.model.Article

/**
 * Breaking-news alerts, separate from topic notifications. Editrove watches the newsrooms
 * the reader picks and flags a story as breaking when the outlet labels it so ("BREAKING:",
 * "Breaking news") or when it is minutes old and already being carried by many outlets.
 */
object BreakingNews {

    data class Outlet(val key: String, val name: String, val domain: String)

    val OUTLETS: List<Outlet> = listOf(
        Outlet("ap", "Associated Press", "apnews.com"),
        Outlet("reuters", "Reuters", "reuters.com"),
        Outlet("bbc", "BBC News", "bbc.com"),
        Outlet("cnn", "CNN", "cnn.com"),
        Outlet("nbc", "NBC News", "nbcnews.com"),
        Outlet("abc", "ABC News", "abcnews.go.com"),
        Outlet("cbs", "CBS News", "cbsnews.com"),
        Outlet("fox", "Fox News", "foxnews.com"),
        Outlet("npr", "NPR", "npr.org"),
        Outlet("nyt", "The New York Times", "nytimes.com"),
        Outlet("wapo", "The Washington Post", "washingtonpost.com"),
        Outlet("wsj", "The Wall Street Journal", "wsj.com"),
        Outlet("bloomberg", "Bloomberg", "bloomberg.com"),
        Outlet("aljazeera", "Al Jazeera", "aljazeera.com"),
        Outlet("guardian", "The Guardian", "theguardian.com"),
        Outlet("politico", "Politico", "politico.com"),
    )

    /** On by default when the reader turns Breaking News on. */
    val DEFAULT_KEYS: Set<String> = setOf("ap", "reuters", "bbc", "cnn", "nbc", "abc", "cbs", "npr")

    /** One Google News query covering every chosen outlet for the last [hours]. */
    fun queryUrl(keys: Set<String>, hours: Int = 2, country: String = "US", language: String = "en"): String? {
        val domains = OUTLETS.filter { it.key in keys }.map { it.domain }
        if (domains.isEmpty()) return null
        val q = "(" + domains.joinToString(" OR ") { "site:$it" } + ") when:${hours}h"
        return "https://news.google.com/rss/search?q=${URLEncoder.encode(q, "UTF-8")}&hl=$language-$country&gl=$country&ceid=$country:$language"
    }

    private val LABEL = Regex("^\\s*(BREAKING( NEWS)?|Breaking( news)?|Developing( story)?|JUST IN|Just in)\\s*[:\\-–—|]", RegexOption.IGNORE_CASE)
    private val WORD = Regex("\\b(breaking news|breaking story|breaking:)\\b", RegexOption.IGNORE_CASE)

    /** Why a story counts as breaking, or null. */
    fun reason(a: Article, nowMillis: Long): String? {
        val age = a.publishedAt?.let { (nowMillis - it) / 60_000 } ?: return null
        if (age > 120 || age < -10) return null
        if (LABEL.containsMatchIn(a.title) || WORD.containsMatchIn(a.title)) return "labelled breaking"
        // Minutes old and already everywhere: Google's full-coverage cluster is large.
        if (age <= 45 && (a.coverage ?: 0) >= 5) return "widely reported"
        return null
    }

    /**
     * The breaking stories in [articles], newest first, one per story: labelled breaking, or a
     * story that several of the watched newsrooms published within half an hour of each other
     * in the last 45 minutes — how a breaking event actually looks in the feeds.
     */
    fun detect(
        articles: List<Article>,
        nowMillis: Long,
        normalizeDomain: (String) -> String,
        minOutlets: Int = 3,
        /** Whose own "BREAKING" label or coverage count is trusted (the watched newsrooms); others need several outlets. */
        trusted: (Article) -> Boolean = { true },
    ): List<Article> {
        val recent = articles.filter { a -> a.publishedAt?.let { nowMillis - it in -600_000L..(60 * 60_000L) } == true }
        val spread = recent.filter { a ->
            val t = a.publishedAt ?: return@filter false
            if (nowMillis - t > 45 * 60_000L) return@filter false
            val outlets = recent.filter { b ->
                kotlin.math.abs((b.publishedAt ?: 0L) - t) <= 30 * 60_000L && (b === a || StoryIdentity.sameStory(a, b, normalizeDomain))
            }.map { normalizeDomain(it.domain ?: it.source ?: "") }.filter { it.isNotEmpty() }.toSet()
            outlets.size >= minOutlets
        }
        val hits = (articles.filter { trusted(it) && reason(it, nowMillis) != null } + spread).distinctBy { it.id }.sortedByDescending { it.publishedAt ?: 0L }
        val out = ArrayList<Article>()
        for (a in hits) if (out.none { StoryIdentity.sameStory(it, a, normalizeDomain) }) out += a
        return out
    }

    /**
     * Keeps alerts rare enough to mean something: at most [maxPerRun] at a time, and within
     * [cooldownMs] of the last alert only a story [isMajor] (very widely carried, or labelled by
     * a watched newsroom) gets through.
     */
    fun throttle(hits: List<Article>, nowMillis: Long, lastAlertAt: Long, isMajor: (Article) -> Boolean, maxPerRun: Int = 2, cooldownMs: Long = 20 * 60_000L): List<Article> {
        val cooling = nowMillis - lastAlertAt < cooldownMs
        return hits.filter { !cooling || isMajor(it) }.take(maxPerRun)
    }

    /** Distinct outlets carrying [a]'s story within half an hour of it. */
    fun outletCount(a: Article, articles: List<Article>, normalizeDomain: (String) -> String): Int {
        val t = a.publishedAt ?: return 0
        return articles.filter { b -> kotlin.math.abs((b.publishedAt ?: 0L) - t) <= 30 * 60_000L && (b === a || StoryIdentity.sameStory(a, b, normalizeDomain)) }
            .map { normalizeDomain(it.domain ?: it.source ?: "") }.filter { it.isNotEmpty() }.toSet().size
    }

    /** Headline without the "BREAKING:" label (the notification says it already). */
    fun cleanTitle(title: String): String = title.replace(LABEL, "").trim().replaceFirstChar { it.uppercase() }
}

/** Background refresh intervals offered in Settings (minutes); WorkManager's floor is 15. */
val REFRESH_CHOICES = listOf(15, 30, 60, 180, 360)

fun validRefresh(minutes: Int): Int = REFRESH_CHOICES.minByOrNull { kotlin.math.abs(it - minutes) } ?: 60
