// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.market.Markets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketsTest {
    private val body = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"^GSPC","shortName":"S&P 500","regularMarketPrice":7416.59,
        "chartPreviousClose":7561.67,"regularMarketDayHigh":7515.6,"regularMarketDayLow":7401.2},
        "indicators":{"quote":[{"close":[7500.1,null,7450.0,7416.59]}]}}],"error":null}}"""

    @Test fun parsesAQuote() {
        val q = Markets.parse(body, Markets.WATCH.first())!!
        assertEquals(7416.59, q.price, 0.001)
        assertTrue(!q.up)
        assertEquals("−1.92%", Markets.formatChange(q))
        assertEquals("7,416.59", Markets.formatPrice(q))
        assertEquals(3, q.series.size)
    }

    @Test fun badBodiesGiveNull() {
        assertNull(Markets.parse("""{"chart":{"result":null,"error":{"code":"Not Found"}}}""", Markets.WATCH.first()))
        assertNull(Markets.parse("<html>", Markets.WATCH.first()))
    }
}
