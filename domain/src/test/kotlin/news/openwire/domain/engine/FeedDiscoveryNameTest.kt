// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedDiscoveryNameTest {
    @Test fun rssChannelSectionNamesAreTidied() {
        assertEquals("CNN.com – Tech", FeedDiscovery.cleanName("CNN.com - RSS Channel - App Tech Section", null))
    }
}
