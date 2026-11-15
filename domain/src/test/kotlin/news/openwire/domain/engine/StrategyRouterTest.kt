package news.openwire.domain.engine

import news.openwire.domain.model.Article
import news.openwire.domain.model.DomainRule
import news.openwire.domain.model.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyRouterTest {

    private val registry = DomainRegistry()

    private fun article(url: String, domain: String = "washingtonpost.com") =
        Article(id = "1", title = "T", domain = domain, originalUrl = url)

    @Test
    fun buildStrategyUrlMapsEveryStrategy() {
        val url = "https://www.wsj.com/articles/sample-article"
        assertEquals(url, StrategyRouter.buildStrategyUrl(Strategy.DIRECT, url))
        assertEquals(url, StrategyRouter.buildStrategyUrl(Strategy.READER, url))
        assertEquals("https://smry.ai/$url", StrategyRouter.buildStrategyUrl(Strategy.SMRY, url))
        assertEquals("https://www.removepaywall.com/search?url=${java.net.URLEncoder.encode(url, "UTF-8")}", StrategyRouter.buildStrategyUrl(Strategy.REMOVEPAYWALL, url))
        assertEquals("https://unwall.app/$url", StrategyRouter.buildStrategyUrl(Strategy.UNWALL, url))
        assertEquals("https://freedium-mirror.cfd/$url", StrategyRouter.buildStrategyUrl(Strategy.FREEDIUM, url))
        assertEquals("https://archive.is/newest/$url", StrategyRouter.buildStrategyUrl(Strategy.ARCHIVE_IS, url))
        assertEquals("https://web.archive.org/web/*/$url", StrategyRouter.buildStrategyUrl(Strategy.WAYBACK, url))
        assertEquals("", StrategyRouter.buildStrategyUrl(null, ""))
    }

    @Test
    fun applyStrategySetsStatusAndBadgePerStrategy() {
        val base = article("https://www.washingtonpost.com/politics/2026/09/13/test")

        val smry = StrategyRouter.applyStrategy(registry, base)
        assertEquals("bypassed", smry.status)
        assertEquals("SMRY", smry.badge)
        assertTrue(smry.targetUrl!!.startsWith("https://smry.ai/"))

        val rp = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.REMOVEPAYWALL))
        assertEquals("bypassed", rp.status)
        assertEquals("RemovePaywall", rp.badge)
        assertTrue(rp.targetUrl!!.contains("removepaywall.com/search?url="))

        val archive = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.ARCHIVE_IS))
        assertEquals("bypassed", archive.status)
        assertEquals("Archive.is", archive.badge)
        assertEquals("https://archive.is/newest/${base.originalUrl}", archive.targetUrl)
        assertTrue(archive.archiveUrl!!.contains("archive.is/newest/"))

        val unwall = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.UNWALL))
        assertEquals("UnWall", unwall.badge)
        assertEquals("https://unwall.app/${base.originalUrl}", unwall.targetUrl)

        val freedium = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.FREEDIUM))
        assertEquals("Freedium", freedium.badge)
        assertEquals("https://freedium-mirror.cfd/${base.originalUrl}", freedium.targetUrl)

        val reader = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.READER))
        assertEquals("Reader", reader.badge)
        assertEquals(base.originalUrl, reader.targetUrl)

        val direct = StrategyRouter.applyStrategy(registry, base, DomainRule("washingtonpost.com", Strategy.DIRECT))
        assertEquals("free", direct.status)
        assertEquals("Free", direct.badge)
        assertEquals(base.originalUrl, direct.targetUrl)
    }

    @Test
    fun unresolvedGoogleWrapperIsNeverWrapped() {
        val url = "https://news.google.com/rss/articles/opaque-unresolved?hl=en-CA"
        assertEquals(url, StrategyRouter.buildStrategyUrl(Strategy.ARCHIVE_IS, url))
        val routed = StrategyRouter.applyStrategy(registry, article(url, domain = "wsj.com"))
        assertEquals(url, routed.targetUrl)
        assertEquals("Open source", routed.badge)
        assertEquals("", routed.archiveUrl)
        assertEquals("free", routed.status)
    }

    @Test
    fun googleNewsUrlDetectionAndId() {
        assertTrue(GoogleNewsUrls.isGoogleNewsUrl("https://news.google.com/rss/articles/CBMiABC"))
        assertFalse(GoogleNewsUrls.isGoogleNewsUrl("https://example.com/story"))
        assertEquals("CBMiABC", GoogleNewsUrls.googleNewsArticleId("https://news.google.com/rss/articles/CBMiABC"))
        assertEquals("", GoogleNewsUrls.decodeEmbeddedGoogleUrl("https://news.google.com/rss/articles/CBMiABC"))
        assertEquals("", GoogleNewsUrls.decodeEmbeddedGoogleUrl(null))
    }
}
