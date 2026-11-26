// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.alerts

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The reader's alert choices (Settings › Notifications). */
@Serializable
data class AlertPrefs(
    /** Topic alerts play Editrove's bell (off: they arrive silently). */
    val sound: Boolean = true,
    /** Breaking alerts play the breaking sting (off: silently). */
    val breakingSound: Boolean = true,
    /** A daily quiet time with no alerts at all. */
    val quietEnabled: Boolean = false,
    /** Minutes after midnight. Can wrap past midnight (22:00 → 07:00). */
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    /** No alerts until this moment (epoch ms; 0 = not paused). */
    val pausedUntil: Long = 0L,
    /** Alert channels that arrive without sound (Notification Hub): "topic:ID", "breaking", "realtime", "op:KEY". */
    val silent: Set<String> = emptySet(),
) {
    /** Whether an alert on [channel] (breaking or not) should make a sound. */
    fun audible(channel: String?, breaking: Boolean): Boolean =
        (if (breaking) breakingSound else sound) && (channel == null || channel !in silent)
}

/** Quiet time: the daily window and any pause. Nothing found in it is delivered later. */
object QuietTime {
    fun active(p: AlertPrefs, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        now < p.pausedUntil || (p.quietEnabled && inWindow(p.quietStart, p.quietEnd, minuteOfDay(now, zone)))

    fun inWindow(start: Int, end: Int, minute: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end // across midnight
    }

    /** When the current quiet time ends (epoch ms), or null when not quiet. */
    fun endsAt(p: AlertPrefs, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val windowEnd = if (p.quietEnabled && inWindow(p.quietStart, p.quietEnd, minuteOfDay(now, zone))) {
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            var end = t.toLocalDate().atStartOfDay().plusMinutes(p.quietEnd.toLong())
            if (!end.isAfter(t)) end = end.plusDays(1)
            end.atZone(zone).toInstant().toEpochMilli()
        } else null
        val pause = p.pausedUntil.takeIf { it > now }
        return listOfNotNull(windowEnd, pause).maxOrNull()
    }

    /** "Until tomorrow morning": the next 07:00. */
    fun nextMorning(now: Long, zone: ZoneId = ZoneId.systemDefault(), hour: Int = 7): Long {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var m = t.toLocalDate().atTime(hour, 0)
        if (!m.isAfter(t)) m = m.plusDays(1)
        return m.atZone(zone).toInstant().toEpochMilli()
    }

    private fun minuteOfDay(now: Long, zone: ZoneId): Int =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).let { it.hour * 60 + it.minute }
}

/**
 * Whether two headlines are the same story. Outlets word a story differently ("UK net migration
 * at lowest level since 1997" / "Net migration to U.K. falls to lowest level since 1997"), so
 * this compares their distinctive words, not the text.
 */
object HeadlineMatch {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val PREFIX = Regex("^\\s*(?:breaking(?: news)?|live(?: updates)?|watch|video|just in|update[d]?|exclusive|analysis|opinion)\\s*[:|\\-–—]\\s*", RegexOption.IGNORE_CASE)
    private val SUFFIX = Regex("\\s+[-–—|]\\s+[^-–—|]{2,40}$") // " - BBC News"
    private val STOP = setOf(
        "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "is", "are", "was", "were", "be", "been", "with", "at", "by",
        "from", "as", "after", "over", "into", "its", "it", "has", "have", "had", "will", "would", "new", "says", "say", "said",
        "that", "this", "these", "those", "his", "her", "their", "they", "he", "she", "we", "you", "not", "no", "but", "than",
        "more", "most", "who", "what", "why", "how", "when", "where", "about", "up", "out", "off", "amid", "live", "updates",
        "update", "news", "latest", "report", "reports", "per", "via", "least", "also", "just",
        // Arabic / French / Spanish / German function words
        "في", "من", "على", "إلى", "عن", "مع", "le", "la", "les", "des", "du", "de", "et", "un", "une", "el", "los", "las", "del",
        "y", "en", "der", "die", "das", "und", "ein", "eine", "im", "zu", "mit",
    )

