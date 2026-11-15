// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.Strategy

/**
 * Maps publisher domains to paywall routing strategies, ported from
 * js/domainRegistry.js. Merges built-in defaults with user overrides from a
 * [DomainRuleStore].
 */
class DomainRegistry(
    private val store: DomainRuleStore = InMemoryDomainRuleStore(),
) {

    /**
     * Resolves the rule for a domain (or publisher name / URL).
     * Match order: exact -> subdomain suffix -> DIRECT fallback.
     */
    fun getRule(domainInput: String): DomainRule {
        val domain = normalizeDomain(domainInput)
        if (domain.isEmpty()) {
            return DomainRule(domain = "unknown", strategy = Strategy.DIRECT, paywall = "unknown", label = "Unknown")
        }

        val custom = store.load()
        val merged = DEFAULT_RULES + custom

        merged[domain]?.let { rule ->
            return rule.copy(domain = domain, isCustom = custom.containsKey(domain))
        }
        for ((knownDomain, rule) in merged) {
            if (domain.endsWith(".$knownDomain")) {
                return rule.copy(domain = domain, matchedDomain = knownDomain, isCustom = custom.containsKey(knownDomain))
            }
        }

        return DomainRule(domain = domain, strategy = Strategy.DIRECT, paywall = "unknown", label = domain, isDefault = true)
    }

    /** All rules (defaults merged with user overrides), for the editor UI. */
    fun getAllRules(): Map<String, DomainRule> {
        val custom = store.load()
        val merged = DEFAULT_RULES.toMutableMap()
        for ((domain, rule) in custom) {
            merged[domain] = rule.copy(isCustom = true)
        }
        return merged
    }

    fun setDomainRule(domainInput: String, strategy: Strategy, paywall: String = "custom", label: String = ""): Boolean {
        val domain = normalizeDomain(domainInput)
        if (domain.isEmpty()) return false
        val custom = store.load().toMutableMap()
        custom[domain] = DomainRule(
            domain = domain,
            strategy = strategy,
            paywall = paywall,
            label = label.ifEmpty { domain },
            isCustom = true,
        )
        store.save(custom)
        return true
    }

    fun deleteDomainRule(domainInput: String): Boolean {
        val domain = normalizeDomain(domainInput)
        val custom = store.load().toMutableMap()
        if (custom.containsKey(domain)) {
            custom.remove(domain)
            store.save(custom)
            return true
        }
        return false
    }

    fun resetToDefaults() {
        store.save(emptyMap())
    }

    /** True if the domain is on the strict Freedium-supported list. */
    fun isFreediumSupported(domainInput: String): Boolean {
        val domain = normalizeDomain(domainInput)
        if (domain.isEmpty()) return false
        return FREEDIUM_SUPPORTED_DOMAINS.any { d -> domain == d || domain.endsWith(".$d") }
    }

    /** Normalizes a URL/hostname/publisher name down to its primary domain. */
    fun normalizeDomain(input: String): String {
        if (input.isBlank()) return ""
        var host = input.trim().lowercase()

        if (host.startsWith("http://") || host.startsWith("https://")) {
            host = try {
                java.net.URI(host).host ?: host
            } catch (e: Exception) {
                host
            }
        }

        host = host.substringBefore(':')

        if (host.startsWith("www.")) {
            host = host.removePrefix("www.")
        }

        if (host.contains(' ') || !host.contains('.')) {
            val resolved = resolvePublisherDomain(host)
            if (resolved.isNotEmpty()) return resolved
            if (!host.contains('.')) return ""
        }

        return host
    }

    /** Resolves a publisher title/name to its official domain, else empty. */
    fun resolvePublisherDomain(input: String): String {
        if (input.isBlank()) return ""
        val clean = input.trim().lowercase()
        KNOWN_PUBLISHER_DOMAINS[clean]?.let { return it }
        val stripped = clean.replace(Regex("[^a-z0-9 ]"), "").replace(Regex("\\s+"), " ").trim()
        KNOWN_PUBLISHER_DOMAINS[stripped]?.let { return it }
        if (clean.contains('.') && !clean.contains(' ') && !clean.contains('/')) return clean
        return ""
    }

    companion object {
        /** Publishers officially supported by Freedium Mirror. */
        val FREEDIUM_SUPPORTED_DOMAINS = listOf(
            "medium.com",
            "nytimes.com",
            "washingtonpost.com",
            "bloomberg.com",
            "reuters.com",
            "economist.com",
            "ft.com",
        )

        /** Default curated registry of publisher domains and their strategies. */
        val DEFAULT_RULES: Map<String, DomainRule> = mapOf(
            // Free / open access (direct)
            "bbc.com" to DomainRule("bbc.com", Strategy.DIRECT, "none", "BBC News"),
            "bbc.co.uk" to DomainRule("bbc.co.uk", Strategy.DIRECT, "none", "BBC News"),
            "npr.org" to DomainRule("npr.org", Strategy.DIRECT, "none", "NPR"),
            "apnews.com" to DomainRule("apnews.com", Strategy.DIRECT, "none", "Associated Press"),
            "reuters.com" to DomainRule("reuters.com", Strategy.DIRECT, "metered", "Reuters"),
            "theguardian.com" to DomainRule("theguardian.com", Strategy.DIRECT, "banner", "The Guardian"),
            "cnn.com" to DomainRule("cnn.com", Strategy.DIRECT, "none", "CNN"),
            "arstechnica.com" to DomainRule("arstechnica.com", Strategy.DIRECT, "none", "Ars Technica"),
            "theverge.com" to DomainRule("theverge.com", Strategy.DIRECT, "none", "The Verge"),
            "techcrunch.com" to DomainRule("techcrunch.com", Strategy.DIRECT, "none", "TechCrunch"),
            "aljazeera.com" to DomainRule("aljazeera.com", Strategy.DIRECT, "none", "Al Jazeera"),
            "cbsnews.com" to DomainRule("cbsnews.com", Strategy.DIRECT, "none", "CBS News"),
            "nbcnews.com" to DomainRule("nbcnews.com", Strategy.DIRECT, "none", "NBC News"),
            "abcnews.go.com" to DomainRule("abcnews.go.com", Strategy.DIRECT, "none", "ABC News"),
            "usatoday.com" to DomainRule("usatoday.com", Strategy.DIRECT, "metered", "USA Today"),
            "politico.com" to DomainRule("politico.com", Strategy.DIRECT, "none", "Politico"),
            "globalnews.ca" to DomainRule("globalnews.ca", Strategy.DIRECT, "none", "Global News"),
            "cbc.ca" to DomainRule("cbc.ca", Strategy.DIRECT, "none", "CBC News"),
            "ctvnews.ca" to DomainRule("ctvnews.ca", Strategy.DIRECT, "none", "CTV News"),
            "citynews.ca" to DomainRule("citynews.ca", Strategy.DIRECT, "none", "CityNews"),
            "thestar.com" to DomainRule("thestar.com", Strategy.DIRECT, "none", "Toronto Star"),
            "cnbc.com" to DomainRule("cnbc.com", Strategy.DIRECT, "none", "CNBC"),
            "investing.com" to DomainRule("investing.com", Strategy.DIRECT, "none", "Investing.com"),
            "foxnews.com" to DomainRule("foxnews.com", Strategy.DIRECT, "none", "Fox News"),
            "slashdot.org" to DomainRule("slashdot.org", Strategy.DIRECT, "none", "Slashdot"),
            "ycombinator.com" to DomainRule("ycombinator.com", Strategy.DIRECT, "none", "Hacker News"),
            "youtube.com" to DomainRule("youtube.com", Strategy.DIRECT, "none", "YouTube"),
            "gizmodo.com" to DomainRule("gizmodo.com", Strategy.DIRECT, "none", "Gizmodo"),
            "japantimes.co.jp" to DomainRule("japantimes.co.jp", Strategy.DIRECT, "unknown", "The Japan Times"),
            "scmp.com" to DomainRule("scmp.com", Strategy.DIRECT, "unknown", "South China Morning Post"),
            "channelnewsasia.com" to DomainRule("channelnewsasia.com", Strategy.DIRECT, "unknown", "CNA"),
            "dw.com" to DomainRule("dw.com", Strategy.DIRECT, "unknown", "Deutsche Welle"),
            "france24.com" to DomainRule("france24.com", Strategy.DIRECT, "unknown", "France 24"),
            "lemonde.fr" to DomainRule("lemonde.fr", Strategy.DIRECT, "metered", "Le Monde"),

            // Metered / soft paywalls with standard bypass
            "vancouversun.com" to DomainRule("vancouversun.com", Strategy.SMRY, "metered", "Vancouver Sun"),
            "theprovince.com" to DomainRule("theprovince.com", Strategy.SMRY, "metered", "The Province"),
            "timescolonist.com" to DomainRule("timescolonist.com", Strategy.SMRY, "metered", "Times Colonist"),
            "nytimes.com" to DomainRule("nytimes.com", Strategy.SMRY, "metered", "New York Times"),
            "theatlantic.com" to DomainRule("theatlantic.com", Strategy.SMRY, "metered", "The Atlantic"),
            "washingtonpost.com" to DomainRule("washingtonpost.com", Strategy.SMRY, "metered", "Washington Post"),
            "medium.com" to DomainRule("medium.com", Strategy.FREEDIUM, "metered", "Medium"),
            "newyorker.com" to DomainRule("newyorker.com", Strategy.SMRY, "metered", "The New Yorker"),
            "businessinsider.com" to DomainRule("businessinsider.com", Strategy.REMOVEPAYWALL, "soft", "Business Insider"),
            "wired.com" to DomainRule("wired.com", Strategy.REMOVEPAYWALL, "metered", "Wired"),

            // Hard paywalls requiring archive verification
            "wsj.com" to DomainRule("wsj.com", Strategy.ARCHIVE_IS, "hard", "Wall Street Journal"),
            "bloomberg.com" to DomainRule("bloomberg.com", Strategy.ARCHIVE_IS, "hard", "Bloomberg"),
            "ft.com" to DomainRule("ft.com", Strategy.ARCHIVE_IS, "hard", "Financial Times"),
            "economist.com" to DomainRule("economist.com", Strategy.ARCHIVE_IS, "hard", "The Economist"),
            "barrons.com" to DomainRule("barrons.com", Strategy.ARCHIVE_IS, "hard", "Barron's"),
            "theinformation.com" to DomainRule("theinformation.com", Strategy.ARCHIVE_IS, "hard", "The Information"),
            "telegraph.co.uk" to DomainRule("telegraph.co.uk", Strategy.ARCHIVE_IS, "hard", "The Telegraph"),
        )

        /** Known publisher names mapped to their official domains. */
        val KNOWN_PUBLISHER_DOMAINS: Map<String, String> = mapOf(
            "the guardian" to "theguardian.com",
            "guardian" to "theguardian.com",
            "bbc" to "bbc.com",
            "bbc news" to "bbc.com",
            "the new york times" to "nytimes.com",
            "new york times" to "nytimes.com",
            "nytimes" to "nytimes.com",
            "the washington post" to "washingtonpost.com",
            "washington post" to "washingtonpost.com",
            "wapo" to "washingtonpost.com",
            "the wall street journal" to "wsj.com",
            "wall street journal" to "wsj.com",
            "wsj" to "wsj.com",
            "bloomberg" to "bloomberg.com",
            "bloomberg news" to "bloomberg.com",
            "financial times" to "ft.com",
            "ft" to "ft.com",
            "the economist" to "economist.com",
            "economist" to "economist.com",
            "reuters" to "reuters.com",
            "associated press" to "apnews.com",
            "ap news" to "apnews.com",
            "ap" to "apnews.com",
            "cnn" to "cnn.com",
            "arstechnica" to "arstechnica.com",
            "ars technica" to "arstechnica.com",
            "the verge" to "theverge.com",
            "techcrunch" to "techcrunch.com",
            "wired" to "wired.com",
            "business insider" to "businessinsider.com",
            "insider" to "businessinsider.com",
            "forbes" to "forbes.com",
            "fortune" to "fortune.com",
            "cnbc" to "cnbc.com",
            "cbs news" to "cbsnews.com",
            "cbs" to "cbsnews.com",
            "nbc news" to "nbcnews.com",
            "nbc" to "nbcnews.com",
            "abc news" to "abcnews.go.com",
            "fox news" to "foxnews.com",
            "fox" to "foxnews.com",
            "politico" to "politico.com",
            "al jazeera" to "aljazeera.com",
            "the japan times" to "japantimes.co.jp",
            "japan times" to "japantimes.co.jp",
            "south china morning post" to "scmp.com",
            "scmp" to "scmp.com",
            "cna" to "channelnewsasia.com",
            "channel news asia" to "channelnewsasia.com",
            "deutsche welle" to "dw.com",
            "dw" to "dw.com",
            "france 24" to "france24.com",
            "le monde" to "lemonde.fr",
            "usa today" to "usatoday.com",
            "los angeles times" to "latimes.com",
            "la times" to "latimes.com",
            "chicago tribune" to "chicagotribune.com",
            "the atlantic" to "theatlantic.com",
            "the new yorker" to "newyorker.com",
            "new yorker" to "newyorker.com",
            "time" to "time.com",
            "vox" to "vox.com",
            "slate" to "slate.com",
            "npr" to "npr.org",
            "cbc" to "cbc.ca",
            "cbc news" to "cbc.ca",
            "ctv news" to "ctvnews.ca",
            "ctv" to "ctvnews.ca",
            "global news" to "globalnews.ca",
            "the globe and mail" to "theglobeandmail.com",
            "globe and mail" to "theglobeandmail.com",
            "toronto star" to "thestar.com",
            "the star" to "thestar.com",
            "vancouver sun" to "vancouversun.com",
            "the province" to "theprovince.com",
            "times colonist" to "timescolonist.com",
            "gates notes" to "gatesnotes.com",
            "gatesnotes" to "gatesnotes.com",
            "mckinsey & company" to "mckinsey.com",
            "mckinsey" to "mckinsey.com",
            "ukauthority" to "ukauthority.com",
            "nature" to "nature.com",
            "science" to "science.org",
            "mit technology review" to "technologyreview.com",
            "zdnet" to "zdnet.com",
            "venturebeat" to "venturebeat.com",
            "engadget" to "engadget.com",
            "gizmodo" to "gizmodo.com",
            "slashdot" to "slashdot.org",
            "hacker news" to "news.ycombinator.com",
            "github" to "github.com",
            "medium" to "medium.com",
            "substack" to "substack.com",
            "the register" to "theregister.com",
            "bleepingcomputer" to "bleepingcomputer.com",
            "bleeping computer" to "bleepingcomputer.com",
            "tom's hardware" to "tomshardware.com",
            "pc gamer" to "pcgamer.com",
            "ign" to "ign.com",
            "eurogamer" to "eurogamer.net",
            "9to5mac" to "9to5mac.com",
            "9to5google" to "9to5google.com",
            "macrumors" to "macrumors.com",
            "android central" to "androidcentral.com",
            "android police" to "androidpolice.com",
            "techradar" to "techradar.com",
            "digital trends" to "digitaltrends.com",
            "quartz" to "qz.com",
            "barron's" to "barrons.com",
            "marketwatch" to "marketwatch.com",
            "the information" to "theinformation.com",
            "the telegraph" to "telegraph.co.uk",
            "daily mail" to "dailymail.co.uk",
            "the independent" to "independent.co.uk",
            "evening standard" to "standard.co.uk",
            "sky news" to "news.sky.com",
            "axios" to "axios.com",
            "semafor" to "semafor.com",
            "puck" to "puck.news",
            "the hill" to "thehill.com",
            "rolling stone" to "rollingstone.com",
            "billboard" to "billboard.com",
            "variety" to "variety.com",
            "deadline" to "deadline.com",
            "the hollywood reporter" to "hollywoodreporter.com",
        )
    }
}
