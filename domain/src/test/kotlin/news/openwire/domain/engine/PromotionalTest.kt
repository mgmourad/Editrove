// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionalTest {
    @Test fun dealsAndSponsoredPiecesAreNotNews() {
        assertTrue(Promotional.isPromotionalTitle("The best Black Friday deals on TVs, laptops and headphones"))
        assertTrue(Promotional.isPromotionalTitle("The 12 best air fryers to buy in 2026"))
        assertFalse(Promotional.isPromotionalTitle("Retailers brace for a weak Black Friday as consumer spending slows"))
        assertFalse(Promotional.isPromotionalTitle("Jensen Huang reinstated as Nvidia CEO"))
        assertFalse(Promotional.isPromotionalTitle("EU and UK agree trade deal after months of talks"))
        assertTrue(Promotional.isPromotionalTitle("Black Friday deals: AirPods Pro hit their lowest price"))
        assertTrue(Promotional.isPromotionalText("We may earn a commission when you buy through links on our site. Click on the link below to shop now."))
        assertFalse(Promotional.isPromotionalText("The council voted on Tuesday to approve the budget after a long debate."))
    }
}