    /** The headline's distinctive words (lower-case, roughly singular). */
    fun tokens(title: String): Set<String> {
        val t = title.replace(PREFIX, "").replace(SUFFIX, "").lowercase()
            .replace("u.k.", "uk").replace("u.s.", "us").replace("u.n.", "un")
        return t.split(NON_WORD).asSequence()
            .filter { it.length > 1 && it !in STOP }
            .map { w -> if (w.length > 4 && w.endsWith("s") && !w.endsWith("ss")) w.dropLast(1) else w }
            .toSet()
    }

    fun same(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val shared = (a intersect b).size
        val jaccard = shared.toDouble() / (a union b).size
        val overlap = shared.toDouble() / minOf(a.size, b.size)
        return (shared >= 3 && jaccard >= 0.4) || (shared >= 4 && overlap >= 0.6)
    }
}

/**
 * Every alert Editrove has shown in the last 48 hours, whatever kind (topic, breaking,
 * real-time, opinion). A story already alerted (same link, or a similar headline from any
 * outlet) is never alerted again inside the window.
 */
@Serializable
data class AlertLedger(val entries: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(val key: String, val tokens: List<String>, val at: Long)

    fun alreadyAlerted(key: String?, title: String, now: Long): Boolean {
        val t = HeadlineMatch.tokens(title)
        return entries.any { e ->
            now - e.at < WINDOW_MS && ((key != null && key.isNotEmpty() && e.key == key) || HeadlineMatch.same(t, e.tokens.toSet()))
        }
    }

    fun record(key: String?, title: String, now: Long): AlertLedger =
        AlertLedger((listOf(Entry(key.orEmpty(), HeadlineMatch.tokens(title).toList(), now)) + entries).filter { now - it.at < WINDOW_MS }.take(MAX))

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val WINDOW_MS = 48 * 3_600_000L
        const val MAX = 800
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(s: String?): AlertLedger = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: AlertLedger()
    }
}

/** An alert waiting its turn. */
@Serializable
data class PendingAlert(
    val key: String,
    val title: String,
    val source: String? = null,
    val url: String? = null,
    val topicId: String? = null,
    val topicLabel: String? = null,
    val breaking: Boolean = false,
    /** When the story was found (epoch ms). */
    val foundAt: Long,
    /** What it's an alert for (Notification Hub key): "topic:ID", "breaking", "realtime", "op:KEY". */
    val channel: String? = null,
)

/**
 * Alerts arrive one at a time, a few minutes apart, newest-first within each kind (breaking
 * before topics), instead of a burst when a check finds several. Stale ones are dropped.
 */
@Serializable
data class AlertQueue(val items: List<PendingAlert> = emptyList(), val lastPostedAt: Long = 0L) {
    fun add(new: List<PendingAlert>): AlertQueue =
        copy(items = (items + new).distinctBy { it.key }.sortedWith(compareByDescending<PendingAlert> { it.breaking }.thenBy { it.foundAt }))

    /** What to post now (if anything), the queue after it, and when to look again (ms from now, null = nothing left). */
    fun next(now: Long, spacingMs: Long = SPACING_MS): Triple<PendingAlert?, AlertQueue, Long?> {
        val live = items.filter { now - it.foundAt < MAX_AGE_MS }
        if (live.isEmpty()) return Triple(null, copy(items = emptyList()), null)
        val wait = lastPostedAt + spacingMs - now
        if (wait > 0) return Triple(null, copy(items = live), wait)
        val head = live.first()
        val rest = live.drop(1)
        return Triple(head, AlertQueue(rest, now), if (rest.isEmpty()) null else spacingMs)
    }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val SPACING_MS = 3 * 60_000L
        const val MAX_AGE_MS = 3 * 3_600_000L
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(s: String?): AlertQueue = s?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() } ?: AlertQueue()
    }
}
