// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.repository

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One source a refresh asks: a publisher feed, a news search, or the published edition. */
data class SourceRef(val key: String, val name: String, val kind: Kind) {
    enum class Kind { FEED, SEARCH, EDITION }
}

/** What a refresh is doing, source by source (the pull-to-refresh "wire"). */
sealed interface RefreshEvent {
    val refreshId: Long
    data class Started(override val refreshId: Long, val topicId: String, val sources: List<SourceRef>) : RefreshEvent
    data class Returned(override val refreshId: Long, val source: SourceRef, val elapsedMs: Long) : RefreshEvent
    data class Failed(override val refreshId: Long, val source: SourceRef, val elapsedMs: Long, val timedOut: Boolean) : RefreshEvent
    data class Finished(override val refreshId: Long, val topicId: String) : RefreshEvent
}

/**
 * Reports a refresh's sources as they return. Never waits on its listeners: events go into a
 * small buffer (oldest dropped), so the fetch runs exactly as fast and fetches exactly what it did.
 */
class RefreshReporter(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val ids = AtomicLong(0)
    private val _events = MutableSharedFlow<RefreshEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<RefreshEvent> = _events.asSharedFlow()

    fun start(topicId: String, sources: List<SourceRef>): Long =
        ids.incrementAndGet().also { _events.tryEmit(RefreshEvent.Started(it, topicId, sources)) }

    fun finish(refreshId: Long, topicId: String) { _events.tryEmit(RefreshEvent.Finished(refreshId, topicId)) }

    /** Runs one source's fetch, timing it from the request to its answer. Exceptions pass through unchanged. */
    suspend fun <T> time(refreshId: Long, source: SourceRef, block: suspend () -> T): T {
        val t0 = clock()
        return try {
            block().also { _events.tryEmit(RefreshEvent.Returned(refreshId, source, clock() - t0)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(RefreshEvent.Failed(refreshId, source, clock() - t0, timedOut = e.isTimeout()))
            throw e
        }
    }

    private fun Throwable.isTimeout(): Boolean =
        this is SocketTimeoutException || this is java.io.InterruptedIOException && message?.contains("timeout", true) == true ||
            cause?.isTimeout() == true
}

/** The wire's state for one refresh: lines in arrival order and the live count. */
data class RefreshTicker(
    val refreshId: Long = 0,
    val topicId: String = "",
    val total: Int = 0,
    val lines: List<Line> = emptyList(),
    val finished: Boolean = false,
) {
    data class Line(val name: String, val elapsedMs: Long, val ok: Boolean, val timedOut: Boolean = false, val kind: SourceRef.Kind = SourceRef.Kind.FEED)
    val returned: Int get() = lines.size
    val failed: List<String> get() = lines.filter { !it.ok }.map { it.name }

    /** Folds one event in; events of another refresh are ignored once this one has started. */
    fun reduce(e: RefreshEvent): RefreshTicker = when (e) {
        is RefreshEvent.Started -> RefreshTicker(e.refreshId, e.topicId, e.sources.size)
        is RefreshEvent.Returned -> if (e.refreshId != refreshId) this else copy(lines = lines + Line(e.source.name, e.elapsedMs, true, kind = e.source.kind))
        is RefreshEvent.Failed -> if (e.refreshId != refreshId) this else copy(lines = lines + Line(e.source.name, e.elapsedMs, false, e.timedOut, e.source.kind))
        is RefreshEvent.Finished -> if (e.refreshId != refreshId) this else copy(finished = true)
    }
}
