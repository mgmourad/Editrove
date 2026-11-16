// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublisherLogosTest {
    private val page = """
        <head>
          <link rel="icon" href="/favicon-32.png" sizes="32x32">
          <link rel="apple-touch-icon" sizes="180x180" href="//static.example.com/touch-180.png">
          <link rel="mask-icon" href="/mask.svg">
          <link rel="manifest" href="/site.webmanifest">
        </head>
    """.trimIndent()

    @Test fun picksTheLargestPageIcon() {
        val c = PublisherLogos.fromHtml(page, "https://www.example.com/")
        assertEquals("https://static.example.com/touch-180.png", PublisherLogos.best(c))
        assertEquals("https://www.example.com/site.webmanifest", PublisherLogos.manifestUrl(page, "https://www.example.com/"))
    }

    @Test fun manifestIconsBeatTouchIconsAndSkipSvgAndPreferNonMaskable() {
        val json = """{"icons":[{"src":"/icons/192.png","sizes":"192x192"},{"src":"/icons/512-mask.png","sizes":"512x512","purpose":"maskable"},
            {"src":"/icons/512.png","sizes":"512x512","purpose":"any"},{"src":"/icons/logo.svg","sizes":"any"}]}"""
        val all = PublisherLogos.fromManifest(json, "https://www.example.com/site.webmanifest") + PublisherLogos.fromHtml(page, "https://www.example.com/")
        assertEquals("https://www.example.com/icons/512.png", PublisherLogos.best(all))
    }

    @Test fun tinyFaviconsAreNotEnough() {
        val c = PublisherLogos.fromHtml("""<link rel="shortcut icon" href="/favicon.ico">""", "https://x.com/")
        assertNull(PublisherLogos.best(c))
    }
}
