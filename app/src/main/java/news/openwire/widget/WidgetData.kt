// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import news.openwire.MainActivity
import news.openwire.OpenWireApp
import news.openwire.data.repository.toArticle
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Article
import news.openwire.domain.model.Topic

/** A story as a widget shows it: the article, its photo (if any) and whether it's saved. */
data class WidgetStory(val article: Article, val photo: Bitmap?, val saved: Boolean) {
    val source: String get() = (article.source ?: article.domain ?: "Editrove").uppercase()
    val age: String get() = NewsProcessor.formatRelativeTime(article.publishedAt).uppercase()
    val category: String get() = WidgetData.category(article)
}

/** Reads what the widgets show from the app's own cache — no network for the feed itself. */
object WidgetData {

    private fun container(context: Context) = (context.applicationContext as OpenWireApp).container

    /** The user's visible topics, in their order. */
    fun topics(context: Context): List<Topic> {
        val s = container(context).settingsRepository.settings.value
        return TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder).filter { it.isVisible }
    }

    /** Topics the Topic widget cycles through (Top Stories has its own widget; Latest isn't a topic). */
    fun switchableTopics(context: Context): List<Topic> = topics(context).filter { it.id != "TOP" && it.id != "LATEST" }

    /** Cached stories of a topic, in curated order. */
    suspend fun articles(context: Context, topicId: String): List<Article> = withContext(Dispatchers.IO) {
        runCatching { container(context).database.articleDao().getByTopic(topicId).map { it.toArticle() } }.getOrDefault(emptyList())
    }

    /** Refetches a topic when its cache is older than [maxAgeMs] (waits up to ~8 s). */
    suspend fun refreshIfStale(context: Context, topicId: String, maxAgeMs: Long = 5 * 60_000L) {
        val c = container(context)
        if (System.currentTimeMillis() - c.feedState.lastUpdated(topicId) < maxAgeMs) return
        val topic = topics(context).firstOrNull { it.id == topicId } ?: return
        withTimeoutOrNull(8_000) { runCatching { c.feedRepository.observeTopic(topic, force = true).collect { } } }
    }

    fun savedIds(context: Context): Set<String> = container(context).libraryStore.saved.value.map { it.id }.toSet()

    /** The first [count] stories with photos loaded at roughly [px] on the long side. */
    suspend fun stories(context: Context, topicId: String, count: Int, px: Int, photos: Int = count): List<WidgetStory> {
        val saved = savedIds(context)
        return articles(context, topicId).take(count).mapIndexed { i, a ->
            WidgetStory(a, if (i < photos) photo(context, a, px) else null, a.id in saved)
        }
    }

    private val bitmaps = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Lead photo, scaled down for RemoteViews (whose bitmap budget is small). */
    suspend fun photo(context: Context, a: Article, px: Int): Bitmap? {
        val url = a.imageUrl ?: withTimeoutOrNull(5_000) { container(context).imageResolver.resolve(a.originalUrl) } ?: return null
        val key = "$url@$px"
        bitmaps.get(key)?.let { return it }
        return withTimeoutOrNull(8_000) {
            val req = ImageRequest.Builder(context).data(url).size(px).allowHardware(false).build()
            val d = (context.imageLoader.execute(req) as? SuccessResult)?.drawable ?: return@withTimeoutOrNull null
            val b = d.toBitmap()
            val scale = px.toFloat() / maxOf(b.width, b.height)
            (if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true) else b)
        }?.also { bitmaps.put(key, it) }
    }

    fun findArticle(context: Context, id: String, candidates: List<Article>): Article? =
        candidates.firstOrNull { it.id == id } ?: container(context).libraryStore.saved.value.firstOrNull { it.id == id }

    /** Opens a story in Editrove's reader (routed around its paywall, like a notification tap). */
    fun openIntent(context: Context, a: Article): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_URL, a.originalUrl)
            putExtra(MainActivity.EXTRA_TITLE, a.title)
        }

    fun openApp(context: Context, tab: String? = null, topic: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            tab?.let { putExtra(MainActivity.EXTRA_TAB, it) }
            topic?.let { putExtra(MainActivity.EXTRA_TOPIC, it) }
        }

    private val SECTIONS = listOf(
        "Politics" to Regex("\\b(Trump|Biden|Senate|Congress|House|election|White House|governor|Supreme Court|lawmakers?|Democrats?|Republicans?|GOP)\\b", RegexOption.IGNORE_CASE),
        "World" to Regex("\\b(Iran|Israel|Gaza|Ukraine|Russia|China|NATO|Xi|Putin|Zelensky|Europe|EU|UN|Japan|India|Korea|Mexico|Canada)\\b", RegexOption.IGNORE_CASE),
        "Markets" to Regex("\\b(stocks?|markets?|Fed|economy|inflation|tariffs?|Nvidia|earnings|rates?|Wall Street|jobs)\\b", RegexOption.IGNORE_CASE),
        "Tech" to Regex("\\b(AI|Apple|Google|Microsoft|OpenAI|chips?|software|iPhone|Android|Meta|Tesla)\\b"),
        "Science" to Regex("\\b(NASA|space|climate|study|scientists?|species|planet|vaccine|health)\\b", RegexOption.IGNORE_CASE),
        "U.S." to Regex("\\b(NTSB|FBI|police|court|judge|shooting|crash|collision|flights?|airport|FAA|National Guard|immigration|ICE)\\b", RegexOption.IGNORE_CASE),
        "Weather" to Regex("\\b(storm|hurricane|waves|flood|wildfire|earthquake|snow|heat)\\b", RegexOption.IGNORE_CASE),
    )

    /** A short section label for category pills. */
    fun category(a: Article): String {
        val kind = news.openwire.domain.engine.FeedRanker.kind(a, null)
        return when (kind) {
            news.openwire.domain.engine.FeedRanker.Kind.SPORTS -> "Sports"
            news.openwire.domain.engine.FeedRanker.Kind.ENTERTAINMENT -> "Culture"
            news.openwire.domain.engine.FeedRanker.Kind.LIFESTYLE -> "Life"
            else -> SECTIONS.firstOrNull { it.second.containsMatchIn(a.title) }?.first ?: "Top story"
        }
    }
}

