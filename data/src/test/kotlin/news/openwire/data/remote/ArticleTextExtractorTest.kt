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
}
