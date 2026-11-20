// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingPlanTest {
    private val registry = DomainRegistry()
    private val norm: (String) -> String = { registry.normalizeDomain(it) }
    private fun a(id: String, title: String, domain: String) = Article(id = id, title = title, source = domain.substringBefore('.').uppercase(), domain = domain, originalUrl = "https://$domain/$id")

    @Test fun duplicatesMergeAndRelatedStoriesSitTogether() {
        val list = listOf(
            a("1", "Israeli spy chief pressed for renewed Iran strikes during US visit", "wsj.com"),
            a("2", "AMD dives on short seller report of alleged China exposure", "cnbc.com"),
            a("3", "Israeli spy chief pressed US for renewed Iran strikes, report says", "reuters.com"),
            a("4", "Spacecraft reaches Mercury on second-ever mission to enter its orbit", "theguardian.com"),
            a("5", "Trump weighs fresh Iran strikes as diplomacy stalls", "axios.com"),
        )
        val plan = BriefingPlan.arrange(list, 4, norm)
        assertEquals(listOf("1", "5", "2", "4"), plan.map { it.article.id })
        assertEquals(listOf("REUTERS"), plan[0].alsoReportedBy)
        assertTrue(plan[1].relatedToPrevious)
        assertFalse(plan[2].relatedToPrevious)
    }

    @Test fun oneSharedNameIsNotARelatedStory() {
        assertFalse(BriefingPlan.related(a("1", "Trump backs Musk in latest breakup", "axios.com"), a("2", "Trump says people stockpiling food should relax", "thehill.com")))
    }

    @Test fun editionTabsGiveScopeAndSection() {
        assertEquals(BriefingScript.Scope.LOCAL, BriefingPlan.scopeOf(TopicRegistry.LOCAL_ID))
        assertEquals(BriefingScript.Scope.INTERNATIONAL, BriefingPlan.scopeOf("INTERNATIONAL"))
        assertEquals(BriefingScript.Category.TECHNOLOGY, BriefingPlan.categoryOf("AI"))
        assertEquals(null, BriefingPlan.categoryOf("TOP"))
    }
}
