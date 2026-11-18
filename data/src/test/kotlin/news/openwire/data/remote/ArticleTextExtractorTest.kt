// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTextExtractorTest {
    private val page = """
        <html><head><meta property="og:site_name" content="DC News Now"></head><body>
        <header><nav><p>DC News Now | DC, Virginia, Maryland News, Weather, Traffic, Sports Live — Menu and sections for every neighbourhood</p></nav></header>
        <main><article>
          <h1>Amtrak halts service in the DC area after local officials hold tracks</h1>
          <p>DC News Now | DC, Virginia, Maryland News, Weather, Traffic, Sports Live WASHINGTON (DC News Now) — Service for Amtrak trains in Washington, D.C., has been halted after local officials placed a hold on the tracks.</p>
          <p>Passengers at Union Station were told to expect delays of several hours while crews and city officials worked through the dispute.</p>
          <p>The hold was placed Monday morning after a disagreement over maintenance work on a bridge that carries the line into the city.</p>
          <div class="related-stories"><p>Related: Metro announces new late-night schedule for the holiday season across the region</p></div>
        </article></main>
        <aside><p>Launched in 2021 and based in the capital, DC News Now is provided by Nexstar Media, Inc., and uses the My Nexstar sign-in.</p></aside>
        <footer><p>Nexstar Media, Inc. is a leading, diversified media company that leverages the power of local connection.</p></footer>
        </body></html>
    """.trimIndent()

    @Test fun keepsTheStoryAndDropsSiteChrome() {
        val ps = ArticleTextExtractor().articleParagraphs(Jsoup.parse(page))
        assertEquals(3, ps.size)
        assertTrue(ps.first().startsWith("WASHINGTON (DC News Now) — Service for Amtrak"))
        assertFalse(ps.any { it.contains("Nexstar") || it.contains("Related:") || it.contains("Menu and sections") })
    }

    @Test
    fun previewMustBeAboutTheHeadline() {
        val x = ArticleTextExtractor(okhttp3.OkHttpClient())
        val title = "مصير طاقم ناقلة مجهول بعد هجوم ثان بمضيق هرمز"
        assertTrue(x.aboutTitle("قالت هيئة عمليات التجارة البحرية البريطانية إن مصير طاقم ناقلة تعرضت لهجوم ثان أثناء عبورها مضيق هرمز لا يزال مجهولا", title))
        assertFalse(x.aboutTitle("أعربت جمهورية مصر العربية عن إدانتها واستنكارها البالغين للاعتداءات التي استهدفت مملكة البحرين", title))
        assertTrue(x.aboutTitle("Marine Le Pen presented her pension reform plan on Tuesday.", "Le Pen unveils pension reform"))
    }

    @Test
    fun embeddedXPostsAreCollectedAndLeftOutOfTheText() {
        val html = """<html><body><article>
            <p>The minister spoke on Tuesday about the new plan for the coast and its many towns.</p>
            <blockquote class="twitter-tweet"><p lang="en">This is a post that should not be read as body text at all.</p>&mdash; A (@a) <a href="https://twitter.com/a/status/1234567890?ref_src=twsrc">Nov 17</a></blockquote>
            <p>Officials said the work would start next year, after a long consultation with residents.</p>
            </article></body></html>"""
        val doc = Jsoup.parse(html)
        val x = ArticleTextExtractor(okhttp3.OkHttpClient())
        assertEquals(listOf("https://x.com/a/status/1234567890"), x.embeddedPosts(doc))
        assertFalse(x.articleParagraphs(doc).any { it.contains("should not be read") })
    }

    @Test
    fun xOembedIsReadIntoAPost() {
        val body = """{"url":"https://x.com/jack/status/20","author_name":"jack","author_url":"https://x.com/jack","html":"<blockquote class=\"twitter-tweet\" data-dnt=\"true\"><p lang=\"en\" dir=\"ltr\">just setting up my twttr</p>&mdash; jack (@jack) <a href=\"https://x.com/jack/status/20?ref_src=twsrc%5Etfw\">March 21, 2006</a></blockquote>"}"""
        val p = news.openwire.domain.opinion.XPosts.parse(body, "https://x.com/jack/status/20")!!
        assertEquals("just setting up my twttr", p.text)
        assertEquals("jack", p.authorHandle)
        assertEquals("March 21, 2006", p.linkTitle)
    }

    @Test fun findsPhotosAndVideosBetweenParagraphs() {
        val html = """
            <html><head><meta property="og:image" content="https://cdn.example.com/lead.jpg?w=1200"></head><body><article>
            <figure><img src="https://cdn.example.com/lead.jpg"><figcaption>The lead photo</figcaption></figure>
            <p>The first paragraph of the story is long enough to count as part of the article text here.</p>
            <figure><img data-src="/img/bridge.jpg" src="data:image/gif;base64,R0lGOD"><figcaption>The bridge on Monday morning.</figcaption></figure>
            <p>The second paragraph keeps going with more detail about what happened on the bridge today.</p>
            <img src="https://cdn.example.com/site-logo.png" width="120">
            <iframe src="https://www.youtube.com/embed/dQw4w9WgXcQ"></iframe>
            <p>The third paragraph adds the reaction from officials and residents who live near the river.</p>
            <video poster="/p.jpg"><source src="https://cdn.example.com/clip.mp4" type="video/mp4"></video>
            <p>The last paragraph wraps up the story with what comes next for the bridge and its repairs.</p>
            <img src="https://cdn.example.com/related.jpg">
            </article></body></html>
        """.trimIndent()
        val x = ArticleTextExtractor(okhttp3.OkHttpClient())
        val doc = Jsoup.parse(html, "https://news.example.com/story")
        doc.select("figure").forEach { f -> f.selectFirst("figcaption")?.text()?.let { f.attr("data-ew-caption", it) } }
        val ps = x.articleParagraphs(doc)
        val media = x.articleMedia(doc, ps, "https://cdn.example.com/lead.jpg?w=1200")
        assertEquals(listOf("https://news.example.com/img/bridge.jpg", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://cdn.example.com/clip.mp4"), media.map { it.url })
        assertEquals(listOf(0, 1, 2), media.map { it.after })
        assertEquals("The bridge on Monday morning.", media[0].caption)
        assertEquals(ArticleMedia.Kind.EXTERNAL, media[1].kind)
        assertEquals("https://news.example.com/p.jpg", media[2].poster)
    }
}
