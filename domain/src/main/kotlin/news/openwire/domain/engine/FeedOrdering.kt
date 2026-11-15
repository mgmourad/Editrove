// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import news.openwire.domain.model.SortMode

/** Sort modes (SPEC §6.1) and notification diffing (SPEC §6.7). */
object FeedOrdering {

    fun apply(articles: List<Article>, mode: SortMode): List<Article> = when (mode) {
        SortMode.CURATED -> articles
        SortMode.NEWEST -> articles.sortedByDescending { it.publishedAt ?: Long.MIN_VALUE }
        SortMode.OLDEST -> articles.sortedBy { it.publishedAt ?: Long.MAX_VALUE }
    }

    /** Stable identity for "have we seen this story" — ids are positional, URLs are not. */
    fun storyKey(a: Article): String =
        FeedWeaver.normalizeArticleUrl(a.originalUrl ?: a.targetUrl).ifEmpty { FeedWeaver.normalizeTitleForComparison(a.title) }

    /**
     * Stories not in [seen]. A null [seen] is the first observation of a topic: it only
     * establishes the baseline, so nothing is reported as new.
     */
    fun newStories(current: List<Article>, seen: Set<String>?): List<Article> =
        if (seen == null) emptyList() else current.filter { storyKey(it) !in seen }
}
