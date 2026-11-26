// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.foryou

import kotlin.math.ln
import news.openwire.domain.alerts.HeadlineMatch
import news.openwire.domain.model.Article

/**
 * "For You": stories picked from the feeds by what the reader has loved, entirely on the phone.
 * The loved headlines' distinctive words (and their outlets, a little) make a taste profile; each
 * candidate story scores by the words it shares with it, rare shared words counting most.
 */
object ForYou {
    /** A loved story's headline and outlet. */
    data class Loved(val title: String, val domain: String?)

    /** One story with the topic it was found in. */
    data class Candidate(val topicId: String, val article: Article)

    fun recommend(
        loved: List<Loved>,
        candidates: List<Candidate>,
        exclude: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
        max: Int = 20,
    ): List<Article> {
        if (loved.isEmpty()) return emptyList()
        val lovedTokens = loved.map { HeadlineMatch.tokens(it.title) }
        // How often each word appears across loved headlines; and across all candidates (rarity).
        val taste = HashMap<String, Int>().apply { lovedTokens.forEach { t -> t.forEach { merge(it, 1, Int::plus) } } }
        val pool = candidates.distinctBy { it.article.id }.filter { it.article.id !in exclude && it.article.title.length > 12 }
        val docFreq = HashMap<String, Int>().apply { pool.forEach { c -> HeadlineMatch.tokens(c.article.title).forEach { merge(it, 1, Int::plus) } } }
        val outlets = loved.mapNotNull { it.domain }.groupingBy { it }.eachCount()
        val scored = pool.mapNotNull { c ->
            val a = c.article
            val t = HeadlineMatch.tokens(a.title)
            // A story the reader already loved (or the same story from another outlet) isn't a recommendation.
            if (lovedTokens.any { HeadlineMatch.same(it, t) }) return@mapNotNull null
            var s = 0.0
            for (w in t) {
                val k = taste[w] ?: continue
                s += k * ln(1.0 + pool.size.toDouble() / (docFreq[w] ?: 1))
            }
            if (s <= 0.0) return@mapNotNull null
            a.domain?.let { d -> outlets[d]?.let { s += 0.6 * ln(1.0 + it) } }
            val ageH = ((now - (a.publishedAt ?: now)) / 3_600_000.0).coerceAtLeast(0.0)
            s *= 1.0 / (1.0 + ageH / 36.0) // fresher first, gently
            a to s
        }.sortedByDescending { it.second }
        // One story per headline, and no outlet three times in a row.
        val out = ArrayList<Article>()
        val used = ArrayList<Set<String>>()
        val rest = scored.map { it.first }.toMutableList()
        while (out.size < max && rest.isNotEmpty()) {
            val pick = rest.firstOrNull { a ->
                out.size < 2 || !(out[out.size - 1].domain == a.domain && out[out.size - 2].domain == a.domain)
            } ?: rest.first()
            rest.remove(pick)
            val t = HeadlineMatch.tokens(pick.title)
            if (used.any { HeadlineMatch.same(it, t) }) continue
            used += t
            out += pick
        }
        return out
    }
}
