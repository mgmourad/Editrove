// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every release ships its own What's new: the app's version code must have a changelog file. */
class ChangelogFilesTest {
    @Test fun currentVersionHasItsNotes() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        val code = Regex("versionCode = (\\d+)").find(File(root, "app/build.gradle.kts").readText())!!.groupValues[1]
        val notes = File(root, "fastlane/metadata/android/en-US/changelogs/$code.txt")
        assertTrue("Add $notes for versionCode $code", notes.exists() && notes.readText().isNotBlank())
    }
}
