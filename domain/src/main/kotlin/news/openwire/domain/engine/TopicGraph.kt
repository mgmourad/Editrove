// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/**
 * Connects a new topic's keywords the way the news connects them. Each keyword brings its
 * own live headlines; a term or an outlet that turns up across several keywords' headlines
 * is more strongly tied to the topic than one that turns up under one. So every keyword
 * picked pulls in its own neighbours, and the suggestions sharpen as the topic grows —
 * a chain reaction built from today's coverage, not a fixed list.
 */
object TopicGraph {

    /** One headline found for a keyword. */
    data class Headline(val title: String, val source: String?, val domain: String?)

    /** An outlet that covers the topic: how strongly, and how many of the stories it carried. */
    data class Outlet(val name: String, val domain: String, val score: Double, val hits: Int)

    data class Suggestions(val keywords: List<String>, val outlets: List<Outlet>)

    /**
     * @param seeds headlines per seed (the topic's name and every keyword so far).
     * @param chosen keywords already in the topic (never suggested again), any case.
     */
    fun suggest(seeds: Map<String, List<Headline>>, chosen: Collection<String>, keywordLimit: Int = 12, outletLimit: Int = 8): Suggestions {
        val have = (chosen + seeds.keys).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val own = have.flatMap { it.split(WORD) }.filter { it.length > 1 }.toSet()
        val termScore = HashMap<String, Double>()
        val termSpread = HashMap<String, Int>()
        val termLabel = HashMap<String, String>()
        val outScore = HashMap<String, Double>()
        val outSpread = HashMap<String, Int>()
        val outHits = HashMap<String, Int>()
        val outName = HashMap<String, String>()
        for ((_, heads) in seeds) {
            if (heads.isEmpty()) continue
            val n = heads.size.toDouble()
            // Document frequency within this seed's headlines.
            val df = HashMap<String, Int>()
            val perOutlet = HashMap<String, Int>()
            for (h in heads) {
                KeywordMiner.terms(h.title, own).forEach { t ->
                    val k = t.lowercase()
                    df[k] = (df[k] ?: 0) + 1
                    termLabel.putIfAbsent(k, t)
                }
                val d = h.domain?.lowercase()?.removePrefix("www.")
                if (!d.isNullOrBlank()) {
                    perOutlet[d] = (perOutlet[d] ?: 0) + 1
                    h.source?.takeIf { it.isNotBlank() }?.let { outName.putIfAbsent(d, it) }
                }
            }
            df.forEach { (k, c) -> termScore[k] = (termScore[k] ?: 0.0) + c / n; termSpread[k] = (termSpread[k] ?: 0) + 1 }
            perOutlet.forEach { (d, c) ->
                outScore[d] = (outScore[d] ?: 0.0) + c / n
                outSpread[d] = (outSpread[d] ?: 0) + 1
                outHits[d] = (outHits[d] ?: 0) + c
            }
        }
        val total = seeds.values.sumOf { it.size }
        val minDf = if (total >= 20) 2 else 1
        val outletNames = outName.values.map { it.lowercase() }.toSet()
        val keywords = termScore.entries
            .filter { (k, _) ->
                k !in have && have.none { h -> k.contains(h) || h.contains(k) } && k !in outletNames &&
                    // A short single word ("Pro") or a contraction ("Here's") says nothing on its own; acronyms (RAM, NBA) do.
                    !(' ' !in k && k.length <= 3 && termLabel[k]?.all { it.isUpperCase() || it.isDigit() } != true) &&
                    !(' ' !in k && (k.endsWith("'s") || k.endsWith("’s"))) &&
                    countOf(k, seeds) >= minDf
            }
            .sortedByDescending { (k, v) -> v * (1 + 0.6 * ((termSpread[k] ?: 1) - 1)) + if (k.contains(' ')) 0.05 else 0.0 }
            .map { termLabel[it.key] ?: it.key }
            .distinctBy { it.lowercase() }
            .take(keywordLimit)
        val outlets = outScore.entries
            .filter { (outHits[it.key] ?: 0) >= 2 }
            .map { (d, v) -> Outlet(outName[d] ?: d, d, v * (1 + 0.6 * ((outSpread[d] ?: 1) - 1)), outHits[d] ?: 0) }
            .sortedByDescending { it.score }
            .take(outletLimit)
        return Suggestions(keywords, outlets)
    }

    private fun countOf(k: String, seeds: Map<String, List<Headline>>): Int =
        seeds.values.sumOf { heads -> heads.count { h -> h.title.lowercase().contains(k) } }

    private val WORD = Regex("[^\\p{L}\\p{N}]+")
}
