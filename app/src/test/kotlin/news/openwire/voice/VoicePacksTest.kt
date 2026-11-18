// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePacksTest {
    @Test
    fun everyAppLanguageHasAVoice() {
        listOf("en", "ar", "fr", "es", "de").forEach { assertEquals(it, VoicePacks.forLanguage(it)?.language) }
        assertNull(VoicePacks.forLanguage("ja"))
    }

    @Test
    fun androidLanguageCodesMapToVoices() {
        assertEquals("ar", VoicePacks.fromIso3("ara")?.language)
        assertEquals("de", VoicePacks.fromIso3("deu")?.language)
        assertEquals("fr", VoicePacks.fromIso3("fra")?.language)
    }

    @Test
    fun voicesDownloadFromSherpaReleases() {
        VoicePacks.ALL.forEach { p ->
            assertTrue(p.url, p.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-"))
            assertTrue(p.model.endsWith(".onnx"))
            assertEquals("editrove-${p.language}", p.voiceName)
        }
    }
}
