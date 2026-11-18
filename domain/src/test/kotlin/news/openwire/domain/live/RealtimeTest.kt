// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeTest {
    private val norm: (String) -> String = { it.removePrefix("https://").removePrefix("www.").substringBefore('/') }
    private fun room(key: String) = Jetstream.NEWSROOMS.first { it.key == key }
    private val t0 = 1_794_953_715_000L

    private fun post(key: String, text: String, title: String? = null, minutes: Long = 0) = LivePost(
        did = room(key).did, timeUs = (t0 + minutes * 60_000) * 1000, text = text, createdAt = t0 + minutes * 60_000,
        linkUrl = title?.let { "https://www.${room(key).domain}/story/${it.hashCode()}" }, linkTitle = title, postUrl = "https://bsky.app/x",
    )

    @Test
    fun streamMessagesAreRead() {
        val msg = """{"did":"did:plc:jbvnehrrdqoulco4rf5gxg5r","time_us":1794953723853416,"kind":"commit","commit":{"rev":"r","operation":"create","collection":"app.bsky.feed.post","rkey":"3n2k2xli5zn2h",
          "record":{"${'$'}type":"app.bsky.feed.post","createdAt":"2026-11-17T22:15:23Z","text":"New York Fed's Williams says rates are in a good place",
          "embed":{"${'$'}type":"app.bsky.embed.external","external":{"uri":"https://www.reuters.com/markets/x","title":"Fed's Williams says policy well positioned","description":"d"}}}}}"""
        val p = Jetstream.parse(msg)!!
        assertEquals("Fed's Williams says policy well positioned", p.linkTitle)
        assertEquals("https://bsky.app/profile/did:plc:jbvnehrrdqoulco4rf5gxg5r/post/3n2k2xli5zn2h", p.postUrl)
        assertNull(Jetstream.parse("""{"kind":"identity","did":"x","time_us":1}"""))
    }

    @Test
    fun labelledPostsAlertAtOnceAndOnlyOnce() {
        val d = RealtimeBreaking(norm)
        val a = d.onPost(post("ap", "BREAKING: Magnitude 7.1 earthquake strikes off the coast of Japan, tsunami warning issued"), room("ap"))
        assertNotNull(a)
        assertEquals("Magnitude 7.1 earthquake strikes off the coast of Japan, tsunami warning issued", a!!.article.title)
        assertNull(d.onPost(post("ap", "BREAKING: Magnitude 7.1 earthquake strikes off the coast of Japan, tsunami warning issued", minutes = 1), room("ap")))
        assertNotNull(RealtimeBreaking(norm).onPost(post("lemonde", "Alerte : un séisme de magnitude 7,1 frappe le Japon, alerte au tsunami"), room("lemonde")))
    }

    @Test
    fun ordinaryPostsAlertOnlyWhenThreeNewsroomsCarryTheStory() {
        val d = RealtimeBreaking(norm)
        assertNull(d.onPost(post("reuters", "x", "Federal Reserve cuts interest rates by half a point in surprise move"), room("reuters")))
        assertNull(d.onPost(post("nyt", "x", "Fed cuts interest rates by half a point in surprise move", minutes = 2), room("nyt")))
        val alert = d.onPost(post("cnn", "x", "Federal Reserve cuts rates by half a point in a surprise move", minutes = 4), room("cnn"))
        assertNotNull(alert)
        assertEquals(3, alert!!.outlets)
        // A fourth newsroom on the same story doesn't alert again.
        assertNull(d.onPost(post("wsj", "x", "Fed cuts interest rates by half point in surprise move", minutes = 5), room("wsj")))
        // An unrelated story from one newsroom stays quiet.
        assertNull(d.onPost(post("npr", "x", "A new exhibit on jazz history opens in New Orleans this weekend", minutes = 6), room("npr")))
    }

    @Test
    fun everyNewsroomIsWatchedChosenOnesFirst() {
        val keys = Jetstream.watched(setOf("cnn"), "fr").map { it.key }
        assertTrue(keys.containsAll(listOf("ap", "reuters", "afp", "cnn", "france24", "lemonde", "franceinfo", "spiegel")))
        assertTrue(keys.size == Jetstream.NEWSROOMS.size)
        assertTrue(keys.indexOf("cnn") < keys.indexOf("spiegel"))
    }
}
