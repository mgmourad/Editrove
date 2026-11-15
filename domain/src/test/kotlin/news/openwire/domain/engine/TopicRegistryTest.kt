package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicRegistryTest {

    @Test
    fun worldTopicIsLabeledInternational() {
        assertEquals("International", TopicRegistry.defaultTopics().first { it.id == "INTERNATIONAL" }.label)
    }

    @Test
    fun usAndWashingtonAreLiveOnlyInPhase1() {
        assertTrue(TopicRegistry.defaultTopics().first { it.id == "US" }.liveOnly)
        assertTrue(TopicRegistry.defaultTopics().first { it.id == "WASHINGTON_DC" }.liveOnly)
    }

    @Test
    fun snapshotTopicsExcludeLiveOnlyTopics() {
        val snapshot = TopicRegistry.snapshotTopics()
        assertEquals(0, snapshot.count { it.liveOnly })
        assertEquals(12, snapshot.size)
        assertTrue(snapshot.all { it.snapshotFile != null })
    }

    @Test
    fun defaultTopicsHaveUniqueIds() {
        val ids = TopicRegistry.defaultTopics().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun internationalTopicReadsWorldSnapshotFile() {
        assertFalse(TopicRegistry.defaultTopics().first { it.id == "INTERNATIONAL" }.liveOnly)
        assertEquals("news_world.json", TopicRegistry.defaultTopics().first { it.id == "INTERNATIONAL" }.snapshotFile)
    }
}
