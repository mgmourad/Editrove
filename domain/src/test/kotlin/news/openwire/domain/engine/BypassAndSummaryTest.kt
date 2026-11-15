// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Strategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BypassServicesTest {
    @Test fun smryFallsBackThroughOtherReadersThenArchives() {
        assertEquals(
            listOf(Strategy.SMRY, Strategy.REMOVEPAYWALL, Strategy.UNWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK),
            BypassServices.plan(Strategy.SMRY, emptySet()),
        )
    }

    @Test fun disabledServicesAreSkippedIncludingThePrimary() {
        assertEquals(
            listOf(Strategy.UNWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK),
            BypassServices.plan(Strategy.SMRY, setOf(Strategy.SMRY, Strategy.REMOVEPAYWALL)),
        )
    }

    @Test fun directNeedsNoFallback() {
        assertEquals(listOf(Strategy.DIRECT), BypassServices.plan(Strategy.DIRECT, emptySet()))
    }
}

class SummarizerTest {
    private val text = """
        The city council approved a new transit plan on Tuesday that will add three light rail lines by 2030.
        The transit plan is expected to cost four billion dollars and will be funded by a regional sales tax.
        Council members debated the light rail routes for more than six hours before the final vote.
        Some residents worried that construction of the rail lines would disrupt small businesses downtown.
        The mayor said the transit plan would cut commute times across the city for hundreds of thousands of riders.
        Weather on Tuesday was mild, with temperatures reaching the low sixties by the afternoon.
    """.trimIndent()

    @Test fun picksRepresentativeSentencesInReadingOrder() {
        val points = Summarizer.keyPoints(text, 3)
        assertEquals(3, points.size)
        assertTrue(points.first().startsWith("The city council approved"))
        assertTrue(points.none { it.startsWith("Weather") })
        val order = points.map { text.indexOf(it) }
        assertEquals(order.sorted(), order)
    }

    @Test fun shortTextsAreNotSummarized() {
        assertTrue(Summarizer.keyPoints("One sentence that is long enough to count as a sentence here.", 3).isEmpty())
    }
}
