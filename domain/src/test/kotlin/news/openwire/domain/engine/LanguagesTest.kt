// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.BriefingDetail
import news.openwire.domain.model.LocalEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    private val cairo = LocalEdition("Cairo", "Cairo", "EG")

    @Test
    fun arabicAppReadsArabicFeedsWithArabicNames() {
        val topics = TopicRegistry.defaultTopics(cairo, "ar")
        assertEquals("أهم الأخبار", topics.first { it.id == "TOP" }.label)
        assertEquals("مصر", topics.first { it.id == TopicRegistry.NATIONAL_ID }.label)
        topics.filter { it.liveUrl?.contains("news.google.com") == true && it.id != TopicRegistry.LOCAL_ID }
            .forEach { assertTrue(it.id, it.liveUrl!!.contains("hl=ar")) }
    }

    @Test
    fun englishAppKeepsEnglishFeedsEvenInAnArabicEdition() {
        val topics = TopicRegistry.defaultTopics(cairo, "en")
        assertTrue(topics.first { it.id == "TOP" }.liveUrl!!.contains("hl=en"))
        assertEquals("Top Stories", topics.first { it.id == "TOP" }.label)
        // The edition's own national tab still reads in Egypt's language.
        assertTrue(topics.first { it.id == TopicRegistry.NATIONAL_ID }.liveUrl!!.contains("hl=ar"))
    }

    @Test
    fun searchTopicsAskInTheFeedLanguage() {
        val fr = TopicRegistry.defaultTopics(LocalEdition("Paris", "Île-de-France", "FR"), "fr")
        assertTrue(fr.first { it.id == "POLITICS" }.liveUrl!!.contains("politique"))
        assertEquals("fr", Languages.feedLanguage(fr.first { it.id == "AI" }.liveUrl))
    }

    @Test
    fun relativeTimeIsTranslated() {
        assertEquals("6h ago", Languages.relativeTime("en", 360))
        assertEquals("Il y a 6 h", Languages.relativeTime("fr", 360))
        assertEquals("Vor 5 Min.", Languages.relativeTime("de", 5))
    }

    @Test
    fun editionCountryOffersItsLanguage() {
        assertEquals("ar", Languages.forCountry("EG")?.code)
        assertEquals("es", Languages.forCountry("MX")?.code)
        assertEquals("de", Languages.forCountry("CH")?.code)
        assertEquals(null, Languages.forCountry("JP")) // Editrove isn't in Japanese yet
    }

    @Test
    fun egyptHasLocalNewsroomsThatJoinTheRightTabs() {
        val egypt = LocalSources.forCountry("EG")
        assertTrue(egypt.any { it.name == "Al-Ahram" } && egypt.any { it.name == "Mada Masr" })
        val youm7 = egypt.first { it.name == "Youm7" }
        assertTrue(youm7.url.contains("site%3Ayoum7.com") && youm7.url.contains("gl=EG"))
        // Arabic outlet, English app: the national tab only. English outlet: Top Stories.
        assertEquals(listOf(TopicRegistry.NATIONAL_ID), LocalSources.toFeed(youm7, "EG", "en", 0).topics)
        val mada = egypt.first { it.name == "Mada Masr" }
        assertEquals(listOf("TOP"), LocalSources.toFeed(mada, "EG", "en", 0).topics)
        assertFalse(LocalSources.forCountry("FR").isEmpty())
    }

    @Test
    fun briefingSpeaksTheChosenLanguage() {
        val now = java.time.LocalDateTime.of(2026, 11, 17, 8, 0)
        assertTrue(BriefingScript.intro("أهم الأخبار", 5, BriefingDetail.HEADLINES, now, "ar").startsWith("صباح الخير"))
        assertTrue(BriefingScript.intro("Portada", 5, BriefingDetail.HEADLINES, now, "es").contains("martes"))
        assertTrue(BriefingScript.outro("Top", "en").startsWith("And that's your Top briefing"))
    }
}
