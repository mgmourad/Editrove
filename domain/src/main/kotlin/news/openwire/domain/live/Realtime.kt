// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.live

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import news.openwire.domain.engine.StoryIdentity
import news.openwire.domain.model.Article

/** A newsroom's Bluesky account, watched for real-time breaking news. */
data class Newsroom(val key: String, val name: String, val did: String, val domain: String, val language: String)

/** A newsroom post as it arrives on Bluesky's public stream. */
data class LivePost(
    val did: String,
    val timeUs: Long,
    val text: String,
    val createdAt: Long,
    val linkUrl: String?,
    val linkTitle: String?,
    val postUrl: String,
)

/**
 * Real-time alerts without a server of our own: the phone keeps one connection to
 * Bluesky's public Jetstream, asking only for posts by the newsrooms being watched.
 */
object Jetstream {
    val SERVERS = listOf("wss://jetstream2.us-east.bsky.network", "wss://jetstream1.us-east.bsky.network", "wss://jetstream1.us-west.bsky.network")

    /** Newsrooms' account ids (DIDs are permanent, unlike handles). [key]s match BreakingNews outlets. */
    val NEWSROOMS: List<Newsroom> = listOf(
        Newsroom("ap", "Associated Press", "did:plc:a67zdrt4nl2tv2qojpngogbq", "apnews.com", "en"),
        Newsroom("reuters", "Reuters", "did:plc:jbvnehrrdqoulco4rf5gxg5r", "reuters.com", "en"),
        Newsroom("cnn", "CNN", "did:plc:dzezcmpb3fhcpns4n4xm4ur5", "cnn.com", "en"),
        Newsroom("nbc", "NBC News", "did:plc:wmho6q2uiyktkam3jsvrms3s", "nbcnews.com", "en"),
        Newsroom("cbs", "CBS News", "did:plc:3bxtpdpr73tf7tldv5q4oyqc", "cbsnews.com", "en"),
        Newsroom("npr", "NPR", "did:plc:ln72v57ivz2g46uqf4xxqiuh", "npr.org", "en"),
        Newsroom("nyt", "The New York Times", "did:plc:eclio37ymobqex2ncko63h4r", "nytimes.com", "en"),
        Newsroom("wapo", "The Washington Post", "did:plc:k5nskatzhyxersjilvtnz4lh", "washingtonpost.com", "en"),
        Newsroom("wsj", "The Wall Street Journal", "did:plc:i3fhjvvkbmirhyu4aeihhrnv", "wsj.com", "en"),
        Newsroom("bloomberg", "Bloomberg", "did:plc:uewxgchsjy4kmtu7dcxa77us", "bloomberg.com", "en"),
        Newsroom("aljazeera", "Al Jazeera", "did:plc:2lofqead276vtc5647ye7sl2", "aljazeera.com", "en"),
        Newsroom("guardian", "The Guardian", "did:plc:vovinwhtulbsx4mwfw26r5ni", "theguardian.com", "en"),
        Newsroom("politico", "Politico", "did:plc:yf6hctt2ug3qyfty4in64yob", "politico.com", "en"),
        Newsroom("afp", "AFP", "did:plc:ffccycfh6c6pmxwkhvq5clkv", "afp.com", "en"),
        Newsroom("axios", "Axios", "did:plc:f6avy7jkujdhusski5n64joj", "axios.com", "en"),
        Newsroom("france24", "France 24", "did:plc:wkreszch2k4mumsrlkeaflgn", "france24.com", "fr"),
        Newsroom("lemonde", "Le Monde", "did:plc:qqxqxgdu5z3he2piqfbfaku4", "lemonde.fr", "fr"),
        Newsroom("franceinfo", "franceinfo", "did:plc:vb7d3zz6khc7zqcg7743eqiq", "franceinfo.fr", "fr"),
        Newsroom("elpais", "EL PAÍS", "did:plc:u6mkbcgviwlbhuwqirmhcgu3", "elpais.com", "es"),
        Newsroom("spiegel", "DER SPIEGEL", "did:plc:6xofcnvvojjnmggqx43zghwh", "spiegel.de", "de"),
    )

    /**
     * Who to watch: the breaking-news outlets the reader picked, the wire services (they
     * break most stories first), and the newsrooms writing in Editrove's language.
     */
    fun watched(outletKeys: Set<String>, appLanguage: String): List<Newsroom> =
        NEWSROOMS.filter { it.key in outletKeys || it.key in setOf("ap", "reuters", "afp") || (appLanguage != "en" && it.language == appLanguage) }

