package news.openwire.data.remote

import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.GoogleNewsUrls
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.model.Article

/**
 * Normalizes a raw RSS item into a routed Article, ported from
 * js/newsService.js `normalizeFeedItem` (title/source split, domain resolution).
 */
class RssNormalizer(private val registry: DomainRegistry) {

    fun normalize(item: RssItem, topicId: String, index: Int): Article {
        val link = GoogleNewsUrls.decodeEmbeddedGoogleUrl(item.link).ifEmpty { item.link }
        val (title, source) = parseTitle(item.title)
        val domain = resolveDomain(source, link)

        val base = Article(
            id = "art-$topicId-$index",
            title = title,
            source = source.ifEmpty { domain.ifEmpty { "News" } },
            domain = domain,
            originalUrl = link,
            publishedAt = NewsProcessor.parseDate(item.pubDate),
        )
        return StrategyRouter.applyStrategy(registry, base)
    }

    /** Splits "Headline - Publisher" into (title, source). */
    private fun parseTitle(rawTitle: String?): Pair<String, String> {
        val title = rawTitle?.trim().orEmpty()
        val lastDash = title.lastIndexOf(" - ")
        if (lastDash > 0) {
            return title.substring(0, lastDash).trim() to title.substring(lastDash + 3).trim()
        }
        return title.ifEmpty { "Untitled" } to ""
    }

    private fun resolveDomain(source: String, link: String): String {
        if (source.isNotEmpty() && source.contains('.') && !source.contains(' ')) {
            return registry.normalizeDomain(source)
        }
        if (link.isNotEmpty() && !link.contains("news.google.com")) {
            return registry.normalizeDomain(link)
        }
        val resolvedFromSource = registry.normalizeDomain(source)
        if (resolvedFromSource.isNotEmpty()) return resolvedFromSource
        if (link.isNotEmpty() && !link.contains("news.google.com")) {
            return registry.normalizeDomain(link)
        }
        return ""
    }
}
