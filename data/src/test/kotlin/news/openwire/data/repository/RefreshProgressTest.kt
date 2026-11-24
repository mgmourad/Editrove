// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshProgressTest {
    private var now = 0L
    private val reporter = RefreshReporter { now }
    private val a = SourceRef("a", "Alpha", SourceRef.Kind.FEED)
    private val b = SourceRef("b", "Beta", SourceRef.Kind.FEED)
    private val c = SourceRef("c", "News search", SourceRef.Kind.SEARCH)

    private suspend fun answer(rid: Long, s: SourceRef, ms: Long, fail: Exception? = null) = runCatching {
        reporter.time(rid, s) { now += ms; fail?.let { throw it }; "ok" }
    }

    @Test fun successIsTimedFromRequestToAnswer() = runTest(UnconfinedTestDispatcher()) {
        val got = mutableListOf<RefreshEvent>()
        val job = launch { reporter.events.toList(got) }
        val rid = reporter.start("TOP", listOf(a))
        assertEquals("ok", answer(rid, a, 293).getOrNull())
        reporter.finish(rid, "TOP")
        job.cancel()
        assertEquals(RefreshEvent.Returned(rid, a, 293), got[1])
        assertTrue(got.last() is RefreshEvent.Finished)
    }

    @Test fun failureAndTimeoutAreReportedAndStillThrown() = runTest(UnconfinedTestDispatcher()) {
        val got = mutableListOf<RefreshEvent>()
        val job = launch { reporter.events.toList(got) }
        val rid = reporter.start("TOP", listOf(a, b))
        assertTrue(answer(rid, a, 40, IOException("HTTP 503")).isFailure)
        assertTrue(answer(rid, b, 8000, SocketTimeoutException("timeout")).isFailure)
        job.cancel()
        val failed = got.filterIsInstance<RefreshEvent.Failed>()
        assertEquals(listOf(false, true), failed.map { it.timedOut })
        assertEquals(listOf(40L, 8000L), failed.map { it.elapsedMs })
    }

    @Test fun aMixFoldsIntoTheTickerInArrivalOrder() = runTest(UnconfinedTestDispatcher()) {
        var ticker = RefreshTicker()
        val job = launch { reporter.events.collect { ticker = ticker.reduce(it) } }
        val rid = reporter.start("TOP", listOf(a, b, c))
        listOf(async { answer(rid, c, 120) }, async { answer(rid, a, 300, IOException()) }, async { answer(rid, b, 90) }).awaitAll()
        assertEquals(3, ticker.total)
        assertEquals(3, ticker.returned)
        assertEquals(listOf("News search", "Alpha", "Beta"), ticker.lines.map { it.name })
        assertEquals(listOf("Alpha"), ticker.failed)
        assertFalse(ticker.finished)
        reporter.finish(rid, "TOP")
        assertTrue(ticker.finished)
        job.cancel()
    }

    @Test fun anOlderRefreshCannotWriteIntoANewerOne() {
        val t = RefreshTicker().reduce(RefreshEvent.Started(2, "TOP", listOf(a)))
            .reduce(RefreshEvent.Returned(1, b, 10))
            .reduce(RefreshEvent.Finished(1, "TOP"))
        assertEquals(0, t.returned)
        assertFalse(t.finished)
    }
}
