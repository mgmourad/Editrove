// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import news.openwire.domain.video.ShortVideo
import news.openwire.domain.video.VideoCatalog
import news.openwire.domain.video.VideoChannel
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * News Shorts from the outlets' official YouTube channels, read from YouTube's public Atom feeds
 * (no account, no API key, nothing about the reader sent). Each channel is cached for 15 minutes.
 */
class ShortsSource(private val client: OkHttpClient = OkHttpClient()) {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<ShortVideo>>>()

    suspend fun forTopic(topicId: String, switchedOff: (String) -> Boolean = { false }, now: Long = System.currentTimeMillis()): List<ShortVideo> = coroutineScope {
        val gate = Semaphore(6)
        val all = VideoCatalog.channelsFor(topicId, switchedOff).map { ch -> async { gate.withPermit { channel(ch, now) } } }.awaitAll().flatten()
        VideoCatalog.mix(topicId, all, now)
    }

    private suspend fun channel(ch: VideoChannel, now: Long): List<ShortVideo> {
        cache[ch.id]?.let { (at, list) -> if (now - at < TTL_MS) return list }
        val list = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(ch.feedUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) emptyList() else parse(r.body?.string().orEmpty(), ch)
                }
            }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) cache[ch.id] = now to list
        return list.ifEmpty { cache[ch.id]?.second.orEmpty() }
    }

    internal fun parse(xml: String, ch: VideoChannel): List<ShortVideo> =
        ENTRY.findAll(xml).mapNotNull { m ->
            val e = m.groupValues[1]
            val id = VID.find(e)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = TITLE.find(e)?.groupValues?.get(1)?.let(::unescape) ?: return@mapNotNull null
            val at = PUB.find(e)?.groupValues?.get(1)?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: return@mapNotNull null
            ShortVideo(id, title, ch.name, ch.avatar, at, ch.kind, ch.topics)
        }.toList()

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private companion object {
        const val TTL_MS = 15 * 60_000L
        val ENTRY = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val VID = Regex("<yt:videoId>([^<]+)</yt:videoId>")
        val TITLE = Regex("<title>([^<]+)</title>")
        val PUB = Regex("<published>([^<]+)</published>")
    }
}
