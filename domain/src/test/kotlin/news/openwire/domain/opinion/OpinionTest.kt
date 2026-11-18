// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.opinion

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpinionTest {
    private fun a(id: Int, title: String, author: String? = null) = Article(id = "a$id", title = title, author = author)

    @Test
    fun trendingFindsWhatManyStoriesAreAbout() {
        val news = listOf(
            a(1, "Iran warns Washington over strikes near the Strait of Hormuz - Reuters"),
            a(2, "Oil jumps as Iran tensions rise in the Strait of Hormuz"),
            a(3, "What Iran wants from talks with Oman"),
            a(4, "Le Pen unveils pension reform plan"),
            a(5, "Le Pen pension plan draws fire"),
            a(6, "Analysts weigh Le Pen's chances"),
            a(7, "The weather today"),
        )
        val t = Trending.topics(news, minStories = 2)
        assertTrue(t.toString(), t.any { it == "Iran" })
        assertTrue(t.toString(), t.any { it.startsWith("Le Pen") })
        assertTrue(t.toString(), t.any { it.contains("Hormuz") })
        assertFalse(t.any { it.equals("The", true) || it.equals("What", true) })
    }

    @Test
    fun arabicHeadlinesTrendByWord() {
        val news = listOf(a(1, "إيران تحذر واشنطن من التصعيد"), a(2, "واشنطن ترد على إيران"), a(3, "إيران وسلطنة عمان تبحثان التطورات"))
        assertTrue(Trending.topics(news, minStories = 2).contains("إيران"))
    }

    @Test
    fun arabicPhrasesStayTogether() {
        val news = listOf(
            a(1, "الذكاء الاصطناعي يغير سوق العمل"), a(2, "شركات تكشف خططها في الذكاء الاصطناعي"),
            a(3, "قمة حول الذكاء الاصطناعي في القاهرة"), a(4, "تكشف الأرقام تراجع التضخم"), a(5, "تكشف دراسة أسباب السمنة"),
        )
        val t = Trending.topics(news, minStories = 3)
        assertTrue(t.toString(), t.contains("الذكاء الاصطناعي"))
        assertFalse(t.toString(), t.contains("الذكاء") || t.contains("الاصطناعي") || t.contains("تكشف"))
    }

    @Test
    fun followedWritersMatchLoosely() {
        val list = listOf(a(1, "x", "Paul Krugman"), a(2, "y", "PAUL  KRUGMAN"), a(3, "z", "Someone Else"))
        assertEquals(2, OpinionCatalog.byAuthors(list, setOf("paul krugman")).size)
    }

    @Test
    fun blueskyPostsAndLinksAreRead() {
        val body = """{"feed":[{"post":{"uri":"at://did:plc:x/app.bsky.feed.post/3abc","author":{"handle":"nytopinion.nytimes.com","displayName":"NYT Opinion","avatar":"https://a/av.jpg"},
          "record":{"text":"Why the measles comeback matters","createdAt":"2026-11-17T10:00:00Z"},
          "embed":{"${'$'}type":"app.bsky.embed.external#view","external":{"uri":"https://www.nytimes.com/2026/11/17/opinion/measles.html","title":"The Measles Comeback","thumb":"https://t/x.jpg"}},
          "likeCount":12,"repostCount":3}},
          {"post":{"uri":"at://x/app.bsky.feed.post/r"},"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost"}}]}"""
        val posts = Bluesky.parse(body)
        assertEquals(1, posts.size)
        with(posts[0]) {
            assertEquals("https://bsky.app/profile/nytopinion.nytimes.com/post/3abc", url)
            assertEquals("The Measles Comeback", linkTitle)
            assertEquals(12, likes)
        }
    }

    @Test
    fun mastodonHtmlBecomesText() {
        val body = """[{"uri":"https://m/1","url":"https://m/@a/1","content":"<p>Hello <a href=\"x\">#iran</a> &amp; world</p>","created_at":"2026-11-17T10:00:00Z",
          "account":{"acct":"a@m.social","display_name":"A","avatar":"https://m/a.png"},"favourites_count":2,"reblogs_count":1}]"""
        assertEquals("Hello #iran & world", Mastodon.parse(body).single().text)
    }

    @Test
    fun everyLanguageHasOpinionFeeds() {
        listOf("en", "ar", "fr", "es", "de").forEach { l -> assertTrue(l, OpinionCatalog.FEEDS.count { it.language == l } >= 3) }
    }

    @Test
    fun xEmbedDataGivesPhotosAndVideos() {
        val body = """{"__typename":"Tweet","favorite_count":12,"created_at":"2026-11-17T20:50:14.000Z","id_str":"1858000000000000000",
            "text":"Flooding on the corniche tonight &amp; more rain due https://t.co/abc https://t.co/def",
            "user":{"name":"Cairo Desk","screen_name":"cairodesk","profile_image_url_https":"https://pbs.twimg.com/profile_images/1/a_normal.jpg"},
            "mediaDetails":[
              {"type":"photo","media_url_https":"https://pbs.twimg.com/media/A.jpg","original_info":{"width":1200,"height":800}},
              {"type":"video","media_url_https":"https://pbs.twimg.com/ext_tw_video_thumb/B.jpg","original_info":{"width":1280,"height":720},
               "video_info":{"variants":[{"content_type":"application/x-mpegURL","url":"https://video.twimg.com/b.m3u8"},
                 {"bitrate":256000,"content_type":"video/mp4","url":"https://video.twimg.com/b_320.mp4"},
                 {"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/b_1280.mp4"},
                 {"bitrate":10368000,"content_type":"video/mp4","url":"https://video.twimg.com/b_1920.mp4"}]}}]}"""
        val p = XPosts.parseSyndication(body, "https://x.com/cairodesk/status/1858000000000000000") { "Nov 17" }!!
        assertEquals("Flooding on the corniche tonight & more rain due", p.text)
        assertEquals("Cairo Desk", p.authorName)
        assertEquals("https://pbs.twimg.com/profile_images/1/a_bigger.jpg", p.avatar)
        assertEquals("Nov 17", p.linkTitle)
        assertEquals(2, p.media.size)
        assertEquals(PostMedia(false, "https://pbs.twimg.com/media/A.jpg", aspect = 1.5f), p.media[0])
        assertEquals("https://video.twimg.com/b_1280.mp4", p.media[1].url)
        assertEquals(true, p.media[1].video)
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/B.jpg", p.media[1].poster)
        assertEquals("https://cdn.syndication.twimg.com/tweet-result?id=1858000000000000000&lang=en&token=${XPosts.token("1858000000000000000")}", XPosts.syndicationUrl("https://twitter.com/cairodesk/status/1858000000000000000"))
    }
}
