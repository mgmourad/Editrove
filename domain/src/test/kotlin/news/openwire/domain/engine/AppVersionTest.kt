// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    private fun v(s: String) = AppVersion.parse(s)!!

    @Test fun comparesNumerically() {
        assertTrue(v("v0.10.0") > v("0.9.9"))
        assertTrue(v("1.0") == v("1.0.0") || v("1.0").compareTo(v("1.0.0")) == 0)
        assertTrue(v("0.4.0-beta") > v("0.3.9"))
        assertNull(AppVersion.parse("latest"))
    }

    private fun rel(tag: String) = ReleaseInfo(v(tag), tag, tag, "", "https://x/$tag.apk", 1, null, true)

    @Test fun picksNewestNewerRelease() {
        val list = listOf(rel("v0.2.0"), rel("v0.4.1"), rel("v0.3.0"), rel("v0.4.0"))
        assertEquals("v0.4.1", Releases.newest(v("0.3.0"), list)?.tag)
        assertNull(Releases.newest(v("0.4.1"), list))
    }

    @Test fun markdownToPlainText() {
        assertEquals("New\n• Bold thing and code", Releases.plainNotes("### New\n- **Bold** thing and `code`"))
    }
}
