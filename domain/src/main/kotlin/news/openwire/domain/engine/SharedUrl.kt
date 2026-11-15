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
        if (Regex("^https?%3A%2F%2F", RegexOption.IGNORE_CASE).containsMatchIn(c)) {
            c = try { URLDecoder.decode(c, "UTF-8") } catch (e: Exception) { c }
        }
        return c.replace(Regex("^[<\"'(]+|[>\"',;:!?).]+$"), "")
    }

    fun extract(text: String?, subject: String? = null): String {
        val raw = text?.trim().orEmpty()
        var target = ""
        if (raw.isNotEmpty() && !raw.contains(Regex("\\s"))) {
            val c = clean(raw)
            target = when {
                Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(c) -> c
                c.contains('.') -> "https://$c"
                else -> ""
            }
        }
        if (target.isEmpty() && raw.isNotEmpty()) {
            target = Regex("https?://[^\\s<>\"')]+", RegexOption.IGNORE_CASE).find(raw)?.value?.let(::clean)
                ?: Regex("(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}/[^\\s<>\"')]*").find(raw)?.value?.let { "https://" + clean(it) }
                ?: ""
        }
        if (target.isEmpty() && !subject.isNullOrEmpty()) {
            target = Regex("https?://[^\\s<>\"')]+", RegexOption.IGNORE_CASE).find(subject)?.value?.let(::clean) ?: ""
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
