// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiscoveryTest {
    @Test
    fun websitesAndNamesAreToldApart() {
        assertTrue(FeedDiscovery.looksLikeSite("youm7.com"))
        assertTrue(FeedDiscovery.looksLikeSite("https://www.ahram.org.eg/News/1"))
        assertFalse(FeedDiscovery.looksLikeSite("Al Ahram"))
        assertFalse(FeedDiscovery.looksLikeSite("اليوم السابع"))
        assertEquals("ahram.org.eg", FeedDiscovery.domainOf("https://www.ahram.org.eg/News/1"))
    }

    @Test
    fun languageOfHeadlines() {
        assertEquals("ar", FeedDiscovery.detectLanguage(listOf("الرئيس يشهد توقيع اتفاق", "ارتفاع أسعار الذهب")))
        assertEquals("fr", FeedDiscovery.detectLanguage(listOf("Le gouvernement annonce des mesures pour les retraites", "La France et l’Europe")))
        assertEquals("en", FeedDiscovery.detectLanguage(listOf("The Fed holds rates as inflation cools", "What to know about the vote in the House")))
    }

    @Test
    fun foundSourcesJoinTheRightTabs() {
        // Arabic paper, Arabic app, Egyptian edition: Top Stories and the Egypt tab.
        assertEquals(listOf("TOP", TopicRegistry.NATIONAL_ID), FeedDiscovery.topicsFor("ar", "ar", "EG"))
        // Arabic paper, English app, Egyptian edition: the Egypt tab only.
        assertEquals(listOf(TopicRegistry.NATIONAL_ID), FeedDiscovery.topicsFor("ar", "en", "EG"))
        // English paper, English app: Top Stories (and the US tab in a US edition).
        assertEquals(listOf("TOP"), FeedDiscovery.topicsFor("en", "en", "EG"))
        assertEquals(listOf("TOP", TopicRegistry.NATIONAL_ID), FeedDiscovery.topicsFor("en", "en", "US"))
    }

    @Test
    fun namesAreTidied() {
        assertEquals("اليوم السابع · أخبار عاجلة", FeedDiscovery.cleanName("rss-أخبار عاجلة", "اليوم السابع"))
        assertEquals("The Verge", FeedDiscovery.cleanName("The Verge", "The Verge"))
    }

    @Test
    fun matchingNamesRankFirst() {
        val good = FeedDiscovery.score("masrawy", "Masrawy · اقتصاد", "masrawy.com", 500, 2, false)
        val other = FeedDiscovery.score("masrawy", "You Must Remember This", "feedburner.com", 409, 2, false)
        assertTrue(good > other)
        assertEquals(FeedDiscovery.normalize("الأهرام"), FeedDiscovery.normalize("الاهرام"))
    }

    @Test
    fun feedlyResultsAreRead() {
        val body = """{"results":[{"feedId":"feed/http://www.masrawy.com/News/rss/RSS_Economy.aspx","title":"Masrawy-اقتصاد","website":"http://www.masrawy.com","subscribers":503},
            {"feedId":"feed/http://gdata.youtube.com/feeds/base/users/x/uploads","title":"YT","subscribers":3}]}"""
        val r = FeedDiscovery.parseFeedly(body)
        assertEquals(1, r.size)
        assertEquals("http://www.masrawy.com/News/rss/RSS_Economy.aspx", r[0].url)
    }
}
