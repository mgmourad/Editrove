// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI
import java.net.URLDecoder
import news.openwire.domain.model.Article
import news.openwire.domain.model.FeedSource

/**
 * Deduplication, fair-share allocation and cadence-paced interleaving of the
 * primary aggregator feed with direct publisher feeds. Port of js/feedWeaver.js.
 */
object FeedWeaver {

    data class Group(val source: FeedSource, val articles: List<Article>)

    data class Options(
        /** Primary stories kept at the top (hero + lead). */
        val leadAnchorCount: Int = 2,
        val maxConsecutivePerDomain: Int = 2,
        /** Insert one secondary story every N primary stories. */
        val cadenceInterval: Int = 2,
        val maxTotalArticles: Int = 60,
    )

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "ref", "source", "cmpid", "mc_cid", "mc_eid", "fbclid", "gclid",
    )

    /** Strips tracking params, fragments, `www.` and trailing slashes for comparison. */
    fun normalizeArticleUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        return try {
            val uri = URI(rawUrl.trim())
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: throw IllegalArgumentException()
            val path = (uri.rawPath ?: "").trimEnd('/')
            val query = uri.rawQuery?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.filterNot { URLDecoder.decode(it.substringBefore('='), "UTF-8") in TRACKING_PARAMS }
                ?.joinToString("&")
                .orEmpty()
            "https://$host$path${if (query.isNotEmpty()) "?$query" else ""}"
        } catch (e: Exception) {
            rawUrl.trim().lowercase().trimEnd('/')
        }
    }

    /** Lowercases, drops a trailing " - Publisher" suffix and punctuation. */
    fun normalizeTitleForComparison(rawTitle: String?): String {
        if (rawTitle.isNullOrEmpty()) return ""
        return rawTitle.lowercase()
            .replace(Regex("\\s+[-–—|]\\s+[a-z0-9\\s.]+$"), "")
            .replace(Regex("[^\\w\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Token Jaccard similarity over words longer than two characters. */
    fun calculateTitleSimilarity(a: String?, b: String?): Double {
        val normA = normalizeTitleForComparison(a)
        val normB = normalizeTitleForComparison(b)
        if (normA == normB) return 1.0
        if (normA.isEmpty() || normB.isEmpty()) return 0.0
        val ta = normA.split(' ').filter { it.length > 2 }.toSet()
        val tb = normB.split(' ').filter { it.length > 2 }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val union = (ta + tb).size
        return if (union == 0) 0.0 else ta.intersect(tb).size.toDouble() / union
    }

    fun isDuplicate(a: Article?, b: Article?, normalizeDomain: (String) -> String): Boolean {
        if (a == null || b == null) return false
        val urlA = normalizeArticleUrl(a.originalUrl ?: a.targetUrl)
        val urlB = normalizeArticleUrl(b.originalUrl ?: b.targetUrl)
        if (urlA.isNotEmpty() && urlA == urlB) return true

        val similarity = calculateTitleSimilarity(a.title, b.title)
        if (similarity >= 0.75) return true

        val domainA = normalizeDomain(a.domain ?: a.source ?: "")
        val domainB = normalizeDomain(b.domain ?: b.source ?: "")
        if (domainA.isNotEmpty() && domainA == domainB) {
            if (similarity >= 0.25) return true
            val wa = normalizeTitleForComparison(a.title).split(' ').filter { it.length >= 4 }.toSet()
            val wb = normalizeTitleForComparison(b.title).split(' ').filter { it.length >= 4 }.toSet()
            if (wa.intersect(wb).size >= 2) return true
        }
        return false
    }

    /** Keeps the richest fields: summary, lead image, and a canonical publisher URL. */
    fun mergeMetadata(primary: Article, secondary: Article): Article {
        var merged = primary
        if (merged.summary.isNullOrEmpty() && !secondary.summary.isNullOrEmpty()) merged = merged.copy(summary = secondary.summary)
        if (merged.imageUrl.isNullOrEmpty() && !secondary.imageUrl.isNullOrEmpty()) merged = merged.copy(imageUrl = secondary.imageUrl)
        val orig = merged.originalUrl
        if ((orig.isNullOrEmpty() || orig.contains("news.google.com")) && !secondary.originalUrl.isNullOrEmpty()) {
            merged = merged.copy(originalUrl = secondary.originalUrl, targetUrl = secondary.targetUrl ?: secondary.originalUrl)
        }
        return merged
    }

    /**
     * Weaves primary (aggregator) stories with capped secondary feed queues:
     * lead anchors first, then one secondary story per [Options.cadenceInterval]
     * primary stories in round-robin, never more than
     * [Options.maxConsecutivePerDomain] in a row from one domain.
     */
    fun weave(
        primary: List<Article>,
        groups: List<Group>,
        normalizeDomain: (String) -> String,
        options: Options = Options(),
    ): List<Article> {
        if (groups.isEmpty()) return primary.take(options.maxTotalArticles)

        val seenUrls = HashSet<String>()
        val pool = ArrayList<Article>()
        for (art in primary) {
            val u = normalizeArticleUrl(art.originalUrl ?: art.targetUrl)
            if (u.isEmpty() || seenUrls.add(u)) pool.add(art)
        }

        val result = ArrayList<Article>()
        repeat(minOf(options.leadAnchorCount, pool.size)) { result.add(pool.removeAt(0)) }

        val queues = ArrayList<ArrayDeque<Article>>()
        for (group in groups) {
            val limit = group.source.maxArticles.takeIf { it > 0 } ?: 8
            val capped = ArrayDeque<Article>()
            for (art in group.articles) {
                if (capped.size >= limit) break
                var dupe = false
                for (i in result.indices) {
                    if (isDuplicate(result[i], art, normalizeDomain)) { result[i] = mergeMetadata(result[i], art); dupe = true; break }
                }
                if (!dupe) {
                    for (i in pool.indices) {
                        if (isDuplicate(pool[i], art, normalizeDomain)) { pool[i] = mergeMetadata(pool[i], art); dupe = true; break }
                    }
                }
                if (!dupe) {
                    val u = normalizeArticleUrl(art.originalUrl ?: art.targetUrl)
                    if (u.isEmpty() || seenUrls.add(u)) capped.addLast(art)
                }
            }
            if (capped.isNotEmpty()) queues.add(capped)
        }

        fun exceedsDomainLimit(candidate: Article): Boolean {
            val n = options.maxConsecutivePerDomain
            if (result.size < n) return false
            val d = normalizeDomain(candidate.domain ?: candidate.source ?: "")
            if (d.isEmpty()) return false
            for (i in 1..n) {
                val prev = result[result.size - i]
                if (normalizeDomain(prev.domain ?: prev.source ?: "") != d) return false
            }
            return true
        }

        var queueIndex = 0
        var primarySinceSecondary = 0
        while ((pool.isNotEmpty() || queues.any { it.isNotEmpty() }) && result.size < options.maxTotalArticles) {
            val hasSecondary = queues.any { it.isNotEmpty() }
            if (hasSecondary && (primarySinceSecondary >= options.cadenceInterval || pool.isEmpty())) {
                var picked: Article? = null
                var searched = 0
                while (searched < queues.size) {
                    val q = queues[queueIndex]
                    queueIndex = (queueIndex + 1) % queues.size
                    searched++
                    if (q.isNotEmpty() && !exceedsDomainLimit(q.first())) { picked = q.removeFirst(); break }
                }
                if (picked == null && pool.isEmpty()) picked = queues.firstOrNull { it.isNotEmpty() }?.removeFirst()
                if (picked != null) {
                    result.add(picked)
                    primarySinceSecondary = 0
                    continue
                }
            }
            if (pool.isNotEmpty()) {
                val idx = pool.indexOfFirst { !exceedsDomainLimit(it) }
                result.add(if (idx >= 0) pool.removeAt(idx) else pool.removeAt(0))
                primarySinceSecondary++
            } else if (!hasSecondary) {
                break
            }
        }
        return result.take(options.maxTotalArticles)
    }
}
