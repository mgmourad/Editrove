// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/**
 * On-device extractive summary: picks the article's most representative
 * sentences (word-frequency scoring with a lead bias) and returns them in
 * reading order. No network, no model — the article's own words.
 */
object Summarizer {

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "but", "if", "of", "to", "in", "on", "for", "with", "at", "by", "from", "as",
        "is", "was", "are", "were", "be", "been", "being", "it", "its", "that", "this", "these", "those", "he", "she",
        "they", "we", "you", "i", "his", "her", "their", "our", "your", "not", "no", "so", "than", "then", "there",
        "has", "have", "had", "will", "would", "can", "could", "said", "says", "also", "who", "which", "what", "when",
        "about", "after", "into", "over", "more", "most", "some", "such", "do", "does", "did", "just", "up", "out",
    )

    /** Splits text into sentences (no length filtering). */
    fun split(text: String): List<String> = text.replace(RX_1, " ").split(RX_2).map { it.trim() }.filter { it.isNotEmpty() }

    fun sentences(text: String): List<String> = text
        .replace(RX_1, " ")
        .split(RX_2)
        .map { it.trim() }
        .filter { it.length in 40..400 }

    private fun words(s: String) = s.lowercase().split(RX_3).filter { it.length > 2 && it !in STOP }

    /** Up to [count] key sentences, or empty when the text is too short to summarize. */
    fun keyPoints(text: String, count: Int = 3): List<String> {
        val all = sentences(text).filter { it.length <= 260 }
        if (all.size <= count) return emptyList()
        val freq = HashMap<String, Int>()
        all.forEach { s -> words(s).forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
        val max = (freq.values.maxOrNull() ?: 1).toDouble()
        val scored = all.mapIndexed { i, s ->
            val ws = words(s)
            val base = if (ws.isEmpty()) 0.0 else ws.sumOf { (freq[it] ?: 0) / max } / Math.sqrt(ws.size.toDouble())
            val lead = if (i < 3) 0.35 * (3 - i) / 3 else 0.0
            val quote = if (s.contains('“') || s.contains('"')) -0.1 else 0.0
            Triple(i, s, base + lead + quote)
        }
        val picked = ArrayList<Triple<Int, String, Double>>()
        for (c in scored.sortedByDescending { it.third }) {
            if (picked.size == count) break
            // Avoid near-duplicates.
            if (picked.none { FeedWeaver.calculateTitleSimilarity(it.second, c.second) > 0.5 }) picked += c
        }
        return picked.sortedBy { it.first }.map { it.second }
    }
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("\\s+")
private val RX_2 = Regex("(?<=[.!?][\"”’)]?)\\s+(?=[\"“‘(]?[A-Z0-9])")
private val RX_3 = Regex("[^\\p{L}\\p{N}]+")
