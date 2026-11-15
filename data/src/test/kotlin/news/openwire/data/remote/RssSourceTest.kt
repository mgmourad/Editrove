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
}
