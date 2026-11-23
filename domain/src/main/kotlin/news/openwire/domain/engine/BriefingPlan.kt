// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article

/**
 * Puts a briefing's stories in an order a presenter can tell: duplicate coverage of one
 * story is merged (the other outlets are named, not read again), and stories on the same
 * subject are brought together so the presenter can connect them.
 */
object BriefingPlan {

    /** A story to read, with the outlets also carrying it and whether it follows on from the one before. */
    data class Item(val article: Article, val alsoReportedBy: List<String>, val relatedToPrevious: Boolean)

    /**
     * @param breaking a story labelled breaking leads the briefing (the presenter opens with it).
     */
    fun arrange(articles: List<Article>, count: Int, normalizeDomain: (String) -> String, breaking: (Article) -> Boolean = { false }, reserve: Int = 0): List<Item> {
        if (count <= 0) return emptyList()
        class Cluster(val lead: Article) { val others = LinkedHashSet<String>() }
        val clusters = ArrayList<Cluster>()
        for (a in articles) {
            val home = clusters.firstOrNull { StoryIdentity.sameStory(it.lead, a, normalizeDomain) }
            if (home != null) {
                val name = a.source ?: a.domain
                if (name != null && !name.equals(home.lead.source, ignoreCase = true) &&
                    normalizeDomain(a.domain ?: "") != normalizeDomain(home.lead.domain ?: "-")
                ) home.others += name
            } else if (clusters.size < count + reserve) clusters += Cluster(a)
        }
        if (clusters.isEmpty()) return emptyList()
        // Listening order: the most widely covered story leads (the feed's own lead is picked
        // for its photo, and its near-duplicates are spread apart: neither matters by ear).
        val position = clusters.withIndex().associate { it.value to it.index }
        clusters.sortByDescending { c ->
            val outlets = 1 + c.others.size + maxOf(c.lead.alsoCoveredBy.size, (c.lead.coverage ?: 1) - 1, 0)
            2.0 * kotlin.math.ln(outlets.toDouble()) - 0.12 * (position[c] ?: 0)
        }
        clusters.indexOfFirst { breaking(it.lead) }.takeIf { it > 0 }?.let { clusters.add(0, clusters.removeAt(it)) }
        // The lead story stays first; each next slot prefers a story on the same subject.
        val out = ArrayList<Item>(clusters.size)
        val left = clusters.drop(1).toMutableList()
        out += Item(clusters[0].lead, clusters[0].others.toList(), false)
        while (left.isNotEmpty()) {
            val prev = out.last().article
            val next = left.firstOrNull { related(prev, it.lead) }
            val c = next ?: left.first()
            left.remove(c)
            out += Item(c.lead, c.others.toList(), next != null)
        }
        return out
    }

    /**
     * Two different stories about the same subject: their headlines share two or more
     * distinctive words ("Iran" and "strikes"; "Anthropic" and "IPO") — not just one name.
     */
    fun related(a: Article, b: Article): Boolean {
        val wa = words(a.title)
        if (wa.size < 2) return false
        return wa.intersect(words(b.title)).size >= 2
    }

    private fun words(t: String): Set<String> =
        RX_WORD.findAll(t.lowercase()).map { it.value }.filter { it.length >= 4 && it !in COMMON }.toSet()

    /** Section a topic's stories belong to, for "In business news." and the like. */
    fun categoryOf(topicId: String): BriefingScript.Category? = when (topicId) {
        "POLITICS" -> BriefingScript.Category.POLITICS
        "BUSINESS", "FINANCE" -> BriefingScript.Category.BUSINESS
        "TECHNOLOGY", "AI" -> BriefingScript.Category.TECHNOLOGY
        "SCIENCE" -> BriefingScript.Category.SCIENCE
        "LIFESTYLE" -> BriefingScript.Category.HEALTH
        "SPORTS" -> BriefingScript.Category.SPORTS
        "ENTERTAINMENT" -> BriefingScript.Category.ENTERTAINMENT
        "INTERNATIONAL" -> BriefingScript.Category.WORLD
        else -> null
    }

    /** Local, national or international, from the edition's own tabs. */
    fun scopeOf(topicId: String): BriefingScript.Scope? = when (topicId) {
        TopicRegistry.LOCAL_ID -> BriefingScript.Scope.LOCAL
        TopicRegistry.NATIONAL_ID -> BriefingScript.Scope.NATIONAL
        "INTERNATIONAL" -> BriefingScript.Scope.INTERNATIONAL
        else -> null
    }

    /** Labelled "BREAKING" by its newsroom within the last two hours. */
    fun isBreaking(a: Article, nowMillis: Long): Boolean = BreakingNews.reason(a, nowMillis) == "labelled breaking" && !isDeveloping(a)

    /** Labelled "Developing" by its newsroom. */
    fun isDeveloping(a: Article): Boolean = RX_DEVELOPING.containsMatchIn(a.title)

    private val RX_WORD = Regex("\\p{L}+")
    private val RX_DEVELOPING = Regex("^\\s*(developing( story)?|en développement|en desarrollo|eilmeldung)\\b", RegexOption.IGNORE_CASE)
    private val COMMON = setOf(
        "that", "this", "with", "from", "have", "they", "their", "about", "after", "before", "over", "under", "what", "when",
        "where", "which", "while", "will", "would", "could", "should", "were", "been", "more", "most", "than", "then", "into",
        "says", "said", "amid", "other", "some", "just", "also", "only", "here", "these", "those", "new", "news", "first", "last",
        "year", "years", "week", "today", "people", "report", "reports", "breaking", "live", "update", "updates",
        "dans", "pour", "avec", "sont", "cette", "plus", "para", "como", "pero", "sobre", "esta", "este", "eine", "einer", "nicht", "sich", "auch", "wird",
    )
}
