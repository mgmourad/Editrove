// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.domain.opinion.Bluesky
import news.openwire.domain.opinion.Mastodon
import news.openwire.domain.opinion.OpinionCatalog
import news.openwire.domain.opinion.OpinionFeed
import news.openwire.domain.opinion.OpinionKind
import news.openwire.domain.opinion.SocialPost
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Opinion and analysis columns (publisher feeds) and public posts (Bluesky, Mastodon),
 * read on the phone. Only the accounts followed and the words searched are sent, to the
 * networks' public APIs; results are kept for a few minutes.
 */
class OpinionSource(
    private val rss: GoogleNewsRssSource,
    private val normalizer: RssNormalizer,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Column(val article: Article, val kind: OpinionKind, val language: String)

    private data class Cached<T>(val at: Long, val value: T)
    private val columns = ConcurrentHashMap<String, Cached<List<Column>>>()
    private val posts = ConcurrentHashMap<String, Cached<List<SocialPost>>>()
    private val gate = Semaphore(5)

    private fun <T> ConcurrentHashMap<String, Cached<T>>.fresh(key: String, force: Boolean) =
        this[key]?.takeIf { !force && clock() - it.at < TTL }?.value

    /** Every column for [appLanguage] (and English), newest first. */
    suspend fun columns(appLanguage: String, force: Boolean = false): List<Column> {
        columns.fresh(appLanguage, force)?.let { return it }
        val list = coroutineScope {
            OpinionCatalog.feedsFor(appLanguage).mapIndexed { i, feed -> async { gate.withPermit { read(feed, i) } } }.awaitAll().flatten()
        }
        val cutoff = clock() - 4L * 24 * 60 * 60 * 1000
        val out = list.filter { (it.article.publishedAt ?: clock()) >= cutoff }
            .distinctBy { it.article.title.lowercase() }
            .sortedByDescending { it.article.publishedAt ?: 0L }
        if (out.isNotEmpty()) columns[appLanguage] = Cached(clock(), out)
        return out
    }

    private suspend fun read(feed: OpinionFeed, i: Int): List<Column> = runCatching {
        rss.fetchFeed(feed.url).items.take(25).mapIndexed { idx, item ->
            val a = normalizer.normalize(item, "OPINION", idx, feedName = feed.name, feedUrl = feed.url, idPrefix = "op$i")
            Column(a.copy(source = if (item.sourceName != null) a.source else feed.name.substringBefore(" · ")), feed.kind, feed.language)
        }
    }.getOrDefault(emptyList())

    /** Opinion writing on [topic] in [lang] (a Google News search). */
    suspend fun topicColumns(topic: String, lang: String, force: Boolean = false): List<Article> {
        val key = "t:$lang:$topic"
        columns.fresh(key, force)?.let { list -> return list.map { it.article } }
        val list = runCatching {
            rss.fetchFeed(OpinionCatalog.topicSearch(topic, lang)).items.take(30).mapIndexed { idx, item ->
                normalizer.normalize(item, "OPINION", idx, idPrefix = "ot${topic.hashCode()}")
            }
        }.getOrDefault(emptyList())
        columns[key] = Cached(clock(), list.map { Column(it, OpinionKind.OPINION, lang) })
        return list
    }

    /** Latest posts from the followed accounts, newest first. */
    suspend fun following(handles: Set<String>, force: Boolean = false): List<SocialPost> {
        if (handles.isEmpty()) return emptyList()
        val key = "f:" + handles.sorted().joinToString(",")
        posts.fresh(key, force)?.let { return it }
        val list = coroutineScope {
            handles.map { h -> async { gate.withPermit { get(Bluesky.authorFeedUrl(h))?.let(Bluesky::parse).orEmpty() } } }.awaitAll().flatten()
        }.sortedByDescending { it.createdAt ?: 0L }
        posts[key] = Cached(clock(), list)
        return list
    }

    /** Posts about [topic]: Bluesky search in [lang], plus the Mastodon hashtag. */
    suspend fun about(topic: String, lang: String, force: Boolean = false): List<SocialPost> {
        val key = "a:$lang:$topic"
        posts.fresh(key, force)?.let { return it }
        val list = coroutineScope {
            val bsky = async { get(Bluesky.searchUrl(topic, lang))?.let(Bluesky::parse).orEmpty() }
            val masto = async { if (lang == "ar") emptyList() else get(Mastodon.tagUrl(topic))?.let(Mastodon::parse).orEmpty() }
            bsky.await() + masto.await().take(8)
        }.distinctBy { it.text.take(80) }.sortedByDescending { (it.likes + 2 * it.reposts).coerceAtMost(500) + recency(it) }
        posts[key] = Cached(clock(), list)
        return list
    }

    private fun recency(p: SocialPost): Int {
        val hours = ((clock() - (p.createdAt ?: 0L)) / 3_600_000).toInt()
        return (48 - hours).coerceIn(0, 48) * 4
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", "Editrove/1.0 (Android news reader)").header("Accept", "application/json").build())
                .execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull()
    }

    private companion object { const val TTL = 10 * 60_000L }
}
