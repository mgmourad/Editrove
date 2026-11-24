// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class ArticleContent(
    val title: String,
    val text: String,
    val imageUrl: String?,
    /** The publisher's standfirst / deck (og:description), when present. */
    val description: String? = null,
    /** Byline from the page metadata, when present. */
    val author: String? = null,
    /** Where the readable text came from (publisher page or an archive snapshot). */
    val via: TextSource = TextSource.PUBLISHER,
    /** The page the text was read from, for the "read at the source" link. */
    val viaUrl: String? = null,
    /** True when only a teaser could be read (paywall and no snapshot). */
    val partial: Boolean = false,
    /** Posts from X the story embeds (x.com status links), shown as cards under the text. */
    val embeds: List<String> = emptyList(),
    /** Photos and videos from the story, each placed after the paragraph it follows. */
    val media: List<ArticleMedia> = emptyList(),
    /** For [TextSource.OTHER_OUTLETS]: the outlet whose report this is (credited in the reader). */
    val viaName: String? = null,
    /** For [TextSource.OTHER_OUTLETS]: every outlet read for the combined summary. */
    val otherOutlets: List<OtherOutlet> = emptyList(),
    /** For [TextSource.OTHER_OUTLETS]: key points drawn from all of [otherOutlets]. */
    val combinedPoints: List<String> = emptyList(),
) {
    val paragraphs: List<String> get() = text.split("\n\n").filter { it.isNotBlank() }
    val wordCount: Int get() = text.split(RX_1).count { it.isNotBlank() }

    /** Reading time at ~230 wpm, never less than one minute. */
    val readMinutes: Int get() = maxOf(1, (wordCount + 229) / 230)

    /** Narration length at ~160 wpm (typical TTS pace), in seconds. */
    val narrationSeconds: Int get() = maxOf(30, wordCount * 60 / 160)

    /**
     * A sentence suitable for an editorial pull quote: the first direct quotation
     * of reasonable length found in the body (never invented).
     */
    val pullQuote: String?
        get() = QUOTE.findAll(text)
            .map { it.groupValues[1].trim().trimEnd(',', ';', ':', ' ') }
            .firstOrNull { it.length in 50..220 }
            ?.let { "“$it”" }

    private companion object {
        val QUOTE = Regex("[“\"]([^”\"]{50,220})[”\"]")
    }
}

/** A photo or video inside a story. [after] is the index of the paragraph it follows. */
data class ArticleMedia(
    val kind: Kind,
    val url: String,
    val poster: String? = null,
    val caption: String? = null,
    val after: Int = 0,
) {
    enum class Kind {
        IMAGE,
        /** A video file or stream (mp4, HLS), played in the reader. */
        VIDEO,
        /** A YouTube / Vimeo player: shown as a card that opens the video. */
        EXTERNAL,
    }
}

/** Another newsroom's report of the same story. */
data class OtherOutlet(val name: String, val url: String)

enum class TextSource(val label: String) {
    PUBLISHER("Publisher"),
    WAYBACK("Wayback Machine"),
    ARCHIVE_TODAY("archive.today"),
    /** The original wouldn't load: the same story as other newsrooms reported it. */
    OTHER_OUTLETS("Other outlets"),
}

/**
 * Pulls an article's readable text for the in-app reader, falling back through
 * sources that serve real HTML (bypass sites such as smry.ai render client-side,
 * so they cannot be read from here):
 *
 * 1. the publisher page (soft/JS paywalls usually ship the full text);
 * 2. the newest Wayback Machine snapshot (raw `id_` capture);
 * 3. the newest archive.today snapshot.
 *
 * The best complete result wins; otherwise the longest teaser is returned, marked partial.
 */
class ArticleTextExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
    /** Turns a news.google.com wrapper into the publisher's own URL (null if it can't). */
    private val resolveLink: suspend (String) -> String? = { null },
    /** Loads a page the way a browser does (for sites that refuse plain HTTP clients). */
    private val renderPage: suspend (String) -> String? = { null },
    /** True for hard/metered-paywall publishers (from the paywall registry). */
    private val isPaywalled: (String) -> Boolean = { false },
    /** Logs each extraction's source, size and time (diagnostics). */
    private val diagnostics: Boolean = false,
    /** Google News search (for the same story from other outlets). */
    private val rss: GoogleNewsRssSource = GoogleNewsRssSource(),
) {
    private companion object {
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        /** Longest the reader waits for a complete article before showing the best partial. */
        const val BUDGET_MS = 20_000L
        /** A paywalled publisher page at least this long is the full article, not a teaser. */
        const val PAYWALL_FULL_CHARS = 2_500
        /** How long archives get once a paywall teaser is in hand. */
        const val TEASER_GRACE_MS = 6_000L
        /** Site chrome that leaks into `<p>` tags (comment prompts, newsletter and cookie banners…). */
        val BOILERPLATE = listOf(
            "commenting policy", "sign up for", "newsletter", "subscribe to", "all rights reserved",
            "cookie", "click here", "advertisement", "follow us on", "download our app",
            "javascript", "your browser", "copyright ©", "terms of use", "privacy policy",
            // "About us" blurbs from site footers and sidebars.
            "is provided by", "launched in 20", "launched in 19", "media company", "sign-in, which works",
            "download the app", "watch live", "all rights", "is a leading", "our journalists", "send us a tip",
            "support trusted journalism", "support our journalism", "become a member", "donate now", "make a gift",
            // Privacy and consent notices (CCPA/GDPR dialogs) that pages ship as plain paragraphs.
            "right to opt-out", "right to opt out", "opt-out of the sale", "opt out of the sale", "sale or sharing of your personal",
            "sharing of your personal information", "processing of your personal data", "targeted advertising", "do not sell or share",
            "your privacy choices", "accept all cookies", "manage your preferences", "manage preferences", "by continuing to use",
            "consumer, you have the right", "using this toggle", "we and our partners", "store and/or access information",
            "personalised ads", "personalized ads", "legitimate interest",
            // Reprint and licensing notices.
            "non-commercial use only", "follow this section", "follow this tag", "follow this author", "personalize your feed",
            "update your preferences in account settings", "open follow modal", "subscriber agreement", "djreprints", "order multiple copies", "order presentation-ready copies",
        )
        val PAYWALL_HINTS = listOf(
            "subscribe to continue", "to continue reading", "already a subscriber", "subscribers only",
            "create a free account", "this article is for subscribers", "log in to continue", "keep this page open until",
        )
    }

    private fun complete(c: ArticleContent?, paywalled: Boolean = true): Boolean {
        if (c == null || PAYWALL_HINTS.any { c.text.lowercase().contains(it) }) return false
        // Paywalled sites tease a few paragraphs, so they need a full-length text; anywhere
        // else a short article is simply short — don't make the reader wait on archives.
        return if (paywalled) c.text.length >= 900 && c.paragraphs.size >= 4 else c.text.length >= 350 && c.paragraphs.size >= 2
    }

    /** Paywalled/blocked domains: archives start right away instead of after the publisher fails. */
    private val archiveFirst: (String) -> Boolean = isPaywalled

    /**
     * Every source races at once — the publisher, a browser render (blocked or script-built
     * pages), the Wayback Machine and archive.today — and the first complete article wins.
     * If none is complete within [BUDGET_MS], the longest partial text is returned.
     */
    suspend fun extract(link: String, title: String? = null): ArticleContent? = kotlinx.coroutines.coroutineScope {
        val started = System.currentTimeMillis()
        // Google News wrappers carry no article: read the publisher's page instead.
        val url = if (news.openwire.domain.engine.GoogleNewsUrls.isGoogleNewsUrl(link)) {
            resolveLink(link) ?: run {
                if (diagnostics) android.util.Log.i("EditroveReader", "${System.currentTimeMillis() - started}ms UNRESOLVED ${link.take(90)}")
                return@coroutineScope null
            }
        } else link
        val refused = news.openwire.domain.engine.FeedDiscovery.domainOf(url)?.let { it in refusing } == true
        val paywalled = refused || runCatching { archiveFirst(url) }.getOrDefault(false)
        val results = kotlinx.coroutines.channels.Channel<ArticleContent?>(capacity = 8)
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        fun race(delayMs: Long = 0, block: suspend () -> ArticleContent?) {
            jobs += this@coroutineScope.launch {
                if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                results.send(runCatching { block() }.getOrNull())
            }
        }
        val publisher = this@coroutineScope.async { extractFrom(url)?.copy(via = TextSource.PUBLISHER, viaUrl = url) }
        race { publisher.await() }
        // Browser render when the plain fetch is refused or thin (France 24, script-built pages).
        race {
            val p = publisher.await()
            if (p != null && p.text.length >= 600) null
            else renderPage(url)?.let { parse(it, url) }?.copy(via = TextSource.PUBLISHER, viaUrl = url)
        }
        // Archives: immediately for paywalled sites, otherwise only if the publisher is slow.
        val archiveDelay = if (paywalled) 0L else 4_000L
        race(archiveDelay) {
            if (!paywalled && complete(publisher.await(), false)) null
            else waybackSnapshot(url)?.let { snap -> extractFrom(snap.second)?.copy(via = TextSource.WAYBACK, viaUrl = snap.first) }
        }
        race(archiveDelay) {
            if (!paywalled && complete(publisher.await(), false)) null
            else extractFrom("https://archive.ph/newest/$url")?.copy(via = TextSource.ARCHIVE_TODAY, viaUrl = "https://archive.ph/newest/$url")
        }
        val got = mutableListOf<ArticleContent>()
        var winner: ArticleContent? = null
        var deadline = started + BUDGET_MS
        var pending = jobs.size
        while (pending > 0 && winner == null) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val r = kotlinx.coroutines.withTimeoutOrNull(left) { results.receive() } ?: if (System.currentTimeMillis() >= deadline) break else { pending--; continue }
            pending--
            // Text that isn't about this headline (a privacy notice, another story) never counts.
            if (!relevant(r, title)) {
                if (diagnostics) android.util.Log.i("EditroveReader", "off-topic ${r.via} ${r.paragraphs.firstOrNull()?.take(80)}")
                continue
            }
            got += r
            // A paywalled publisher's own page is usually a teaser: it only counts when it is
            // clearly the whole piece; otherwise give the archives a few more seconds, then stop.
            val ok = if (paywalled && r.via == TextSource.PUBLISHER) complete(r) && r.text.length >= PAYWALL_FULL_CHARS else complete(r, paywalled || r.via != TextSource.PUBLISHER)
            if (ok) winner = r
            else if (paywalled && r.via == TextSource.PUBLISHER && r.text.length >= 300) deadline = minOf(deadline, System.currentTimeMillis() + TEASER_GRACE_MS)
        }
        jobs.forEach { it.cancel() }
        publisher.cancel()
        val meta = got.firstOrNull { it.via == TextSource.PUBLISHER }
        val out = winner?.let { if (it.via == TextSource.PUBLISHER) it else it.mergeMeta(meta) }
            ?: got.maxByOrNull { it.text.length }?.let { (if (it.via == TextSource.PUBLISHER) it else it.mergeMeta(meta))?.copy(partial = true) }
        // Nothing readable (the site turned the reader away and no archive has it yet): the same
        // story from other newsrooms, credited in the reader. Works for any outlet, not a list.
        val final = if ((out == null || (out.partial && out.text.length < 1_200)) && !title.isNullOrBlank()) {
            runCatching { fromOtherOutlets(title, url) }.getOrNull()?.let { o -> if (out != null && out.text.length > o.text.length) out else o.mergeMeta(meta) ?: o } ?: out
        } else out
        if (diagnostics) android.util.Log.i("EditroveReader", "${System.currentTimeMillis() - started}ms ${final?.via ?: "NONE"} ${final?.text?.length ?: 0}ch partial=${final?.partial} paywalled=$paywalled refused=$refused ${url.take(90)}")
        final
    }

    /**
     * The same story from other newsrooms, for when the original won't load: Google News finds
     * reports with the same headline from other outlets (open ones first), the best full text
     * is shown with its outlet credited, and key points are drawn from every report read.
     */
    suspend fun fromOtherOutlets(title: String, originalUrl: String?, country: String = "US"): ArticleContent? {
        val reports = otherReports(title, originalUrl, country)
        val best = reports.firstOrNull() ?: return null
        val clean = cleanTitle(title)
        // Key points: the chosen report's first, then the other full reports' (short clips and promos left out).
        val full = reports.drop(1).filter { it.text.length >= 1_200 && match(it, clean) >= 2 }
        val points = (news.openwire.domain.engine.Summarizer.keyPoints(best.text, 3) +
            news.openwire.domain.engine.Summarizer.keyPoints(full.joinToString("\n\n") { it.text }, 3))
            .distinct().take(5)
        return best.copy(
            title = clean,
            // The deck and byline were the other outlet's: the reader credits it in a banner instead.
            description = null, author = null,
            otherOutlets = reports.map { OtherOutlet(it.viaName ?: "", it.viaUrl ?: "") },
            combinedPoints = points,
        )
    }

    private fun cleanTitle(title: String) = title.substringBeforeLast(" - ").substringBeforeLast(" | ").trim()

    private fun match(c: ArticleContent, clean: String) = titleOverlap(c.title + " " + c.paragraphs.take(3).joinToString(" "), clean)

    /**
     * Every other newsroom's full report of the story behind [title] (Google News search on the
     * headline's distinctive words; open sites first; each read and checked to be about it),
     * the most detailed close match first. Each carries its outlet ([ArticleContent.viaName],
     * [ArticleContent.viaUrl]) so it can be credited.
     */
    suspend fun otherReports(title: String, originalUrl: String?, country: String = "US", max: Int = 6): List<ArticleContent> = kotlinx.coroutines.coroutineScope {
        val clean = cleanTitle(title)
        if (clean.length < 12) return@coroutineScope emptyList()
        val home = originalUrl?.let { news.openwire.domain.engine.FeedDiscovery.domainOf(it) }
        val c = news.openwire.domain.engine.TopicRegistry.country(country)
        // The headline's distinctive words, not the whole sentence: other outlets word it differently.
        val keywords = RX_WORDS.findAll(clean).map { it.value }.filter { it.length >= 4 && it.lowercase() !in STOP_WORDS }.distinct().take(7).joinToString(" ")
        val items = runCatching { rss.fetchFeed(news.openwire.domain.engine.TopicRegistry.googleNewsSearchUrl("${keywords.ifBlank { clean }} when:7d", c)).items }.getOrDefault(emptyList())
        if (diagnostics) android.util.Log.i("EditroveReader", "other outlets: ${items.size} results for '$keywords'")
        val candidates = items.filter { item ->
            val d = item.source?.let { news.openwire.domain.engine.FeedDiscovery.domainOf(it) }
            d != null && d != home && aboutTitle(item.title, clean)
        }.distinctBy { it.source }
            // Open sites first: a paywalled outlet would stall here just like the original.
            .sortedBy { item -> runCatching { isPaywalled(item.source.orEmpty()) }.getOrDefault(false) }
            .take(max + 2)
        val reads = candidates.map { item ->
            async {
                withTimeoutOrNull(12_000) {
                    val url = if (news.openwire.domain.engine.GoogleNewsUrls.isGoogleNewsUrl(item.link)) resolveLink(item.link) else item.link
                    url ?: return@withTimeoutOrNull null
                    extractFrom(url)?.takeIf { complete(it, false) && relevant(it, clean) }?.copy(
                        via = TextSource.OTHER_OUTLETS, viaUrl = url, partial = false,
                        viaName = item.sourceName ?: news.openwire.domain.engine.FeedDiscovery.domainOf(url) ?: url,
                    )
                }
            }
        }.awaitAll().filterNotNull()
        if (reads.isEmpty()) return@coroutineScope emptyList()
        // Close matches to the headline (a related feature isn't the story), the most detailed first.
        val top = reads.maxOf { match(it, clean) }
        reads.sortedWith(
            compareByDescending<ArticleContent> { match(it, clean) >= top - 1 }.thenByDescending { it.text.length + it.media.size * 400 },
        ).take(max)
    }

    /** Keeps the publisher's own deck/byline/photo when the text came from an archive. */
    private fun ArticleContent?.mergeMeta(publisher: ArticleContent?): ArticleContent? = this?.copy(
        description = publisher?.description ?: description,
        author = publisher?.author ?: author,
        imageUrl = publisher?.imageUrl ?: imageUrl,
    )

    /** (viewer URL, raw capture URL) of the closest Wayback snapshot, if one exists. */
    private suspend fun waybackSnapshot(url: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val api = "https://archive.org/wayback/available?url=" + java.net.URLEncoder.encode(url, "UTF-8")
            val body = get(api) ?: return@withContext null
            val snap = RX_2.find(body)?.groupValues?.get(1) ?: return@withContext null
            val viewer = snap.replaceFirst("http:", "https:")
            val raw = RX_3.replace(viewer) { "/web/${it.groupValues[1]}id_/" }
            viewer to raw
        } catch (e: Exception) {
            null
        }
    }

    private fun isBoilerplate(p: String): Boolean {
        if (BARE_URL.matches(p.trim())) return true
        val l = p.lowercase()
        return BOILERPLATE.any { it in l }
    }

    private val previews = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * A short opening for a feed card: the publisher's standfirst, else the story's first
     * paragraph. One plain page fetch (no browser render or archive race), cached.
     */
    suspend fun preview(link: String, title: String): String? {
        previews[link]?.let { return it.ifEmpty { null } }
        val url = if (news.openwire.domain.engine.GoogleNewsUrls.isGoogleNewsUrl(link)) resolveLink(link) ?: return null else link
        fun pick(c: ArticleContent?) = (listOfNotNull(c?.description) + c?.paragraphs?.take(3).orEmpty())
            .map { it.trim() }
            .firstOrNull { it.length > 40 && news.openwire.domain.engine.FeedWeaver.calculateTitleSimilarity(it, title) < 0.7 && aboutTitle(it, title) }
        // A plain fetch first; pages built by scripts get the reader's full extraction.
        val text = pick(withTimeoutOrNull(8_000) { extractFrom(url) }) ?: pick(withTimeoutOrNull(20_000) { extract(url) })
        previews[link] = text.orEmpty()
        return text
    }

    /**
     * True when [text] is about the headline: it shares at least two of the headline's
     * significant words (one for very short headlines). Pages read without scripts sometimes
     * offer another story's opening (a "most read" box), and a card must never show that.
     */
    /** How many of the headline's significant words [text] shares. */
    internal fun titleOverlap(text: String, title: String): Int {
        fun words(t: String) = RX_WORDS.findAll(t.lowercase()).map { it.value }.filter { it.length >= 4 && it !in STOP_WORDS }.toSet()
        val body = words(text)
        return words(title).count { w -> w in body || (w.length >= 5 && body.any { it.startsWith(w.take(5)) }) }
    }

    internal fun aboutTitle(text: String, title: String): Boolean {
        fun words(t: String) = RX_WORDS.findAll(t.lowercase()).map { it.value }.filter { it.length >= 4 && it !in STOP_WORDS }.toSet()
        val head = words(title)
        if (head.isEmpty()) return false
        val shared = head.count { w -> w in words(text) || words(text).any { it.startsWith(w.take(5)) && w.length >= 5 } }
        return shared >= if (head.size < 4) 1 else 2
    }

    /**
     * True when an extracted text is the story behind [title]: its opening shares the
     * headline's words. Rejects consent notices, paywall prompts and the next story on
     * endless-scroll pages, which would otherwise be read (and narrated) as the article.
     */
    internal fun relevant(c: ArticleContent, title: String?): Boolean {
        val t = title?.takeIf { it.isNotBlank() } ?: c.title.takeIf { it.isNotBlank() } ?: return true
        // The body itself must be about the headline: a deck alone can't vouch for a reprint notice.
        val opening = c.paragraphs.take(6).joinToString(" ").ifBlank { return false }
        return aboutTitle(opening, t.substringBeforeLast(" - ").substringBeforeLast(" | "))
    }

    private suspend fun extractFrom(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            get(url)?.let { parse(it, url) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(html: String, baseUrl: String = ""): ArticleContent? {
        try {
            val doc = Jsoup.parse(html, baseUrl)
            // Captions are page chrome to the text finder: keep each on its figure first.
            doc.select("figure").forEach { f ->
                f.selectFirst("figcaption")?.text()?.trim()?.takeIf { it.length in 3..300 }?.let { f.attr("data-ew-caption", it) }
            }
            val title = doc.selectFirst("h1")?.text().orEmpty().ifEmpty { doc.title() }
            // Embedded X posts become cards; their text isn't part of the story's paragraphs.
            val embeds = embeddedPosts(doc)
            val paragraphs = articleParagraphs(doc)
            val text = paragraphs.joinToString("\n\n")
            if (text.isBlank()) return null
            val imageUrl = doc.meta("og:image") ?: doc.selectFirst("article img")?.attr("src")
            val media = runCatching { articleMedia(doc, paragraphs, imageUrl) }.getOrDefault(emptyList())
            return ArticleContent(
                title = title,
                text = text,
                imageUrl = imageUrl,
                description = (doc.meta("og:description") ?: doc.meta("description"))
                    ?.replace(MD_LINK, "$1")?.replace(MD_LINK_CUT, "$1")?.trim()
                    ?.takeIf { it.length > 30 },
                author = (doc.meta("author") ?: doc.meta("article:author"))
                    ?.takeIf { it.isNotBlank() && !it.startsWith("http") && it.length < 80 },
                embeds = embeds,
                media = media,
            )
        } catch (e: Exception) {
            return null
        }
    }

    /** X posts embedded in the page (removed from it), as canonical x.com links. */
    internal fun embeddedPosts(doc: Document): List<String> {
        val found = LinkedHashSet<String>()
        doc.select("blockquote.twitter-tweet, blockquote.twitter-video").forEach { q ->
            q.select("a[href]").map { it.attr("href") }.lastOrNull { news.openwire.domain.opinion.XPosts.canonical(it) != null }
                ?.let { news.openwire.domain.opinion.XPosts.canonical(it) }?.let(found::add)
            q.remove()
        }
        // Pages rendered with X's script carry the post id on the widget instead.
        doc.select("[data-tweet-id]").forEach { e ->
            val id = e.attr("data-tweet-id").takeIf { it.all(Char::isDigit) && it.length > 5 } ?: return@forEach
            found += "https://x.com/i/status/$id"
            e.remove()
        }
        return found.take(8)
    }

    /**
     * Photos and videos that sit between the story's paragraphs (in page order). Anything
     * before the first paragraph (the lead photo, shown already) or after the last (related
     * stories, ads) is left out, as are logos, icons and tracking pixels.
     */
    internal fun articleMedia(doc: Document, paragraphs: List<String>, hero: String?): List<ArticleMedia> {
        if (paragraphs.size < 2) return emptyList()
        val index = HashMap<String, Int>()
        paragraphs.forEachIndexed { i, p -> index.putIfAbsent(p, i) }
        val body = doc.body() ?: return emptyList()
        val consumed = HashSet<org.jsoup.nodes.Element>()
        val out = ArrayList<ArticleMedia>()
        val seen = HashSet<String>()
        hero?.let { seen += mediaKey(it) }
        var last = -1
        for (e in body.select("p, figure, picture, img, video, iframe")) {
            if (e in consumed) continue
            if (e.tagName() == "p") {
                // Stripped of a site banner, the first paragraph may not match: it's paragraph 0.
                val i = index[e.text().trim()] ?: if (last < 0 && paragraphs[0].length > 40 && e.text().trim().endsWith(paragraphs[0])) 0 else null
                if (i != null) last = maxOf(last, i)
                continue
            }
            consumed += e.allElements
            if (last < 0) continue
            val m = mediaOf(e) ?: continue
            if (!seen.add(mediaKey(m.url))) continue
            out += m.copy(after = last)
        }
        // After the story's last paragraph is where related links and ads live.
        return out.filter { it.after < paragraphs.lastIndex }.take(12)
    }

    private fun mediaKey(url: String) = url.substringBefore('?').substringBefore('#').removePrefix("https:").removePrefix("http:").lowercase()

    private fun captionOf(e: org.jsoup.nodes.Element): String? =
        (if (e.tagName() == "figure") e else e.closest("figure"))?.attr("data-ew-caption")?.takeIf { it.isNotBlank() }

    private fun mediaOf(e: org.jsoup.nodes.Element): ArticleMedia? {
        when (e.tagName()) {
            "figure" -> {
                (e.selectFirst("video") ?: e.selectFirst("iframe"))?.let { return mediaOf(it)?.copy(caption = captionOf(e)) }
                return e.selectFirst("picture, img")?.let { imageOf(it) }?.let { ArticleMedia(ArticleMedia.Kind.IMAGE, it, caption = captionOf(e)) }
            }
            "picture", "img" -> return imageOf(e)?.let {
                val alt = e.selectFirst("img")?.attr("alt")?.trim()?.takeIf { a -> a.length in 25..200 } ?: e.attr("alt").trim().takeIf { a -> a.length in 25..200 }
                ArticleMedia(ArticleMedia.Kind.IMAGE, it, caption = captionOf(e) ?: alt)
            }
            "video" -> {
                val src = (listOf(e.absUrl("src")) + e.select("source[src]").map { it.absUrl("src") })
                    .filter { it.startsWith("http") }
                    .sortedByDescending { it.contains(".mp4") || it.contains(".m3u8") }
                    .firstOrNull() ?: return null
                val poster = e.absUrl("poster").takeIf { it.startsWith("http") }
                return ArticleMedia(ArticleMedia.Kind.VIDEO, src, poster = poster, caption = captionOf(e))
            }
            "iframe" -> {
                val src = e.absUrl("src").ifBlank { e.absUrl("data-src") }
                YOUTUBE.find(src)?.groupValues?.get(1)?.let { id ->
                    return ArticleMedia(ArticleMedia.Kind.EXTERNAL, "https://www.youtube.com/watch?v=$id", poster = "https://i.ytimg.com/vi/$id/hqdefault.jpg", caption = captionOf(e) ?: e.attr("title").takeIf { it.length > 3 })
                }
                VIMEO.find(src)?.groupValues?.get(1)?.let { id ->
                    return ArticleMedia(ArticleMedia.Kind.EXTERNAL, "https://vimeo.com/$id", caption = captionOf(e) ?: e.attr("title").takeIf { it.length > 3 })
                }
                return null
            }
        }
        return null
    }

    /** The best address of a story photo (lazy-loading and responsive sources included), or null for icons and pixels. */
    private fun imageOf(e: org.jsoup.nodes.Element): String? {
        val img = if (e.tagName() == "img") e else e.selectFirst("img")
        // Bylines, author bios and logos carry small portraits: never story photos.
        if (generateSequence(e) { it.parent() }.take(6).any { a -> SMALL_CONTAINER.containsMatchIn(a.className() + " " + a.id()) }) return null
        val sources = listOfNotNull(img) + e.select("source")
        var widest = 0.0
        fun largest(srcset: String): String? = srcset.split(SRCSET_SPLIT).mapNotNull { part ->
            val bits = part.trim().split(RX_1)
            val d = bits.getOrNull(1)
            val w = d?.trimEnd('w', 'x')?.toDoubleOrNull() ?: 1.0
            if (d?.endsWith("w") == true) widest = maxOf(widest, w)
            bits.firstOrNull()?.takeIf { it.isNotBlank() }?.let { it to w }
        }.maxByOrNull { it.second }?.first
        val raw = sources.firstNotNullOfOrNull { s ->
            listOf("data-srcset", "srcset").firstNotNullOfOrNull { a -> s.attr(a).takeIf { it.isNotBlank() }?.let(::largest) }
        } ?: img?.let { i -> listOf("data-src", "data-lazy-src", "data-original", "data-url", "src").firstNotNullOfOrNull { a -> i.attr(a).takeIf { it.isNotBlank() && !it.startsWith("data:") } } }
            ?: return null
        val url = runCatching { java.net.URL(java.net.URL(e.baseUri().ifBlank { "https://example.invalid/" }), raw.trim()).toString() }.getOrNull() ?: return null
        if (!url.startsWith("http") || url.startsWith("https://example.invalid")) return null
        val lower = url.lowercase().substringBefore('?')
        if (lower.endsWith(".svg") || SMALL_IMAGE.containsMatchIn(lower)) return null
        val w = img?.attr("width")?.toIntOrNull()
        val h = img?.attr("height")?.toIntOrNull()
        // Small renditions look blurry in the reader: a story photo is at least ~480 px wide.
        if ((w != null && w < 480) || (h != null && h < 240) || (widest > 0 && widest < 480)) return null
        LOW_RES_HINT.find(lower)?.let { m -> m.groupValues.drop(1).firstNotNullOfOrNull { it.toIntOrNull() }?.let { if (it < 480) return null } }
        return url
    }

    /**
     * The story's own paragraphs, the way a reading mode finds them: page chrome
     * (navigation, headers, footers, sidebars, related links, share bars) is dropped,
     * then the container holding the most paragraph text is taken as the article.
     */
    internal fun articleParagraphs(doc: Document): List<String> {
        // Drop page chrome, but never a block holding much of the page's paragraph text:
        // layout wrappers are often named "has-sidebar", "share-layout"… and contain the story.
        fun pText(e: org.jsoup.nodes.Element) = e.select("p").sumOf { p -> p.text().length.takeIf { it > 40 } ?: 0 }
        val total = pText(doc.body() ?: doc).coerceAtLeast(1)
        doc.select(CHROME).filter { pText(it) < total * 0.25 }.forEach { it.remove() }
        val siteName = doc.meta("og:site_name")
        // Reader-mode scoring: each paragraph credits its parent fully and its grandparent by
        // half, so stories split across many small blocks still find their common container.
        val scores = HashMap<org.jsoup.nodes.Element, Double>()
        for (p in doc.select("p")) {
            val len = p.text().length
            if (len <= 40) continue
            val score = 1.0 + minOf(len / 100.0, 3.0) + p.text().count { it == ',' } * 0.2
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2 }
        }
        val best = scores.maxByOrNull { it.value }?.key
        // Endless-scroll pages put the next stories right under this one: read the first only.
        fun firstStory(scope: org.jsoup.nodes.Element): List<org.jsoup.nodes.Element> {
            val stories = scope.select("article").filter { pText(it) > 300 }
            val root = if (stories.size >= 2) stories.first() else scope
            val out = ArrayList<org.jsoup.nodes.Element>()
            for (e in root.select("p, h1")) {
                // A second headline ends the story — but only once the story itself has begun
                // (menus and follow prompts above the headline don't count as the story).
                if (e.tagName() == "h1") {
                    if (out.count { it.text().length > 80 && !isBoilerplate(it.text()) } >= 2) break else continue
                }
                out += e
            }
            return out
        }
        val inBest = best?.let(::firstStory).orEmpty()
        val source = if (inBest.size >= 3 && pText(best!!) >= total * 0.4) inBest else firstStory(doc.body() ?: doc)
        val fromParagraphs = source.map { it.text().trim() }
            .map { stripBanner(it, siteName) }
            .filter { it.length > 40 && !isBoilerplate(it) }
            .distinct()
            .take(40)
        // Many publishers also put the full text in their structured data (JSON-LD articleBody).
        val fromJsonLd = jsonLdBody(doc)
        return if (fromJsonLd.sumOf { it.length } > fromParagraphs.sumOf { it.length } * 1.3) fromJsonLd else fromParagraphs
    }

    /** The article text from JSON-LD `articleBody`, split into paragraphs. */
    private fun jsonLdBody(doc: Document): List<String> {
        val body = doc.select("script[type=application/ld+json]").asSequence()
            .mapNotNull { s -> ARTICLE_BODY.find(s.data())?.groupValues?.get(1) }
            .maxByOrNull { it.length } ?: return emptyList()
        val text = runCatching { kotlinx.serialization.json.Json.decodeFromString<String>("\"$body\"") }.getOrDefault(body)
        val clean = Jsoup.parse(text).text().takeIf { '<' in text } ?: text
        val parts = clean.split(PARA_BREAK).map { it.trim() }.filter { it.length > 40 }
        val paras = if (parts.size >= 3) parts else {
            // One long block: regroup sentences into paragraphs of ~3.
            val sentences = clean.split(SENTENCE).map { it.trim() }.filter { it.isNotEmpty() }
            sentences.chunked(3).map { it.joinToString(" ") }.filter { it.length > 40 }
        }
        return paras.filter { !isBoilerplate(it) }.take(40)
    }

    /** "DC News Now | DC, Virginia… Live WASHINGTON (DC News Now) — Service…" → from the dateline on. */
    private fun stripBanner(p: String, siteName: String?): String {
        val head = p.take(220)
        if (!head.contains(" | ") && (siteName == null || !head.startsWith(siteName))) return p
        val m = DATELINE.find(p) ?: return p
        return if (m.range.first in 1..220) p.substring(m.range.first) else p
    }

    private fun Document.meta(key: String): String? =
        (selectFirst("meta[property=$key]") ?: selectFirst("meta[name=$key]"))
            ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

    private fun get(url: String): String? {
        fetch(url, MOBILE_UA)?.let { return it }
        // Some sites refuse phone-style requests but serve a desktop browser.
        return fetch(url, DESKTOP_UA)
    }

    private fun fetch(url: String, ua: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            // A site that turns readers away (401/403, or a 429 bot wall) is remembered for this run.
            if (response.code in REFUSED) news.openwire.domain.engine.FeedDiscovery.domainOf(url)?.let { refusing += it }
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /** Sites that refused the reader this session: their stories go straight to archives and other outlets. */
    private val refusing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val REFUSED = setOf(401, 403, 429, 451)
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("\\s+")
private val RX_2 = Regex("\"closest\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
private val RX_3 = Regex("/web/(\\d+)/")
private const val CHROME = "nav, header, footer, aside, form, script, style, noscript, figcaption, [role=navigation], [role=banner], " +
    "[role=contentinfo], [class*=related], [class*=newsletter], [class*=promo], [class*=share], [class*=social], " +
    "[class*=footer], [id*=footer], [class*=sidebar], [class*=breadcrumb], [class*=cookie], [class*=subscribe], [aria-hidden=true], " +
    "[class*=consent], [id*=consent], [class*=privacy], [id*=privacy], [class*=gdpr], [class*=ccpa], [class*=onetrust], [id*=onetrust], " +
    "[id^=sp_message], [class*=paywall], [class*=modal], [role=dialog], [aria-modal=true], [class*=recirc], [class*=read-next], [class*=up-next]"
/** A wire-style dateline: "WASHINGTON (DC News Now) —" or "LONDON (Reuters) -". */
private val DATELINE = Regex("\\b[A-Z][A-Z .,'’-]{2,40}\\s*\\([^)]{2,40}\\)\\s*[—–-]")
private val ARTICLE_BODY = Regex("\"articleBody\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
private val SENTENCE = Regex("(?<=[.!?])\\s+(?=[A-Z\"“])")
private val PARA_BREAK = Regex("\\n\\s*\\n|\\n")

private val RX_WORDS = Regex("\\p{L}+")
/** Words too common to tell two stories apart. */
private val STOP_WORDS = setOf(
    "that", "this", "with", "from", "have", "has", "they", "their", "them", "there", "about", "after", "before", "over", "under",
    "what", "when", "where", "which", "while", "will", "would", "could", "should", "were", "been", "being", "more", "most",
    "than", "then", "into", "onto", "says", "said", "your", "yours", "other", "others", "some", "just", "also", "only", "very",
    "here", "these", "those", "does", "done", "make", "made", "like", "much", "many", "such", "each", "every", "first", "last",
    "dans", "pour", "avec", "sont", "cette", "plus", "para", "como", "pero", "sobre", "esta", "este", "eine", "einer", "nicht", "sich", "auch", "wird",
)
private val YOUTUBE = Regex("(?:youtube(?:-nocookie)?\\.com/embed/|youtu\\.be/)([A-Za-z0-9_-]{11})")
private val VIMEO = Regex("player\\.vimeo\\.com/video/(\\d+)")
/** Logos, icons, avatars, tracking pixels and placeholders: not story photos. */
private val SMALL_IMAGE = Regex("(^|[/_.-])(logos?|icons?|avatars?|sprites?|pixel|spacer|placeholder|blank|1x1|badges?|emoji|ads|authors?|headshots?)([/_.-]|$)|doubleclick")
private val SRCSET_SPLIT = Regex(",\\s+")
/** A paragraph that is only a web address. */
private val BARE_URL = Regex("https?://\\S+")
/** "[soaring costs](https://…)" in a deck → "soaring costs"; a cut-off link at the end is dropped. */
private val MD_LINK = Regex("\\[([^\\]]{1,200})\\]\\(https?://[^)\\s]*\\)")
private val MD_LINK_CUT = Regex("\\[([^\\]]{0,200})(\\]\\(https?://\\S*)?$")
/** Byline, author, bio and logo blocks (their pictures are portraits and marks, not photos). */
private val SMALL_CONTAINER = Regex("(author|byline|avatar|contributor|headshot|\\bbio\\b|logo)", RegexOption.IGNORE_CASE)
/** A width spelled in the address: "?w=150", "&width=200", "/150x150/", "-150x100.jpg". */
private val LOW_RES_HINT = Regex("[?&](?:w|width|resize|size)=(\\d{2,4})|[/_-](\\d{2,4})x\\d{2,4}[./_-]")
