// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.opinion

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.Article

/** Columns and op-eds, or analysis (explainers, policy and research writing). */
enum class OpinionKind { OPINION, ANALYSIS }

data class OpinionFeed(val name: String, val url: String, val language: String, val kind: OpinionKind, val domain: String)

/** A newsroom or commentator on Bluesky, offered to follow. */
data class Voice(val handle: String, val name: String, val language: String, val newsroom: Boolean)

/** A public post from Bluesky or Mastodon. */
data class SocialPost(
    val id: String,
    val network: String,
    val authorHandle: String,
    val authorName: String,
    val avatar: String?,
    val text: String,
    val createdAt: Long?,
    val url: String,
    val linkUrl: String? = null,
    val linkTitle: String? = null,
    val image: String? = null,
    val likes: Int = 0,
    val reposts: Int = 0,
)

object OpinionCatalog {
    private fun site(path: String, lang: String): String {
        val c = TopicRegistry.COUNTRIES.first { it.language == lang }
        return TopicRegistry.googleNewsSearchUrl("site:$path when:3d", c)
    }

    /** Opinion and analysis sections, checked to be live (most name their writers). */
    val FEEDS: List<OpinionFeed> = listOf(
        // English
        OpinionFeed("New York Times Opinion", "https://rss.nytimes.com/services/xml/rss/nyt/Opinion.xml", "en", OpinionKind.OPINION, "nytimes.com"),
        OpinionFeed("Guardian Opinion", "https://www.theguardian.com/commentisfree/rss", "en", OpinionKind.OPINION, "theguardian.com"),
        OpinionFeed("Washington Post Opinions", "https://feeds.washingtonpost.com/rss/opinions", "en", OpinionKind.OPINION, "washingtonpost.com"),
        OpinionFeed("WSJ Opinion", "https://feeds.a.dj.com/rss/RSSOpinion.xml", "en", OpinionKind.OPINION, "wsj.com"),
        OpinionFeed("LA Times Opinion", "https://www.latimes.com/opinion/rss2.0.xml", "en", OpinionKind.OPINION, "latimes.com"),
        OpinionFeed("The Atlantic · Ideas", "https://www.theatlantic.com/feed/channel/ideas/", "en", OpinionKind.OPINION, "theatlantic.com"),
        OpinionFeed("Al Jazeera Opinion", site("aljazeera.com/opinions", "en"), "en", OpinionKind.OPINION, "aljazeera.com"),
        OpinionFeed("Politico Magazine", "https://rss.politico.com/magazine.xml", "en", OpinionKind.OPINION, "politico.com"),
        OpinionFeed("Project Syndicate", "https://www.project-syndicate.org/rss", "en", OpinionKind.ANALYSIS, "project-syndicate.org"),
        OpinionFeed("The Conversation", "https://theconversation.com/global/articles.atom", "en", OpinionKind.ANALYSIS, "theconversation.com"),
        OpinionFeed("Foreign Policy", "https://foreignpolicy.com/feed/", "en", OpinionKind.ANALYSIS, "foreignpolicy.com"),
        OpinionFeed("Foreign Affairs", "https://www.foreignaffairs.com/rss.xml", "en", OpinionKind.ANALYSIS, "foreignaffairs.com"),
        OpinionFeed("The Economist · Leaders", "https://www.economist.com/leaders/rss.xml", "en", OpinionKind.ANALYSIS, "economist.com"),
        // Arabic
        OpinionFeed("Asharq Al-Awsat · الرأي", "https://aawsat.com/feed/opinion", "ar", OpinionKind.OPINION, "aawsat.com"),
        OpinionFeed("Shorouk · أعمدة", "https://www.shorouknews.com/columns/rss", "ar", OpinionKind.OPINION, "shorouknews.com"),
        OpinionFeed("Al Jazeera · آراء", site("aljazeera.net/opinions", "ar"), "ar", OpinionKind.OPINION, "aljazeera.net"),
        OpinionFeed("Al-Araby Al-Jadeed · آراء", site("alaraby.co.uk/opinion", "ar"), "ar", OpinionKind.OPINION, "alaraby.co.uk"),
        // French
        OpinionFeed("Le Monde · Idées", "https://www.lemonde.fr/idees/rss_full.xml", "fr", OpinionKind.OPINION, "lemonde.fr"),
        OpinionFeed("Le Figaro Vox", "https://www.lefigaro.fr/rss/figaro_vox.xml", "fr", OpinionKind.OPINION, "lefigaro.fr"),
        OpinionFeed("Libération · Idées", "https://www.liberation.fr/arc/outboundfeeds/rss-all/category/idees-et-debats/?outputType=xml", "fr", OpinionKind.OPINION, "liberation.fr"),
        OpinionFeed("The Conversation France", "https://theconversation.com/fr/articles.atom", "fr", OpinionKind.ANALYSIS, "theconversation.com"),
        // Spanish
        OpinionFeed("El País · Opinión", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/opinion/portada", "es", OpinionKind.OPINION, "elpais.com"),
        OpinionFeed("El Mundo · Opinión", "https://e00-elmundo.uecdn.es/elmundo/rss/opinion.xml", "es", OpinionKind.OPINION, "elmundo.es"),
        OpinionFeed("Infobae · Opinión", "https://www.infobae.com/arc/outboundfeeds/rss/category/opinion/", "es", OpinionKind.OPINION, "infobae.com"),
        OpinionFeed("The Conversation España", "https://theconversation.com/es/articles.atom", "es", OpinionKind.ANALYSIS, "theconversation.com"),
        // German
        OpinionFeed("Süddeutsche · Meinung", "https://rss.sueddeutsche.de/rss/Meinung", "de", OpinionKind.OPINION, "sueddeutsche.de"),
        OpinionFeed("Tagesspiegel · Meinung", "https://www.tagesspiegel.de/contentexport/feed/meinung", "de", OpinionKind.OPINION, "tagesspiegel.de"),
        OpinionFeed("Die Zeit · Politik", "https://newsfeed.zeit.de/politik/index", "de", OpinionKind.ANALYSIS, "zeit.de"),
    )

    /** Feeds to read for [appLanguage]: its own, plus English (the widest range of writers). */
    fun feedsFor(appLanguage: String): List<OpinionFeed> =
        FEEDS.filter { it.language == appLanguage } + if (appLanguage == "en") emptyList() else FEEDS.filter { it.language == "en" }

    /** Bluesky accounts offered to follow (checked to exist). */
    val VOICES: List<Voice> = listOf(
        Voice("nytopinion.nytimes.com", "New York Times Opinion", "en", true),
        Voice("theatlantic.com", "The Atlantic", "en", true),
        Voice("theguardian.com", "The Guardian", "en", true),
        Voice("washingtonpost.com", "The Washington Post", "en", true),
        Voice("economist.com", "The Economist", "en", true),
        Voice("foreignaffairs.com", "Foreign Affairs", "en", true),
        Voice("foreignpolicy.com", "Foreign Policy", "en", true),
        Voice("propublica.org", "ProPublica", "en", true),
        Voice("aljazeera.com", "Al Jazeera English", "en", true),
        Voice("pkrugman.bsky.social", "Paul Krugman", "en", false),
        Voice("rbreich.bsky.social", "Robert Reich", "en", false),
        Voice("mehdirhasan.bsky.social", "Mehdi Hasan", "en", false),
        Voice("lemonde.fr", "Le Monde", "fr", true),
        Voice("france24.com", "France 24", "fr", true),
        Voice("elpais.com", "EL PAÍS", "es", true),
        Voice("spiegel.de", "DER SPIEGEL", "de", true),
        Voice("zeit.de", "DIE ZEIT", "de", true),
    )

    fun voicesFor(appLanguage: String): List<Voice> =
        VOICES.sortedBy { if (it.language == appLanguage) 0 else if (it.language == "en") 1 else 2 }

    /** A Google News search for opinion writing on [topic], in [lang]. */
    fun topicSearch(topic: String, lang: String): String {
        val c = TopicRegistry.COUNTRIES.firstOrNull { it.language == lang } ?: TopicRegistry.COUNTRIES.first()
        val word = when (lang) { "ar" -> "رأي OR مقال"; "fr" -> "tribune OR opinion OR éditorial"; "es" -> "opinión OR tribuna OR columna"; "de" -> "Kommentar OR Meinung OR Essay"; else -> "opinion OR op-ed OR analysis" }
        return TopicRegistry.googleNewsSearchUrl("\"${topic.trim()}\" ($word) when:3d", c)
    }

    /** Stories by any of [authors] (matched loosely: case and spacing). */
    fun byAuthors(articles: List<Article>, authors: Set<String>): List<Article> {
        if (authors.isEmpty()) return emptyList()
        val keys = authors.map(::authorKey).toSet()
        return articles.filter { a -> a.author?.let { authorKey(it) in keys } == true }
    }

    fun authorKey(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/** Topics most of today's coverage is about, for tabs ("Iran", "Gaza", "Fed"…). */
object Trending {
    private val STOP = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "for", "to", "with", "after", "as", "at", "by", "from", "is", "are", "be", "how", "why",
        "what", "who", "new", "says", "say", "over", "into", "amid", "its", "his", "her", "their", "this", "that", "will", "may", "could", "more",
        "live", "update", "updates", "news", "video", "watch", "opinion", "analysis", "breaking", "report", "exclusive", "first", "about",
        "le", "la", "les", "des", "du", "de", "un", "une", "et", "en", "pour", "sur", "dans", "el", "los", "las", "del", "y", "con", "por", "para",
        "der", "die", "das", "und", "mit", "für", "von", "im", "auf", "den", "dem", "ein", "eine",
        "في", "من", "على", "إلى", "عن", "مع", "بعد", "قبل", "هذا", "هذه", "التي", "الذي", "بين", "حول", "خلال", "منذ", "عند", "أن", "إن", "كان",
        "قد", "لا", "ما", "كل", "غير", "بعض", "أو", "ثم", "حتى", "لم", "لن", "يوم", "اليوم", "عاجل", "فيديو", "مباشر", "تقرير",
    )

    /** Common headline verbs and connectors in Arabic news ("reveals", "warns", "announces"…). */
    private val AR_VERBS = setOf(
        "تكشف", "يكشف", "تعلن", "يعلن", "تحذر", "يحذر", "ترد", "يرد", "تبحث", "يبحث", "تؤكد", "يؤكد", "تنفي", "ينفي", "يقول", "تقول", "قال", "قالت",
        "بشأن", "أمام", "عقب", "وسط", "أكثر", "أول", "جديد", "جديدة", "رئيس", "مصادر", "مسؤول", "مقتل", "إطلاق", "بسبب", "ضد", "دون", "لدى",
        "يستعد", "تستعد", "يطالب", "تطالب", "يدعو", "تدعو", "يواصل", "تواصل", "يتوقع", "تتوقع", "عاما", "عام", "مليون", "مليار",
    )

    private val PARTICLES = setOf("le", "la", "de", "al", "el", "van", "von", "der", "di", "da", "bin", "ben", "abu", "del")
    private val JOINERS = setOf("of", "de", "du", "del", "der", "al", "and", "&")

    private val WORD = Regex("[\\p{L}][\\p{L}\\p{N}'’.-]*")

    /**
     * The phrases most stories mention: proper-noun runs in Latin scripts ("White House",
     * "Le Pen"), frequent words in Arabic. Each story counts once per phrase.
     */
    fun topics(articles: List<Article>, max: Int = 8, minStories: Int = 3): List<String> {
        val counts = HashMap<String, MutableSet<String>>()
        val display = HashMap<String, String>()
        for (a in articles) {
            val title = a.title.substringBeforeLast(" - ").substringBeforeLast(" | ")
            val words = WORD.findAll(title).map { it.value.trimEnd('.', '-', '’', '\'') }.toList()
            val phrases = mutableSetOf<String>()
            val arabic = words.any { w -> w.first() in '؀'..'ۿ' }
            if (arabic) {
                // Words and two-word phrases ("الذكاء الاصطناعي"); a phrase replaces its words
                // when they mostly appear together (decided below, across all stories).
                val content = words.map { it.trim() }.filter { it.length >= 3 && it !in STOP && it !in AR_VERBS && !it.all(Char::isDigit) }
                content.filter { it.length >= 4 }.forEach { phrases += it }
                content.zipWithNext().forEach { (x, y) -> phrases += "$x $y" }
            } else {
                var run = mutableListOf<String>()
                fun flush() { if (run.isNotEmpty()) { phrases += run.joinToString(" "); run = mutableListOf() } }
                fun proper(i: Int): Boolean {
                    val w = words.getOrNull(i) ?: return false
                    return w.first().isUpperCase() && w.lowercase() !in STOP && (i > 0 || w.length > 3) && w.length >= 2
                }
                words.forEachIndexed { i, w ->
                    val lower = w.lowercase()
                    when {
                        // "Le Pen", "Al Sisi", "Van Dyke": a name particle starts a run.
                        w.first().isUpperCase() && lower in PARTICLES && proper(i + 1) -> { flush(); run += w }
                        proper(i) && run.size < 4 -> run += w
                        // "Strait of Hormuz", "Bank of England": a joining word inside a run.
                        run.isNotEmpty() && lower in JOINERS && proper(i + 1) -> run += w
                        else -> { flush(); if (proper(i)) run += w }
                    }
                }
                flush()
            }
            for (p in phrases) {
                val key = p.lowercase()
                if (key.length < 3 || key in STOP) continue
                counts.getOrPut(key) { mutableSetOf() } += a.id
                display.putIfAbsent(key, p)
            }
        }
        // An Arabic two-word phrase wins over its words when it carries most of their stories.
        val pairs = counts.keys.filter { k -> ' ' in k && k.first() in '\u0600'..'\u06FF' }
        for (pair in pairs) {
            val n = counts[pair]?.size ?: continue
            if (n < minStories) { counts.remove(pair); continue }
            pair.split(' ').forEach { w -> val single = counts[w]; if (single != null && n * 10 >= single.size * 6) counts.remove(w) }
        }
        val ranked = counts.filter { it.value.size >= minStories }.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableSet<String>>> { it.value.size }.thenByDescending { it.key.length })
            .map { display[it.key]!! }
        // Drop a phrase already covered by a longer one ("House" under "White House").
        val out = mutableListOf<String>()
        for (p in ranked) {
            if (out.any { it.contains(p, ignoreCase = true) || p.contains(it, ignoreCase = true) }) continue
            out += p
            if (out.size == max) break
        }
        return out
    }
}

/** Bluesky's public, keyless API (posts are public; nothing is sent about the reader). */
object Bluesky {
    const val FEED = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed"
    const val SEARCH = "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts"

