// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageQualityTest {
    @Test fun thumbnailsAreAskedForInLargeSizes() {
        assertEquals("https://ichef.bbci.co.uk/ace/standard/1024/cpsprodpb/4a66/live/x.jpg", ImageExtractor.highRes("https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/4a66/live/x.jpg"))
        assertEquals("https://static01.nyt.com/images/2026/11/23/a-superJumbo.jpg", ImageExtractor.highRes("https://static01.nyt.com/images/2026/11/23/a-thumbStandard.jpg"))
        assertEquals("https://lh3.googleusercontent.com/abc=w1200", ImageExtractor.highRes("https://lh3.googleusercontent.com/abc=w200-h150-p-df"))
        assertEquals("https://site.com/wp-content/uploads/photo.jpg", ImageExtractor.highRes("https://site.com/wp-content/uploads/photo-300x200.jpg"))
        assertEquals("https://i0.wp.com/site.com/p.jpg?w=1200", ImageExtractor.highRes("https://i0.wp.com/site.com/p.jpg?w=300"))
        // Signed addresses would break if changed.
        assertEquals("https://i.guim.co.uk/img/x.jpg?width=140&s=abc", ImageExtractor.highRes("https://i.guim.co.uk/img/x.jpg?width=140&s=abc"))
    }
}
