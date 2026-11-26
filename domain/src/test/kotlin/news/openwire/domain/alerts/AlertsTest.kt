// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.alerts

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases taken from real duplicate alerts (a reader's notification history, Nov 2026). */
class AlertsTest {
    private fun same(a: String, b: String) = HeadlineMatch.same(HeadlineMatch.tokens(a), HeadlineMatch.tokens(b))

    @Test fun sameStoryFromDifferentOutletsMatches() {
        assertTrue(same(
            "North Korea's Kim vows to build more nuclear weapons after ignoring Trump",
            "North Korea’s Kim vows to build more nuclear weapons after ignoring Trump overtures",
        ))
        assertTrue(same("UK net migration at lowest level since 1997 as asylum claims also fall", "Net migration to UK falls to lowest level since 1997 excluding pandemic"))
        assertTrue(same("Net Migration to U.K. Falls to Lowest Level Since 1997, Excluding Pandemic", "Net migration to UK falls to lowest level since 1997 excluding pandemic"))
        assertTrue(same("FedEx cargo plane runs off Miami International Airport runway, causing ground stop", "FedEx cargo plane runs off MIA runway, causing brief ground stop"))
        assertTrue(same("Andy Taylor, guitarist of new wave icons Duran Duran, dies at 65", "BREAKING: Andy Taylor, guitarist of new wave icons Duran Duran, dies at 65 - WTOP News"))
    }

    @Test fun differentStoriesDoNotMatch() {
        assertFalse(same("Trump says tariffs on Canada will rise next week", "Trump says he will meet Putin in Alaska"))
        assertFalse(same("Steelers officially release Aaron Rodgers", "Depleted PSG attack shows vulnerability in 2-2 Champions League draw"))
        assertFalse(same("Stocks rise as AI trade rebounds", "Stocks fall as oil prices jump"))
    }

    @Test fun ledgerBlocksRepeatsForTwoDaysAcrossKindsAndOutlets() {
        val t0 = 1_000_000_000L
        var l = AlertLedger().record("nytimes.com/storm", "Thanksgiving Storm Threatens the East Coast: Live Weather Updates", t0)
        // Same link (topic then breaking).
        assertTrue(l.alreadyAlerted("nytimes.com/storm", "anything", t0 + 1))
        // Another outlet, hours later.
        assertTrue(l.alreadyAlerted("cbs.com/x", "Thanksgiving storm threatens East Coast travel", t0 + 6 * 3_600_000L))
        // After 48 h it may come back.
        assertFalse(l.alreadyAlerted("nytimes.com/storm", "Thanksgiving Storm Threatens the East Coast", t0 + 49 * 3_600_000L))
        l = l.record("a", "Something else entirely happened today in Paris", t0)
        assertEquals(l, AlertLedger.decode(l.encode()))
    }

    @Test fun quietTimeWrapsMidnightAndPauses() {
        val zone = ZoneOffset.UTC
        fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 26, h, m).toInstant(zone).toEpochMilli()
        val p = AlertPrefs(quietEnabled = true, quietStart = 22 * 60, quietEnd = 7 * 60)
        assertTrue(QuietTime.active(p, at(23), zone))
        assertTrue(QuietTime.active(p, at(6, 59), zone))
        assertFalse(QuietTime.active(p, at(7), zone))
        assertFalse(QuietTime.active(p, at(12), zone))
        assertEquals(LocalDateTime.of(2026, 11, 27, 7, 0).toInstant(zone).toEpochMilli(), QuietTime.endsAt(p, at(23), zone))
        val paused = AlertPrefs(pausedUntil = at(15))
        assertTrue(QuietTime.active(paused, at(12), zone))
        assertFalse(QuietTime.active(paused, at(15), zone))
        assertNull(QuietTime.endsAt(AlertPrefs(), at(12), zone))
    }

    @Test fun queueSpacesAlertsBreakingFirstAndDropsStale() {
        val now = 10_000_000L
        fun a(k: String, br: Boolean = false, found: Long = now) = PendingAlert(k, k, foundAt = found, breaking = br)
        var q = AlertQueue().add(listOf(a("t1"), a("t2"), a("b1", br = true), a("old", found = now - 4 * 3_600_000L)))
        val (first, q1, again) = q.next(now)
        assertEquals("b1", first?.key)
        assertEquals(AlertQueue.SPACING_MS, again)
        // Too soon for the next one.
        val (none, q2, wait) = q1.next(now + 60_000)
        assertNull(none); assertNotNull(wait)
        val (second, _, _) = q2.next(now + AlertQueue.SPACING_MS)
        assertEquals("t1", second?.key)
        q = AlertQueue().add(listOf(a("old", found = now - 4 * 3_600_000L)))
        assertNull(q.next(now).first)
    }
}
