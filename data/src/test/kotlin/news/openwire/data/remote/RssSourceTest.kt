// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import news.openwire.domain.engine.DomainRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssSourceTest {

    private val sampleRss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Google News</title>
            <item>
              <title>Breaking Tech Development - The Guardian</title>
              <link>https://news.google.com/rss/articles/CBMiABC</link>
              <pubDate>2026-09-14T10:00:00Z</pubDate>
              <description>Some description</description>
              <source url="https://www.theguardian.com">The Guardian</source>
            </item>
            <item>
              <title>SpaceX Starship Completes Flight Test - TechCrunch</title>
              <link>https://news.google.com/rss/articles/CBMiXYZ</link>
              <pubDate>2026-09-14T12:00:00Z</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parseExtractsItemsWithTitleLinkAndDate() {
        val items = GoogleNewsRssSource().parse(sampleRss)
        assertEquals(2, items.size)
        assertEquals("Breaking Tech Development - The Guardian", items[0].title)
        assertEquals("https://news.google.com/rss/articles/CBMiABC", items[0].link)
        assertEquals("2026-09-14T10:00:00Z", items[0].pubDate)
        assertEquals("https://www.theguardian.com", items[0].source)
    }

    @Test
    fun normalizeSplitsTitleAndResolvesPublisherDomain() {
        val normalizer = RssNormalizer(DomainRegistry())
        val item = GoogleNewsRssSource().parse(sampleRss)[0]
        val article = normalizer.normalize(item, "TOP", 0)

        assertEquals("Breaking Tech Development", article.title)
        assertEquals("The Guardian", article.source)
        assertEquals("theguardian.com", article.domain)
        assertTrue(article.publishedAt != null)
    }

    @Test
    fun parsesAtomEntriesWithImagesAndSummaries() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
              <title>The Verge</title>
              <entry>
                <title>New phone launched</title>
                <link rel="alternate" href="https://www.theverge.com/2026/phone"/>
                <published>2026-09-14T10:00:00Z</published>
                <summary type="html">&lt;p&gt;The phone is a big deal for everyone who buys phones.&lt;/p&gt;</summary>
                <media:thumbnail url="https://cdn.vox-cdn.com/photo.jpg"/>
              </entry>
            </feed>
        """.trimIndent()
        val feed = GoogleNewsRssSource().parseFeed(atom)
        assertEquals("The Verge", feed.title)
        val item = feed.items.single()
        assertEquals("https://www.theverge.com/2026/phone", item.link)
        assertEquals("https://cdn.vox-cdn.com/photo.jpg", item.imageUrl)

        val article = RssNormalizer(DomainRegistry()).normalize(item, "TECHNOLOGY", 0, feedName = "The Verge", feedUrl = "https://www.theverge.com/rss/index.xml")
        assertEquals("New phone launched", article.title)
        assertEquals("The Verge", article.source)
        assertEquals("theverge.com", article.domain)
        assertEquals("The phone is a big deal for everyone who buys phones.", article.summary)
        assertEquals("https://www.theverge.com/rss/index.xml", article.feedUrl)
    }

    @Test
    fun rssEnclosureAndDescriptionImages() {
        val rss = """
            <rss version="2.0"><channel><title>CBC</title>
              <item><title>Story one</title><link>https://www.cbc.ca/news/one</link>
                <enclosure url="https://i.cbc.ca/one.jpg" type="image/jpeg"/></item>
              <item><title>Story two</title><link>https://www.cbc.ca/news/two</link>
                <description>&lt;img src="https://i.cbc.ca/two.jpg"&gt; text</description></item>
            </channel></rss>
        """.trimIndent()
        val items = GoogleNewsRssSource().parse(rss)
        assertEquals("https://i.cbc.ca/one.jpg", items[0].imageUrl)
        val second = RssNormalizer(DomainRegistry()).normalize(items[1], "TOP", 1, feedName = "CBC News")
        assertEquals("https://i.cbc.ca/two.jpg", second.imageUrl)
    }

    @Test
    fun photoCreditInSourceTagIsNotTheOutlet() {
        val xml = """<rss><channel><item><title>Trump heads to Asia</title><link>https://www.france24.com/en/asia-pacific/20261117-trump</link>
            <source url="https://s.france24.com/media/display/x.jpg">© Manuel Balce Ceneta, AP</source><pubDate>Tue, 17 Nov 2026 03:07:59 GMT</pubDate></item></channel></rss>"""
        val item = GoogleNewsRssSource().parse(xml).first()
        val a = RssNormalizer(DomainRegistry()).normalize(item, "INTERNATIONAL", 0, feedName = "France 24", feedUrl = "https://www.france24.com/en/rss")
        org.junit.Assert.assertEquals("France 24", a.source)
        org.junit.Assert.assertEquals("france24.com", a.domain)
    }
}
