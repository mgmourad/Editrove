// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/** Semantic-ish version ("v0.4.0", "0.4", "1.2.3-beta") comparable numerically. */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = (parts.getOrNull(i) ?: 0).compareTo(other.parts.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        return 0
    }

    override fun toString() = parts.joinToString(".")

    companion object {
        fun parse(raw: String?): AppVersion? {
            val core = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('-')?.substringBefore('+') ?: return null
            val nums = core.split('.').map { it.toIntOrNull() ?: return null }
            return if (nums.isEmpty()) null else AppVersion(nums)
        }
    }
}

/** A published build of the app. */
data class ReleaseInfo(
    val version: AppVersion,
    val tag: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    /** Lower-case hex SHA-256 published by the host, when available. */
    val sha256: String?,
    val prerelease: Boolean,
)

object Releases {
    /** The newest release strictly newer than [current] that ships an APK, or null. */
    fun newest(current: AppVersion, releases: List<ReleaseInfo>): ReleaseInfo? =
        releases.filter { it.version > current }.maxByOrNull { it.version }

    /** Release notes as plain text: drops Markdown markers, keeps bullets. */
    fun plainNotes(markdown: String): String = markdown.lines().joinToString("\n") { line ->
        line.replace(Regex("^#{1,6}\\s*"), "").replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\[(.+?)]\\((.+?)\\)"), "$1").replace(Regex("^\\s*[-*]\\s+"), "• ")
    }.replace(Regex("\n{3,}"), "\n\n").trim()
}
