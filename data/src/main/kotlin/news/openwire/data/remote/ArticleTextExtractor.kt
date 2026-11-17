// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
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

enum class TextSource(val label: String) {
    PUBLISHER("Publisher"),
    WAYBACK("Wayback Machine"),
    ARCHIVE_TODAY("archive.today"),
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
    suspend fun extract(link: String): ArticleContent? = kotlinx.coroutines.coroutineScope {
        val started = System.currentTimeMillis()
        // Google News wrappers carry no article: read the publisher's page instead.
        val url = if (news.openwire.domain.engine.GoogleNewsUrls.isGoogleNewsUrl(link)) {
            resolveLink(link) ?: run {
                if (diagnostics) android.util.Log.i("EditroveReader", "${System.currentTimeMillis() - started}ms UNRESOLVED ${link.take(90)}")
                return@coroutineScope null
            }
        } else link
        val paywalled = runCatching { archiveFirst(url) }.getOrDefault(false)
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
            else renderPage(url)?.let { parse(it) }?.copy(via = TextSource.PUBLISHER, viaUrl = url)
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
        if (diagnostics) android.util.Log.i("EditroveReader", "${System.currentTimeMillis() - started}ms ${out?.via ?: "NONE"} ${out?.text?.length ?: 0}ch partial=${out?.partial} paywalled=$paywalled ${url.take(90)}")
        out
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
    internal fun aboutTitle(text: String, title: String): Boolean {
        fun words(t: String) = RX_WORDS.findAll(t.lowercase()).map { it.value }.filter { it.length >= 4 }.toSet()
        val head = words(title)
        if (head.isEmpty()) return false
        val shared = head.count { w -> w in words(text) || words(text).any { it.startsWith(w.take(5)) && w.length >= 5 } }
        return shared >= if (head.size < 4) 1 else 2
    }

    private suspend fun extractFrom(url: String): ArticleContent? = withContext(Dispatchers.IO) {
        try {
            get(url)?.let { parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(html: String): ArticleContent? {
        try {
            val doc = Jsoup.parse(html)
            val title = doc.selectFirst("h1")?.text().orEmpty().ifEmpty { doc.title() }
            // Embedded X posts become cards; their text isn't part of the story's paragraphs.
            val embeds = embeddedPosts(doc)
            val paragraphs = articleParagraphs(doc)
            val text = paragraphs.joinToString("\n\n")
            if (text.isBlank()) return null
            val imageUrl = doc.meta("og:image") ?: doc.selectFirst("article img")?.attr("src")
            return ArticleContent(
                title = title,
                text = text,
                imageUrl = imageUrl,
                description = (doc.meta("og:description") ?: doc.meta("description"))
                    ?.takeIf { it.length > 30 },
                author = (doc.meta("author") ?: doc.meta("article:author"))
                    ?.takeIf { it.isNotBlank() && !it.startsWith("http") && it.length < 80 },
                embeds = embeds,
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
        val inBest = best?.select("p").orEmpty()
        val source = if (inBest.size >= 3 && pText(best!!) >= total * 0.4) inBest else doc.select("p")
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
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("\\s+")
private val RX_2 = Regex("\"closest\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
private val RX_3 = Regex("/web/(\\d+)/")
private const val CHROME = "nav, header, footer, aside, form, script, style, noscript, figcaption, [role=navigation], [role=banner], " +
    "[role=contentinfo], [class*=related], [class*=newsletter], [class*=promo], [class*=share], [class*=social], " +
    "[class*=footer], [id*=footer], [class*=sidebar], [class*=breadcrumb], [class*=cookie], [class*=subscribe], [aria-hidden=true]"
/** A wire-style dateline: "WASHINGTON (DC News Now) —" or "LONDON (Reuters) -". */
private val DATELINE = Regex("\\b[A-Z][A-Z .,'’-]{2,40}\\s*\\([^)]{2,40}\\)\\s*[—–-]")
private val ARTICLE_BODY = Regex("\"articleBody\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
private val SENTENCE = Regex("(?<=[.!?])\\s+(?=[A-Z\"“])")
private val PARA_BREAK = Regex("\\n\\s*\\n|\\n")

private val RX_WORDS = Regex("\\p{L}+")
