// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import news.openwire.domain.engine.ImageExtractor
import news.openwire.domain.engine.PreviewMatcher
import news.openwire.domain.engine.PublisherUrlResolver
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.model.FeedSource
import okhttp3.OkHttpClient
import okhttp3.Request

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** The built-in source library, bundled from the PWA's shared feed catalog. */
class FeedCatalogSource(private val context: Context) {
    @Serializable
    private data class Dto(
        val id: String, val name: String, val url: String, val domain: String = "",
        val topics: List<String> = emptyList(), val maxArticles: Int = 8, val enabled: Boolean = true,
        val category: String? = null, val kind: String = "publisher", val access: String? = null,
        val origin: String? = null,
    )

    val catalog: List<FeedSource> by lazy {
        runCatching {
            val text = context.assets.open("feed_catalog.json").bufferedReader().use { it.readText() }
            lenientJson.decodeFromString<List<Dto>>(text).map {
                FeedSource(it.id, it.name, it.url, it.domain, it.topics, it.maxArticles, it.enabled, it.category, it.kind, false, it.access, extra = it.origin == "editrove")
            }
        }.getOrDefault(emptyList())
    }

    /** Google News pseudo-source + catalog + user feeds, for the library screen. */
    fun library(custom: List<FeedSource>): List<FeedSource> = listOf(SourceLibrary.GOOGLE_NEWS) + SourceLibrary.allSources(catalog, custom)
}

/**
 * Pipeline-published metadata (publisher URL map, preview index) from the static
 * site, with the bundled copy as an offline fallback. Cached in memory.
 */
interface MetadataProvider {
    suspend fun publisherUrls(): Map<String, String>
    suspend fun previews(): List<PreviewMatcher.Preview>
}

class PublishedMetadata(
    private val context: Context,
    private val baseUrl: String = NewsRemoteDataSource.DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build(),
) : MetadataProvider {
    private val mutex = Mutex()
    private var publisherMap: Map<String, String> = emptyMap()
    private var publisherAt = 0L
    private var previews: List<PreviewMatcher.Preview> = emptyList()
    private var previewsAt = 0L

    @Serializable
    private data class PreviewDto(val originalUrl: String? = null, val googleNewsUrl: String? = null, val summary: String? = null)

    private suspend fun text(file: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(baseUrl.trimEnd('/') + "/data/" + file).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull() ?: runCatching { context.assets.open("data/$file").bufferedReader().use { it.readText() } }.getOrNull()
    }

    override suspend fun publisherUrls(): Map<String, String> = mutex.withLock {
        if (System.currentTimeMillis() - publisherAt > 10 * 60_000L || publisherMap.isEmpty()) {
            text("publisher_urls.json")?.let { t ->
                runCatching { lenientJson.decodeFromString<Map<String, String>>(t) }.getOrNull()?.let {
                    publisherMap = PublisherUrlResolver.sanitizeMap(it)
                    publisherAt = System.currentTimeMillis()
                }
            }
        }
        publisherMap
    }

    override suspend fun previews(): List<PreviewMatcher.Preview> = mutex.withLock {
        if (System.currentTimeMillis() - previewsAt > 10 * 60_000L || previews.isEmpty()) {
            text("preview_articles.json")?.let { t ->
                runCatching { lenientJson.decodeFromString<List<PreviewDto>>(t) }.getOrNull()?.let { list ->
                    previews = list.take(600).mapNotNull { p -> p.summary?.takeIf { it.isNotBlank() }?.let { PreviewMatcher.Preview(p.originalUrl, p.googleNewsUrl, it) } }
                    previewsAt = System.currentTimeMillis()
                }
            }
        }
        previews
    }
}

/**
 * Finds a lead photo for stories whose feed carried none, by reading the article
 * page's OpenGraph/JSON-LD/figure images (js/imageService.js). Cached, rate-limited.
 */
class ImageResolver(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build(),
    /** Turns a news.google.com wrapper into the publisher's own URL (null if it can't). */
    private val resolveLink: suspend (String) -> String? = { null },
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val permits = Semaphore(3)

    suspend fun resolve(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val articleUrl = if (link.contains("news.google.com")) resolveLink(link) ?: return null else link
        cache[articleUrl]?.let { return it }
        misses[articleUrl]?.let { if (System.currentTimeMillis() - it < 10 * 60_000L) return null }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                val html = runCatching {
                    client.newCall(
                        Request.Builder().url(articleUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36")
                            .build(),
                    ).execute().use { r -> if (r.isSuccessful) r.body?.source()?.let { s -> s.request(400_000); s.buffer.readUtf8(minOf(s.buffer.size, 400_000)) } else null }
                }.getOrNull()
                ImageExtractor.fromHtml(html, articleUrl)
            }
        }.also { if (it != null) cache[articleUrl] = it else misses[articleUrl] = System.currentTimeMillis() }
    }
}

/**
 * High-resolution publisher logos (manifest / apple-touch icons from the home page) for
 * stories without a photo. Cached per domain; misses fall back to the favicon.
 */
class PublisherLogoResolver(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build(),
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val permits = Semaphore(2)

    suspend fun logo(domain: String?): String? {
        val d = domain?.lowercase()?.removePrefix("www.")?.takeIf { it.contains('.') } ?: return null
        cache[d]?.let { return it }
        misses[d]?.let { if (System.currentTimeMillis() - it < 60 * 60_000L) return null }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val home = "https://$d/"
                    val html = runCatching { text(home, 300_000) }.getOrNull() ?: runCatching { text("https://www.$d/", 300_000) }.getOrNull()
                    val found = html?.let { page ->
                        val fromPage = news.openwire.domain.engine.PublisherLogos.fromHtml(page, home)
                        val manifest = news.openwire.domain.engine.PublisherLogos.manifestUrl(page, home)
                        val fromManifest = manifest?.let { m -> runCatching { text(m, 100_000) }.getOrNull()?.let { news.openwire.domain.engine.PublisherLogos.fromManifest(it, m) } }.orEmpty()
                        news.openwire.domain.engine.PublisherLogos.best(fromManifest + fromPage)
                    }
                    // Sites that block the home page usually still serve the standard 180px touch icon.
                    found ?: listOf("https://www.$d/apple-touch-icon.png", "https://$d/apple-touch-icon.png").firstOrNull { isImage(it) }
                }.getOrNull()
            }
        }.also { if (it != null) cache[d] = it else misses[d] = System.currentTimeMillis() }
    }

    private fun isImage(url: String): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            r.isSuccessful && (r.body?.contentType()?.type == "image") && (r.body?.contentLength() ?: 0L) != 0L
        }
    }.getOrDefault(false)

    private val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"

    private fun text(url: String, max: Long): String? = client.newCall(
        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36").build(),
    ).execute().use { r -> if (r.isSuccessful) r.body?.source()?.let { s -> s.request(max); s.buffer.readUtf8(minOf(s.buffer.size, max)) } else null }
}
