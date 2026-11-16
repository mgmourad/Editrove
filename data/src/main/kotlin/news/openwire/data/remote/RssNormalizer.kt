// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.GoogleNewsUrls
import news.openwire.domain.engine.ImageExtractor
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.PreviewMatcher
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.model.Article

/**
 * Normalizes a raw RSS item into a routed Article, ported from
 * js/newsService.js `normalizeFeedItem` (title/source split, domain resolution,
 * image and public excerpt).
 */
class RssNormalizer(private val registry: DomainRegistry) {

    /**
     * @param feedName the direct feed's display name (publisher feeds have no " - Publisher" suffix).
     * @param feedUrl the feed this item came from, for source enable/disable bookkeeping.
     * @param idPrefix distinguishes items from different feeds woven into one topic.
     */
    fun normalize(
        item: RssItem,
        topicId: String,
        index: Int,
        feedName: String? = null,
        feedUrl: String? = null,
        idPrefix: String = "",
    ): Article {
        val link = GoogleNewsUrls.decodeEmbeddedGoogleUrl(item.link).ifEmpty { item.link }
        val isGoogle = GoogleNewsUrls.isGoogleNewsUrl(item.link)
        val (title, suffix) = if (isGoogle || feedName == null) parseTitle(item.title) else item.title.trim() to ""
        val source = item.sourceName ?: suffix.ifEmpty { feedName.orEmpty() }
        val domain = resolveDomain(source, link, item.source)
        val summary = PreviewMatcher.cleanPreviewText(item.description).takeIf { it.length > 20 && !isGoogle }

        val base = Article(
            id = "art-$topicId-${if (idPrefix.isEmpty()) "" else "$idPrefix-"}$index",
            title = title,
            source = source.ifEmpty { domain.ifEmpty { "News" } },
            domain = domain,
            originalUrl = link,
            publishedAt = NewsProcessor.parseDate(item.pubDate),
            imageUrl = item.imageUrl ?: ImageExtractor.fromDescription(item.description, link),
            summary = summary,
            feedUrl = feedUrl,
            // Only the topic's primary (editor-ordered) feed carries prominence; a publisher
            // feed's own first item isn't the day's lead, so it takes its woven position.
            editorialRank = if (feedUrl == null) index else null,
            // Google lists the outlets running the story ("full coverage") as <li> items.
            coverage = if (isGoogle) relatedCoverage(item.description).coerceAtLeast(1) else null,
        )
        return StrategyRouter.applyStrategy(registry, base)
    }

    private fun relatedCoverage(description: String?): Int =
        description?.let { RX_1.findAll(it).count() } ?: 0

    /** Splits "Headline - Publisher" into (title, source). */
    private fun parseTitle(rawTitle: String?): Pair<String, String> {
        val title = rawTitle?.trim().orEmpty()
        val lastDash = title.lastIndexOf(" - ")
        if (lastDash > 0) {
            return title.substring(0, lastDash).trim() to title.substring(lastDash + 3).trim()
        }
        return title.ifEmpty { "Untitled" } to ""
    }

    private fun resolveDomain(source: String, link: String, sourceUrl: String?): String {
        if (!sourceUrl.isNullOrEmpty()) registry.normalizeDomain(sourceUrl).takeIf { it.isNotEmpty() }?.let { return it }
        if (source.isNotEmpty() && source.contains('.') && !source.contains(' ')) {
            return registry.normalizeDomain(source)
        }
        if (link.isNotEmpty() && !link.contains("news.google.com")) {
            return registry.normalizeDomain(link)
        }
        return registry.normalizeDomain(source)
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("<li\\b", RegexOption.IGNORE_CASE)