/** Gradient art RemoteViews can't draw itself: the waveform, mini equaliser and scrubber. */
object WidgetArt {
    // App palette: Rose (played / accent) and Indigo (still to come).
    private const val CYAN = 0xFF818CF8.toInt()
    private const val AMBER = 0xFFFB3768.toInt()

    private val SHAPE = floatArrayOf(
        0.28f, 0.55f, 0.82f, 0.45f, 0.92f, 0.68f, 1f, 0.52f, 0.76f, 0.40f, 0.88f, 0.64f, 0.95f, 0.48f, 0.72f, 0.85f, 0.38f, 0.90f,
        0.62f, 0.78f, 0.44f, 0.96f, 0.58f, 0.84f, 0.50f, 0.74f, 0.92f, 0.42f, 0.68f, 0.80f, 0.54f, 0.88f, 0.60f, 0.72f, 0.46f, 0.82f,
    )

    /**
     * Frequency bars. Bars before [progress] glow amber, the rest are cyan; while
     * [playing], the heights shift with [phase] so each refresh shows a new frame.
     */
    fun waveform(bars: Int, widthPx: Int, heightPx: Int, progress: Float, playing: Boolean, phase: Int, gradientBars: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gap = widthPx / (bars * 2.4f)
        val bw = (widthPx - gap * (bars - 1)) / bars
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until bars) {
            val base = SHAPE[(i + phase * 3) % SHAPE.size]
            val wobble = if (playing) 0.7f + 0.3f * SHAPE[(i * 7 + phase * 5) % SHAPE.size] else 0.62f
            val h = (heightPx * base * wobble).coerceAtLeast(bw)
            val x = i * (bw + gap)
            val r = RectF(x, heightPx - h, x + bw, heightPx.toFloat())
            if (gradientBars) {
                p.shader = LinearGradient(0f, heightPx.toFloat(), 0f, heightPx - h, CYAN, AMBER, Shader.TileMode.CLAMP)
            } else {
                p.shader = null
                p.color = if (i < progress * bars) AMBER else (CYAN and 0x00FFFFFF) or (0xBF shl 24)
            }
            c.drawRoundRect(r, bw / 2, bw / 2, p)
        }
        return bmp
    }

    /** Scrubber: a cyan→amber fill over a faint track. */
    fun progress(widthPx: Int, heightPx: Int, fraction: Float, night: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = heightPx / 2f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0x26FFFFFF else 0x1F0F172A }
        c.drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), r, r, track)
        val w = (widthPx * fraction.coerceIn(0f, 1f)).coerceAtLeast(heightPx.toFloat())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, w, 0f, CYAN, AMBER, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w, heightPx.toFloat()), r, r, fill)
        return bmp
    }
}