    fun authorFeedUrl(handle: String, limit: Int = 12) = "$FEED?actor=${enc(handle)}&limit=$limit&filter=posts_no_replies"

    fun searchUrl(q: String, lang: String?, limit: Int = 25) =
        "$SEARCH?q=${enc(q)}&limit=$limit&sort=top" + (lang?.let { "&lang=$it" } ?: "")

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private val json = Json { ignoreUnknownKeys = true }

    /** getAuthorFeed ({"feed":[{"post":…}]}) or searchPosts ({"posts":[…]}). */
    fun parse(body: String): List<SocialPost> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val posts = (root["feed"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { o -> if (o["reason"] != null) null else o["post"] } }
            ?: (root["posts"] as? JsonArray)?.toList() ?: return emptyList()
        return posts.mapNotNull { post(it) }
    }

    private fun JsonElement?.str(vararg path: String): String? {
        var e: JsonElement? = this
        for (k in path) e = (e as? JsonObject)?.get(k)
        return (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
    }

    private fun post(e: JsonElement): SocialPost? {
        val o = e as? JsonObject ?: return null
        val uri = o.str("uri") ?: return null
        val handle = o.str("author", "handle") ?: return null
        val text = o.str("record", "text").orEmpty()
        val embed = o["embed"] as? JsonObject
        val external = embed?.get("external") as? JsonObject
            ?: (embed?.get("media") as? JsonObject)?.get("external") as? JsonObject
        val image = (embed?.get("images") as? JsonArray)?.firstOrNull()?.str("thumb") ?: external?.str("thumb")
        if (text.isBlank() && external == null) return null
        return SocialPost(
            id = uri,
            network = "Bluesky",
            authorHandle = handle,
            authorName = o.str("author", "displayName")?.takeIf { it.isNotBlank() } ?: handle,
            avatar = o.str("author", "avatar"),
            text = text,
            createdAt = (o.str("record", "createdAt") ?: o.str("indexedAt"))?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            url = "https://bsky.app/profile/$handle/post/${uri.substringAfterLast('/')}",
            linkUrl = external?.str("uri"),
            linkTitle = external?.str("title")?.takeIf { it.isNotBlank() },
            image = image,
            likes = o["likeCount"]?.jsonPrimitive?.intOrNull ?: 0,
            reposts = o["repostCount"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }
}

/** Mastodon hashtag timelines (public, keyless). */
object Mastodon {
    fun tagUrl(tag: String, instance: String = "mastodon.social", limit: Int = 20) =
        "https://$instance/api/v1/timelines/tag/${java.net.URLEncoder.encode(tag.replace(" ", ""), "UTF-8")}?limit=$limit"

    private val json = Json { ignoreUnknownKeys = true }
    private val TAGS = Regex("<[^>]+>")

    fun parse(body: String): List<SocialPost> {
        val arr = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            val acct = (o["account"] as? JsonObject) ?: return@mapNotNull null
            fun a(k: String) = acct[k]?.jsonPrimitive?.contentOrNull
            val html = s("content").orEmpty()
            val text = html.replace("<br>", "\n").replace("</p><p>", "\n\n").replace(TAGS, "")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()
            if (text.isBlank()) return@mapNotNull null
            val card = o["card"] as? JsonObject
            SocialPost(
                id = s("uri") ?: return@mapNotNull null,
                network = "Mastodon",
                authorHandle = a("acct") ?: return@mapNotNull null,
                authorName = a("display_name")?.takeIf { it.isNotBlank() } ?: a("username").orEmpty(),
                avatar = a("avatar"),
                text = text,
                createdAt = s("created_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                url = s("url") ?: s("uri")!!,
                linkUrl = card?.get("url")?.jsonPrimitive?.contentOrNull,
                linkTitle = card?.get("title")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                image = card?.get("image")?.jsonPrimitive?.contentOrNull,
                likes = o["favourites_count"]?.jsonPrimitive?.intOrNull ?: 0,
                reposts = o["reblogs_count"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }
}

/**
 * Posts from X, read through X's official oEmbed endpoint (one post at a time, with
 * do-not-track) and shown in Editrove's own card design: no X script runs in the app.
 */
object XPosts {
    val STATUS = Regex("https?://(?:www\\.|mobile\\.)?(?:twitter|x)\\.com/([A-Za-z0-9_]{1,15})/status(?:es)?/(\\d{5,25})")

    /** The canonical x.com address of a post link, or null when it isn't one. */
    fun canonical(url: String): String? = STATUS.find(url)?.let { "https://x.com/${it.groupValues[1]}/status/${it.groupValues[2]}" }

    fun oembedUrl(post: String) =
        "https://publish.twitter.com/oembed?url=${java.net.URLEncoder.encode(post, "UTF-8")}&omit_script=true&dnt=true&hide_thread=true"

    private val json = Json { ignoreUnknownKeys = true }
    private val P = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val DATE = Regex("<a[^>]*>([^<]*)</a>\\s*</blockquote>")
    private val TAG = Regex("<[^>]+>")

    private fun text(html: String) = html.replace(Regex("<br\\s*/?>"), "\n").replace(TAG, "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&mdash;", "—").replace("&nbsp;", " ").trim()

    fun parse(body: String, url: String): SocialPost? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val html = o["html"]?.jsonPrimitive?.contentOrNull ?: return null
        val handle = o["author_url"]?.jsonPrimitive?.contentOrNull?.substringAfterLast('/') ?: STATUS.find(url)?.groupValues?.get(1) ?: return null
        val body0 = P.find(html)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotBlank() } ?: return null
        // A trailing pic.x.com / t.co link is X's attachment marker, not words.
        val words = body0.replace(Regex("\\s*(pic\\.(twitter|x)\\.com/\\S+|https://t\\.co/\\S+)\\s*$"), "").trim()
        return SocialPost(
            id = url,
            network = "X",
            authorHandle = handle,
            authorName = o["author_name"]?.jsonPrimitive?.contentOrNull ?: handle,
            avatar = null,
            text = words.ifBlank { body0 },
            createdAt = null,
            url = url,
            linkTitle = DATE.find(html)?.groupValues?.get(1)?.trim(), // shown as the post's date
        )
    }
}
