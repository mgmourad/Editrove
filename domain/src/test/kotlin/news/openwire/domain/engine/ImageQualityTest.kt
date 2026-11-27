// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageQualityTest {
    @Test fun thumbnailsAreAskedForInLargeSizes() {
        assertEquals("https://ichef.bbci.co.uk/ace/standard/1536/cpsprodpb/4a66/live/x.jpg", ImageExtractor.highRes("https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/4a66/live/x.jpg"))
        // Social cards have no 1536 version.
        assertEquals("https://ichef.bbci.co.uk/news/1024/branded_news/1177/live/x.jpg", ImageExtractor.highRes("https://ichef.bbci.co.uk/news/1024/branded_news/1177/live/x.jpg"))
        assertEquals("https://static01.nyt.com/images/2026/11/23/a-superJumbo.jpg", ImageExtractor.highRes("https://static01.nyt.com/images/2026/11/23/a-thumbStandard.jpg"))
        assertEquals("https://lh3.googleusercontent.com/abc=w1200", ImageExtractor.highRes("https://lh3.googleusercontent.com/abc=w200-h150-p-df"))
        assertEquals("https://site.com/wp-content/uploads/photo.jpg", ImageExtractor.highRes("https://site.com/wp-content/uploads/photo-300x200.jpg"))
        assertEquals("https://i0.wp.com/site.com/p.jpg?w=1200", ImageExtractor.highRes("https://i0.wp.com/site.com/p.jpg?w=300"))
        // Signed addresses would break if changed.
        assertEquals("https://i.guim.co.uk/img/x.jpg?width=140&s=abc", ImageExtractor.highRes("https://i.guim.co.uk/img/x.jpg?width=140&s=abc"))
    }

    @Test fun pagePhotoSkipsBrandedShareCards() {
        val html = """
            <meta property="og:image" content="https://i.guim.co.uk/img/media/abc/master/2000.jpg?width=1200&height=630&overlay-align=bottom%2Cleft&overlay-width=100p&overlay-base64=L2ltZw&s=sig1">
            <figure><picture><img src="https://i.guim.co.uk/img/media/abc/master/2000.jpg?width=1300&dpr=2&s=none&crop=none" width="1300" height="780"></picture></figure>
        """
        assertEquals("https://i.guim.co.uk/img/media/abc/master/2000.jpg?width=1300&dpr=2&s=none&crop=none", ImageExtractor.fromHtml(html, "https://www.theguardian.com/x"))
        // The branded card is still used when it is the only photo.
        val only = """<meta property="og:image" content="https://i.guim.co.uk/img/media/abc/master/2000.jpg?width=1200&overlay-base64=L2ltZw&s=sig1">"""
        assertEquals("https://i.guim.co.uk/img/media/abc/master/2000.jpg?width=1200&overlay-base64=L2ltZw&s=sig1", ImageExtractor.fromHtml(only, "https://www.theguardian.com/x"))
    }
}
