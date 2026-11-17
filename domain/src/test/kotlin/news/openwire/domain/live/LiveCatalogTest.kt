// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCatalogTest {
    @Test
    fun everyChannelEitherStreamsOrLinksOut() {
        LiveCatalog.CHANNELS.forEach { c ->
            assertTrue(c.id, (c.stream?.startsWith("https://") == true) xor (c.webUrl?.startsWith("https://") == true))
        }
        assertEquals(LiveCatalog.CHANNELS.size, LiveCatalog.CHANNELS.map { it.id }.toSet().size)
    }

    @Test
    fun appLanguageComesFirst() {
        val ordered = LiveCatalog.ordered(LiveCatalog.CHANNELS, "ar") { it.language }
        assertEquals("ar", ordered.first().language)
        assertTrue(ordered.takeWhile { it.language == "ar" }.size >= 6)
    }

    @Test
    fun radioBrowserResultsAreCleaned() {
        val body = """[
          {"stationuuid":"a","name":"Radio  9090","url":"http://x/9090","url_resolved":"http://x/9090.mp3","homepage":"https://www.9090fm.com/","favicon":"https://9090fm.com/i.png","countrycode":"EG","language":"arabic","tags":"news,talk","lastcheckok":1},
          {"stationuuid":"b","name":"Broken","url_resolved":"http://y","lastcheckok":0},
          {"stationuuid":"c","name":"radio 9090","url_resolved":"http://dup","lastcheckok":1}
        ]"""
        val s = RadioBrowser.parse(body)
        assertEquals(1, s.size)
        assertEquals("Radio 9090", s[0].name)
        assertEquals("ar", s[0].language)
        assertEquals("9090fm.com", s[0].domain)
        assertEquals(listOf("news", "talk"), s[0].tags)
    }
}
