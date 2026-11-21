// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/**
 * Semantic version ("v0.4.0", "0.4", "0.7.0-beta.1"), ordered like SemVer: numbers first,
 * then a pre-release sorts *before* its release (0.7.0-beta.1 < 0.7.0-beta.2 < 0.7.0).
 */
data class AppVersion(val parts: List<Int>, val pre: List<String> = emptyList()) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val c = (parts.getOrNull(i) ?: 0).compareTo(other.parts.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size.coerceAtMost(1) - pre.size.coerceAtMost(1)
        val mine = sortKey(pre); val theirs = sortKey(other.pre)
        for (i in 0 until minOf(mine.size, theirs.size)) {
            val a = mine[i]; val b = theirs[i]
            val an = a.toIntOrNull(); val bn = b.toIntOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1 // numeric identifiers sort before alphanumeric ones
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return pre.size.compareTo(other.pre.size)
    }

    /** A dated build ("build.112026" or "beta.112026", MMddYY) compares by its date: YYMMdd. */
    private fun sortKey(p: List<String>): List<String> {
        val d = p.getOrNull(1)
        return if (p.size == 2 && (p[0] == "build" || p[0] == "beta") && d != null && d.length == 6 && d.all(Char::isDigit)) {
            listOf("build", d.substring(4, 6) + d.substring(0, 2) + d.substring(2, 4))
        } else p
    }

    val isPrerelease: Boolean get() = pre.isNotEmpty()

    override fun toString() = parts.joinToString(".") + if (pre.isEmpty()) "" else "-" + pre.joinToString(".")

    companion object {
        fun parse(raw: String?): AppVersion? {
            val clean = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('+') ?: return null
            val nums = clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }
            val pre = clean.substringAfter('-', "").split('.').filter { it.isNotEmpty() }
            return if (nums.isEmpty()) null else AppVersion(nums, pre)
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
        line.replace(RX_1, "").replace(RX_2, "$1").replace(RX_3, "$1")
            .replace(RX_4, "$1").replace(RX_5, "• ")
    }.replace(RX_6, "\n\n").trim()
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("^#{1,6}\\s*")
private val RX_2 = Regex("\\*\\*(.+?)\\*\\*")
private val RX_3 = Regex("`([^`]+)`")
private val RX_4 = Regex("\\[(.+?)]\\((.+?)\\)")
private val RX_5 = Regex("^\\s*[-*]\\s+")
private val RX_6 = Regex("\n{3,}")
