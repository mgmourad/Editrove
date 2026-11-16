// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Article

/**
 * "Is this the same story?" — beyond near-identical headlines. Two outlets often frame one
 * event very differently ("Trump heads to China" vs "Why President Xi is meeting Trump"),
 * so besides wording this compares who and where the headlines are about, with leaders
 * and capitals folded into their country (Xi → China, Kremlin → Russia).
 */
object StoryIdentity {

    private val ALIASES: Map<String, String> = buildMap {
        fun to(target: String, vararg keys: String) = keys.forEach { put(it, target) }
        to("china", "china", "chinese", "xi", "jinping", "beijing")
        to("russia", "russia", "russian", "putin", "kremlin", "moscow")
        to("ukraine", "ukraine", "ukrainian", "zelensky", "zelenskyy", "kyiv", "kiev")
        to("israel", "israel", "israeli", "netanyahu", "idf", "jerusalem", "tel")
        to("gaza", "gaza", "hamas", "palestinian", "palestinians")
        to("iran", "iran", "iranian", "tehran", "khamenei", "irgc")
        to("india", "india", "indian", "modi", "delhi")
        to("uk", "uk", "britain", "british", "starmer", "london", "downing")
        to("north korea", "pyongyang", "kim")
        to("japan", "japan", "japanese", "tokyo")
        to("france", "france", "french", "macron", "paris")
        to("germany", "germany", "german", "berlin", "merz")
        to("canada", "canada", "canadian", "carney", "ottawa")
        to("mexico", "mexico", "mexican", "sheinbaum")
        to("saudi", "saudi", "riyadh", "mbs")
        to("trump", "trump", "trump's", "trump’s")
        to("nvidia", "nvidia", "huang")
        to("fed", "fed", "powell", "federal reserve")
    }

    /** Names in so many headlines that, alone, they don't make two stories the same. */
    private val WEAK = setOf("trump", "white", "house", "congress", "senate", "gop", "democrats", "republicans", "fed", "washington", "biden")

    /** Words that are capitalised in headlines but don't identify a story. */
    private val GENERIC = setOf(
        "us", "u.s", "u.s.", "america", "american", "americans", "president", "the", "a", "new", "breaking", "live", "watch", "report",
        "why", "how", "what", "who", "when", "where", "after", "as", "in", "on", "at", "for", "to", "of", "and", "is", "are", "says",
        "exclusive", "analysis", "opinion", "video", "photos", "update", "updates", "state", "states", "united", "government", "officials",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "today", "week", "year", "first", "here", "this",
    )

    /** Named entities in a headline (folded through [ALIASES]). */
    fun entities(title: String): Set<String> {
        val clean = title.substringBeforeLast(" - ")
        val tokens = clean.split(SPLIT).filter { it.isNotEmpty() }
        val out = HashSet<String>()
        tokens.forEachIndexed { i, raw ->
            val w = raw.trim('\'', '’', '"', '“', '”', '.', ',', ':', ';', '(', ')')
            if (w.length < 2) return@forEachIndexed
            val lower = w.lowercase()
            ALIASES[lower]?.let { out += it; return@forEachIndexed }
            if (lower in GENERIC) return@forEachIndexed
            val capital = w.first().isUpperCase()
            val acronym = w.length in 2..6 && w.all { it.isUpperCase() || it.isDigit() }
            // The first word is capitalised anyway; only trust it when it's clearly a name.
            if ((capital && (i > 0 || w.length > 4)) || acronym) out += lower
        }
        return out
    }

    /** True when two articles are most likely the same news story. */
    fun sameStory(a: Article, b: Article, normalizeDomain: (String) -> String): Boolean {
        if (FeedWeaver.isDuplicate(a, b, normalizeDomain)) return true
        if (FeedWeaver.calculateTitleSimilarity(a.title, b.title) >= 0.4) return true
        val ea = entities(a.title); val eb = entities(b.title)
        val shared = ea.intersect(eb)
        // Two shared names, at least one specific (Trump + White House alone is half the news).
        if (shared.size >= 2 && shared.any { it !in WEAK }) return true
        // Or a name plus two more of the same key words ("Fed holds rates" / "Powell: Fed may hold rates").
        val words = stems(a.title).intersect(stems(b.title)) - ea - eb - WEAK
        return shared.isNotEmpty() && words.size >= 2
    }

    private val STOP = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "over", "after", "amid", "says", "said", "will", "may", "could",
        "would", "about", "what", "why", "how", "who", "its", "his", "her", "their", "new", "more", "than", "are", "was", "were", "has", "have",
    )

    /** Content words, lightly stemmed (holds → hold, rates → rate), names folded via [ALIASES]. */
    private fun stems(title: String): Set<String> = title.substringBeforeLast(" - ").lowercase().split(SPLIT).mapNotNull { raw ->
        val w = raw.trim('\'', '’', '"', '“', '”', '.', ',', ':', ';', '(', ')', '?', '!')
        if (w.length < 3 || w in STOP) null else ALIASES[w] ?: w.removeSuffix("'s").removeSuffix("’s").let { if (it.length > 4 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
    }.toSet()


    /**
     * The first [count] stories that are each about something different, keeping the
     * given (curated) order — for audio briefings, where a repeat is obvious.
     */
    fun distinct(articles: List<Article>, count: Int, normalizeDomain: (String) -> String): List<Article> {
        val out = ArrayList<Article>(count)
        for (a in articles) {
            if (out.size >= count) break
            if (out.none { sameStory(it, a, normalizeDomain) }) out += a
        }
        return out
    }
}

private val SPLIT = Regex("[\\s/—–-]+")
