// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.net.URI
import java.net.URLDecoder

/**
 * Pulls an article URL out of shared text (Android ACTION_SEND / PROCESS_TEXT) or a
 * pasted string. Port of js/newsService.js `extractSharedUrl`.
 */
object SharedUrl {

    private fun clean(candidate: String): String {
        var c = candidate.trim()
        if (RX_1.containsMatchIn(c)) {
            c = try { URLDecoder.decode(c, "UTF-8") } catch (e: Exception) { c }
        }
        return c.replace(RX_2, "")
    }

    fun extract(text: String?, subject: String? = null): String {
        val raw = text?.trim().orEmpty()
        var target = ""
        if (raw.isNotEmpty() && !raw.contains(RX_3)) {
            val c = clean(raw)
            target = when {
                RX_6.containsMatchIn(c) -> c
                c.contains('.') -> "https://$c"
                else -> ""
            }
        }
        if (target.isEmpty() && raw.isNotEmpty()) {
            target = RX_4.find(raw)?.value?.let(::clean)
                ?: RX_5.find(raw)?.value?.let { "https://" + clean(it) }
                ?: ""
        }
        if (target.isEmpty() && !subject.isNullOrEmpty()) {
            target = RX_4.find(subject)?.value?.let(::clean) ?: ""
        }
        if (target.isEmpty()) return ""
        return try {
            val host = URI(target).host
            if (host.isNullOrEmpty() || !host.contains('.')) "" else target
        } catch (e: Exception) {
            ""
        }
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("^https?%3A%2F%2F", RegexOption.IGNORE_CASE)
private val RX_2 = Regex("^[<\"'(]+|[>\"',;:!?).]+$")
private val RX_3 = Regex("\\s")
private val RX_4 = Regex("https?://[^\\s<>\"')]+", RegexOption.IGNORE_CASE)
private val RX_5 = Regex("(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}/[^\\s<>\"')]*")
private val RX_6 = Regex("^https?://", RegexOption.IGNORE_CASE)
