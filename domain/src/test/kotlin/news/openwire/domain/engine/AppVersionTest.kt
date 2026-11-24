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

    @Test fun preReleasesSortBeforeTheirRelease() {
        assertTrue(v("0.7.0-beta.1") > v("0.6.1"))
        assertTrue(v("0.7.0-beta.1") < v("0.7.0-beta.2"))
        assertTrue(v("0.7.0-beta.2") < v("0.7.0-beta.10"))
        assertTrue(v("0.7.0-beta.9") < v("0.7.0"))
        assertTrue(v("0.7.0-alpha.3") < v("0.7.0-beta.1"))
        assertEquals("0.7.0-beta.1", v("v0.7.0-beta.1").toString())
        assertTrue(v("0.7.0-beta.1").isPrerelease)
    }

    @Test fun finalReleaseIsOfferedOverItsBeta() {
        val list = listOf(rel("v0.7.0-beta.1"), rel("v0.7.0"))
        assertEquals("v0.7.0", Releases.newest(v("0.7.0-beta.1"), list)?.tag)
        assertEquals("v0.7.0-beta.1", Releases.newest(v("0.6.1"), listOf(rel("v0.7.0-beta.1")))?.tag)
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

    @Test fun datedBuildsSortByDateAcrossYears() {
        val nov = AppVersion.parse("0.9-build.112026")!!
        val dec = AppVersion.parse("0.9-build.120126")!!
        val jan = AppVersion.parse("0.9-build.010227")!!
        assertTrue(AppVersion.parse("0.8.2-beta.1")!! < nov)
        assertTrue(nov < dec)
        assertTrue(dec < jan)
        assertEquals("0.9-build.112026", nov.toString())
    }

    @Test fun aFourPartPatchFollowsTheDatedBuild() {
        assertTrue(AppVersion.parse("0.9.2.1")!! > AppVersion.parse("0.9.2-build.112326")!!)
        assertTrue(AppVersion.parse("v0.9.2.1")!! < AppVersion.parse("0.9.3")!!)
        assertEquals(false, AppVersion.parse("0.9.2.1")!!.isPrerelease)
        // The next beta is offered over the patch.
        assertTrue(AppVersion.parse("0.9.3-beta.112426")!! > AppVersion.parse("0.9.2.1")!!)
    }
}
