// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A site that moved its feeds (like CBC in Nov 2026), simulated without the network. */
class FeedSelfHealTest {
    private val requests = mutableListOf<String>()
    private val feed = """<?xml version="1.0"?><rss><channel><title>B.C.</title>
        <item><title>One</title><link>https://news.example.com/1</link></item>
        <item><title>Two</title><link>https://news.example.com/2</link></item></channel></rss>"""
    private val directory = """<html><body>
        <a href="/webfeed/rss/rss-canada">Canada</a>
        <a href="/webfeed/rss/rss-canada-britishcolumbia">B.C.</a>
        <a href="/webfeed/rss/rss-world">World</a></body></html>"""

    private val site = Interceptor { chain ->
        val url = chain.request().url.toString()
        requests += url
        val (code, body, type) = when {
            "/cmlink/" in url -> Triple(404, "gone", "text/html")
            url.endsWith("/rss") || url.endsWith("/rss/") -> Triple(200, directory, "text/html")
            "/webfeed/rss/rss-canada-britishcolumbia" in url -> Triple(200, feed, "application/rss+xml")
            url == "https://news.example.com/" -> Triple(200, "<html>home</html>", "text/html")
            else -> Triple(404, "", "text/html")
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody(type.toMediaType())).build()
    }

    @Test fun aMovedFeedIsFoundAgainAndRemembered() = runTest {
        val store = object : FeedRelocations {
            val map = HashMap<String, String>()
            override fun get(url: String) = map[url]
            override fun put(url: String, to: String) { map[url] = to }
            override fun remove(url: String) { map.remove(url) }
        }
        val source = GoogleNewsRssSource(OkHttpClient.Builder().addInterceptor(site).build(), store)
        val old = "https://news.example.com/cmlink/rss-canada-britishcolumbia"
        val got = source.fetchFeed(old)
        assertEquals(2, got.items.size)
        assertEquals("https://news.example.com/webfeed/rss/rss-canada-britishcolumbia", store.map[old])
        // Next time: straight to the new address, no search.
        requests.clear()
        source.fetchFeed(old)
        assertEquals(listOf("https://news.example.com/webfeed/rss/rss-canada-britishcolumbia"), requests)
    }

    @Test fun aSectionTheSiteNoLongerHasStaysAnError() = runTest {
        val source = GoogleNewsRssSource(OkHttpClient.Builder().addInterceptor(site).build(), FeedRelocations.Memory)
        val r = runCatching { source.fetchFeed("https://news.example.com/cmlink/rss-technology") }
        assertTrue(r.isFailure)
    }
}