    fun url(server: String, rooms: List<Newsroom>, cursorUs: Long? = null): String =
        "$server/subscribe?wantedCollections=app.bsky.feed.post" +
            rooms.joinToString("") { "&wantedDids=${it.did}" } +
            (cursorUs?.let { "&cursor=$it" } ?: "")

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonElement?.str(vararg path: String): String? {
        var e: JsonElement? = this
        for (k in path) e = (e as? JsonObject)?.get(k)
        return (e as? JsonPrimitive)?.contentOrNull
    }

    /** A new post (not a reply), or null for anything else on the stream. */
    fun parse(message: String): LivePost? {
        val o = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null
        if (o.str("kind") != "commit" || o.str("commit", "operation") != "create") return null
        if (o.str("commit", "collection") != "app.bsky.feed.post") return null
        val record = (o["commit"] as? JsonObject)?.get("record") as? JsonObject ?: return null
        if (record["reply"] != null) return null
        val did = o.str("did") ?: return null
        val time = (o["time_us"] as? JsonPrimitive)?.longOrNull ?: return null
        val external = ((record["embed"] as? JsonObject)?.let { e -> e["external"] ?: (e["media"] as? JsonObject)?.get("external") }) as? JsonObject
        val created = record.str("createdAt")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: (time / 1000)
        return LivePost(
            did = did,
            timeUs = time,
            text = record.str("text").orEmpty(),
            createdAt = created,
            linkUrl = external.str("uri"),
            linkTitle = external.str("title")?.takeIf { it.isNotBlank() },
            postUrl = "https://bsky.app/profile/$did/post/${o.str("commit", "rkey")}",
        )
    }
}

/**
 * Decides which live posts are breaking news: a newsroom labelling it so ("BREAKING",
 * "JUST IN", "عاجل", "Eilmeldung"…), or three or more newsrooms posting the same story
 * within half an hour. Each story alerts once.
 */
class RealtimeBreaking(private val normalizeDomain: (String) -> String, private val minOutlets: Int = 3) {
    private val recent = ArrayDeque<Pair<Newsroom, Article>>()
    private val alerted = LinkedHashSet<String>()

    data class Alert(val article: Article, val newsroom: Newsroom, val outlets: Int, val labelled: Boolean)

    fun onPost(post: LivePost, room: Newsroom): Alert? {
        val title = headline(post) ?: return null
        val a = Article(
            id = "rt-" + post.timeUs, title = title, source = room.name, domain = room.domain,
            originalUrl = post.linkUrl ?: post.postUrl, publishedAt = post.createdAt,
        )
        // Keep half an hour of posts.
        val cutoff = post.createdAt - WINDOW_MS
        while (recent.isNotEmpty() && (recent.first().second.publishedAt ?: 0L) < cutoff) recent.removeFirst()
        recent.addLast(room to a)
        if (recent.size > 400) recent.removeFirst()

        val labelled = MARKER.containsMatchIn(post.text.take(60)) || MARKER.containsMatchIn(title.take(40))
        val same = recent.filter { (_, b) -> b === a || StoryIdentity.sameStory(a, b, normalizeDomain) }
        val outlets = same.map { it.first.key }.toSet().size
        if (!labelled && outlets < minOutlets) return null
        val key = storyKey(a, same.map { it.second })
        if (!alerted.add(key)) return null
        if (alerted.size > 300) alerted.remove(alerted.first())
        // The labelled post (or the first one) is the alert's headline.
        val lead = if (labelled) a else same.minByOrNull { it.second.publishedAt ?: 0L }?.second ?: a
        val leadRoom = same.firstOrNull { it.second === lead }?.first ?: room
        return Alert(lead.copy(title = clean(lead.title)), leadRoom, outlets, labelled)
    }

    private fun storyKey(a: Article, cluster: List<Article>): String =
        "rt:" + cluster.minOf { it.id }.ifEmpty { a.id }

    private fun headline(p: LivePost): String? {
        val t = p.linkTitle?.takeIf { it.length > 15 } ?: p.text.lineSequence().map { it.trim() }.firstOrNull { it.length > 15 } ?: return null
        return t.take(220)
    }

    companion object {
        const val WINDOW_MS = 30 * 60_000L
        private val MARKER = Regex(
            "^\\s*(?:[🚨🔴⚡️]\\s*)*(BREAKING( NEWS)?|JUST IN|URGENT|DEVELOPING|عاجل|خبر عاجل|DERNI[ÈE]RE MINUTE|ALERTE|[ÚU]LTIMA HORA|EILMELDUNG)\\b",
            RegexOption.IGNORE_CASE,
        )

        /** "BREAKING: Quake hits…" → "Quake hits…". */
        fun clean(title: String): String = title.replace(MARKER, "").trimStart(':', ' ', '-', '—', '|').trim()
    }
}
