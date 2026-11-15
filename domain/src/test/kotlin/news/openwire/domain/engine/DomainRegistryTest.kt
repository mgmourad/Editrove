package news.openwire.domain.engine

import news.openwire.domain.model.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRegistryTest {

    private val registry = DomainRegistry()

    @Test
    fun normalizeDomainExtractsRootCleanDomains() {
        assertEquals("wsj.com", registry.normalizeDomain("https://www.wsj.com/articles/xyz"))
        assertEquals("edition.cnn.com", registry.normalizeDomain("http://edition.cnn.com"))
        assertEquals("bbc.co.uk", registry.normalizeDomain("www.bbc.co.uk"))
        assertEquals("theguardian.com", registry.normalizeDomain("theguardian.com:8080"))
        assertEquals("", registry.normalizeDomain(""))
    }

    @Test
    fun resolvePublisherDomainAndNormalizeDomainResolveKnownNames() {
        assertEquals("theguardian.com", registry.resolvePublisherDomain("The Guardian"))
        assertEquals("gatesnotes.com", registry.resolvePublisherDomain("Gates Notes"))
        assertEquals("nytimes.com", registry.resolvePublisherDomain("The New York Times"))
        assertEquals("ft.com", registry.resolvePublisherDomain("Financial Times"))
        assertEquals("bbc.com", registry.resolvePublisherDomain("BBC"))
        assertEquals("", registry.resolvePublisherDomain("non existent publisher"))

        assertEquals("theguardian.com", registry.normalizeDomain("The Guardian"))
        assertEquals("gatesnotes.com", registry.normalizeDomain("Gates Notes"))
        assertEquals("bbc.com", registry.normalizeDomain("BBC"))
        assertEquals("nytimes.com", registry.normalizeDomain("The New York Times"))
        assertEquals("", registry.normalizeDomain("Unknown Non Domain Phrase"))

        val nytRule = registry.getRule("The New York Times")
        assertEquals(Strategy.SMRY, nytRule.strategy)
        assertEquals("nytimes.com", nytRule.domain)
    }

    @Test
    fun getRuleMatchesFreeOpenAccessDomains() {
        assertEquals(Strategy.DIRECT, registry.getRule("bbc.com").strategy)
        assertEquals(Strategy.DIRECT, registry.getRule("npr.org").strategy)
        assertEquals(Strategy.DIRECT, registry.getRule("theguardian.com").strategy)
    }

    @Test
    fun getRuleMatchesStandardBypassDomains() {
        assertEquals(Strategy.SMRY, registry.getRule("nytimes.com").strategy)
        assertEquals(Strategy.SMRY, registry.getRule("theatlantic.com").strategy)
        assertEquals(Strategy.REMOVEPAYWALL, registry.getRule("businessinsider.com").strategy)
    }

    @Test
    fun getRuleMatchesHardPaywallsRequiringArchive() {
        assertEquals(Strategy.ARCHIVE_IS, registry.getRule("wsj.com").strategy)
        assertEquals(Strategy.ARCHIVE_IS, registry.getRule("bloomberg.com").strategy)
        assertEquals(Strategy.ARCHIVE_IS, registry.getRule("markets.ft.com").strategy)
    }

    @Test
    fun getRuleFallsBackToDirectForUnknownDomains() {
        val unknown = registry.getRule("somerandomblog.io")
        assertEquals(Strategy.DIRECT, unknown.strategy)
        assertTrue(unknown.isDefault)
    }

    @Test
    fun strategyHasNoTwelveFtEntry() {
        assertTrue(Strategy.entries.none { it.wireValue == "12ft" })
    }

    @Test
    fun legacyTwelveFtWireValueMigratesToReader() {
        assertEquals(Strategy.READER, Strategy.fromWire("12ft"))
    }

    @Test
    fun mediumComDefaultsToFreedium() {
        assertEquals(Strategy.FREEDIUM, registry.getRule("medium.com").strategy)
    }

    @Test
    fun isFreediumSupportedValidatesStrictList() {
        assertTrue(registry.isFreediumSupported("medium.com"))
        assertTrue(registry.isFreediumSupported("nytimes.com"))
        assertTrue(registry.isFreediumSupported("washingtonpost.com"))
        assertTrue(registry.isFreediumSupported("bloomberg.com"))
        assertTrue(registry.isFreediumSupported("reuters.com"))
        assertTrue(registry.isFreediumSupported("economist.com"))
        assertTrue(registry.isFreediumSupported("ft.com"))
        assertTrue(registry.isFreediumSupported("markets.ft.com"))
        assertFalse(registry.isFreediumSupported("cnn.com"))
        assertFalse(registry.isFreediumSupported("wsj.com"))
        assertFalse(registry.isFreediumSupported("bbc.com"))
    }
}
