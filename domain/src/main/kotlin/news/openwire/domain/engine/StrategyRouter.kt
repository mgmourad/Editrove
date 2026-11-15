package news.openwire.domain.engine

import news.openwire.domain.model.Article
import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.Strategy
import java.net.URLEncoder

/**
 * Paywall routing, ported from js/newsService.js: maps a strategy to a target
 * URL and applies it to an article (status, badge, targetUrl, archiveUrl).
 */
object StrategyRouter {

    fun buildStrategyUrl(strategy: Strategy?, originalUrl: String?): String {
        if (originalUrl.isNullOrEmpty()) return ""
        if (GoogleNewsUrls.isGoogleNewsUrl(originalUrl)) {
            val publisher = GoogleNewsUrls.decodeEmbeddedGoogleUrl(originalUrl)
            return if (publisher.isNotEmpty()) buildStrategyUrl(strategy, publisher) else originalUrl
        }
        return when (strategy) {
            Strategy.DIRECT -> originalUrl
            Strategy.SMRY -> "https://smry.ai/$originalUrl"
            Strategy.REMOVEPAYWALL -> "https://www.removepaywall.com/search?url=${URLEncoder.encode(originalUrl, "UTF-8")}"
            Strategy.UNWALL -> "https://unwall.app/$originalUrl"
            Strategy.FREEDIUM -> "https://freedium-mirror.cfd/$originalUrl"
            Strategy.READER -> originalUrl
            Strategy.ARCHIVE_IS -> "https://archive.is/newest/$originalUrl"
            Strategy.WAYBACK -> "https://web.archive.org/web/*/$originalUrl"
            null -> originalUrl
        }
    }

    fun applyStrategy(registry: DomainRegistry, article: Article, customRule: DomainRule? = null): Article {
        val embedded = GoogleNewsUrls.decodeEmbeddedGoogleUrl(article.originalUrl ?: article.targetUrl)
        var domain = if (embedded.isNotEmpty()) {
            registry.normalizeDomain(embedded)
        } else {
            article.domain?.let { registry.normalizeDomain(it) } ?: ""
        }
        if (domain.isEmpty()) {
            val fallbackUrl = article.originalUrl ?: article.targetUrl ?: ""
            if (fallbackUrl.isNotEmpty() && !fallbackUrl.contains("news.google.com")) {
                domain = registry.normalizeDomain(fallbackUrl)
            }
        }
        if (domain == "news.google.com") domain = ""

        val rule = customRule ?: registry.getRule(domain)
        val originalUrl = embedded.ifEmpty { article.originalUrl ?: article.targetUrl ?: "" }
        val targetUrl = buildStrategyUrl(rule.strategy, originalUrl)

        var status: String
        var badge: String
        var archiveUrl = ""
        if (GoogleNewsUrls.isGoogleNewsUrl(originalUrl)) {
            status = "free"
            badge = "Open source"
        } else {
            when (rule.strategy) {
                Strategy.DIRECT -> { status = "free"; badge = "Free" }
                Strategy.SMRY -> { status = "bypassed"; badge = "SMRY" }
                Strategy.REMOVEPAYWALL -> { status = "bypassed"; badge = "RemovePaywall" }
                Strategy.UNWALL -> { status = "bypassed"; badge = "UnWall" }
                Strategy.FREEDIUM -> { status = "bypassed"; badge = "Freedium" }
                Strategy.READER -> { status = "bypassed"; badge = "Reader" }
                Strategy.ARCHIVE_IS -> { status = "bypassed"; badge = "Archive.is"; archiveUrl = targetUrl }
                Strategy.WAYBACK -> { status = "bypassed"; badge = "Wayback"; archiveUrl = targetUrl }
            }
        }

        return article.copy(
            domain = domain.ifEmpty { article.domain },
            originalUrl = originalUrl,
            targetUrl = targetUrl,
            archiveUrl = archiveUrl,
            status = status,
            badge = badge,
        )
    }
}
