// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRelocationTest {
    /** CBC moved every feed from /cmlink/rss-X to /webfeed/rss/rss-X (Nov 2026), listed on cbc.ca/rss. */
    private val cbcDirectory = """
        <html><head><link rel="alternate" type="application/rss+xml" href="https://www.cbc.ca/webfeed/rss/rss-topstories"></head>
        <body><a href="/webfeed/rss/rss-canada">Canada</a> <a href="/webfeed/rss/rss-canada-britishcolumbia">B.C.</a>
        <a href="/webfeed/rss/rss-canada-calgary">Calgary</a> <a href="https://www.cbc.ca/webfeed/rss/rss-world">World</a>
        <a href="/news">News</a> <a href="https://twitter.com/cbc/rss">x</a></body></html>
    """

    @Test fun findsTheSameSectionAtItsNewAddress() {
        val links = FeedRelocation.feedLinks(cbcDirectory, "https://www.cbc.ca/rss/")
        assertTrue(links.none { "twitter" in it || it.endsWith("/news") })
        assertEquals("https://www.cbc.ca/webfeed/rss/rss-canada-britishcolumbia", FeedRelocation.rank("https://www.cbc.ca/cmlink/rss-canada-britishcolumbia", links).first())
        assertEquals("https://www.cbc.ca/webfeed/rss/rss-canada", FeedRelocation.rank("https://www.cbc.ca/cmlink/rss-canada", links).first())
        assertEquals("https://www.cbc.ca/webfeed/rss/rss-world", FeedRelocation.rank("https://www.cbc.ca/cmlink/rss-world", links).first())
    }

    @Test fun anUnrelatedSectionIsNeverTaken() {
        val links = FeedRelocation.feedLinks(cbcDirectory, "https://www.cbc.ca/rss/")
        assertTrue(FeedRelocation.rank("https://www.cbc.ca/cmlink/rss-technology", links).isEmpty())
    }

    @Test fun aPlainFeedTakesTheSitesMainFeed() {
        val html = """<link rel="alternate" type="application/rss+xml" href="/feed/">"""
        assertEquals("https://example.org/feed/", FeedRelocation.rank("https://example.org/rss.xml", FeedRelocation.feedLinks(html, "https://example.org/")).first())
    }
}
