// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import kotlin.math.exp
import kotlin.math.ln
import news.openwire.domain.model.Article

/**
 * Editorial ranking for "Curated" feeds — the blend the big news apps use:
 *
 * 1. **Prominence** — where a professional desk placed the story (Google News order /
 *    the published snapshot order).
 * 2. **Coverage** — how many independent outlets are carrying the same story
 *    (clusters across every loaded topic, plus Google's "full coverage" hint).
 * 3. **Freshness** — a smooth half-life decay instead of newest-first.
 * 4. **Authority** — wire services and major newsrooms over aggregators and blogs.
 * 5. **Section fit** — hard news leads Top Stories; sport, celebrity, shopping and
 *    lifestyle are held back there unless they are genuinely everywhere.
 * 6. **Diversity** — maximal-marginal-relevance re-ranking so near-duplicate
 *    stories and one outlet don't bunch together; the lead story has a photo.
 *
 * Everything runs on-device; the optional affinity boost uses only the reader's own
 * saved/liked stories.
 */
object FeedRanker {

    data class Context(
        val topicId: String,
        val nowMillis: Long,
        /** Normalised domain → number of distinct outlets carrying a similar story across all loaded feeds. */
        val coverageOf: (Article) -> Int = { 1 },
        /** Feed URL → catalog category (e.g. "Sports"), for direct-feed stories. */
        val feedCategory: (String) -> String? = { null },
        /** Domain → 0..1 affinity from the reader's saves/likes. */
        val affinity: (String) -> Double = { 0.0 },
        val normalizeDomain: (String) -> String = { it },
    )

    enum class Kind { HARD, SPORTS, ENTERTAINMENT, LIFESTYLE, SHOPPING, OTHER }

    data class Scored(val article: Article, val score: Double, val kind: Kind, val coverage: Int) {
        // Computed once per story: diversification compares every pair, so these must not be re-derived.
        internal val outlet: String get() = outletOf ?: ""
        internal var outletOf: String? = null
        internal val words: Set<String> by lazy { similarityWords(article.title) }
    }

    /** The words [FeedWeaver.calculateTitleSimilarity] compares (longer than two letters). */
    internal fun similarityWords(title: String): Set<String> =
        FeedWeaver.normalizeTitleForComparison(title).split(' ').filter { it.length > 2 }.toSet()

    /** Jaccard similarity on precomputed word sets; same result as [FeedWeaver.calculateTitleSimilarity]. */
    internal fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val (small, big) = if (a.size <= b.size) a to b else b to a
        val inter = small.count { it in big }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    // ---- Authority -------------------------------------------------------------------

    private val TIER1 = setOf(
        "apnews.com", "reuters.com", "bbc.co.uk", "bbc.com", "nytimes.com", "washingtonpost.com", "wsj.com", "ft.com",
        "bloomberg.com", "npr.org", "theguardian.com", "economist.com", "cnn.com", "nbcnews.com", "cbsnews.com",
        "abcnews.go.com", "politico.com", "axios.com", "aljazeera.com", "pbs.org", "cbc.ca", "latimes.com",
    )
    private val TIER2 = setOf(
        "usatoday.com", "thehill.com", "time.com", "theatlantic.com", "newyorker.com", "foxnews.com", "cnbc.com",
        "nature.com", "scientificamerican.com", "techcrunch.com", "theverge.com", "arstechnica.com", "wired.com",
        "espn.com", "france24.com", "dw.com", "independent.co.uk", "news.sky.com", "forbes.com", "businessinsider.com",
        "globalnews.ca", "newsweek.com", "technologyreview.com", "marketwatch.com", "fortune.com", "vox.com",
    )

    fun authority(domain: String?): Double = when (domain) {
        null, "" -> 0.45
        in TIER1 -> 1.0
        in TIER2 -> 0.78
        else -> 0.55
    }

    // ---- Section fit --------------------------------------------------------------------

    private val SPORTS_PATH = Regex("/(sports?|nfl|nba|mlb|nhl|mls|soccer|football|cricket|golf|tennis|f1|motorsport|ncaa|olympics|cfl)(/|$)")
    private val SPORTS_TITLE = Regex(
        "\\b(NFL|NBA|MLB|NHL|MLS|CFL|NCAA|Premier League|Champions League|Grey Cup|Super Bowl|World Series|Stanley Cup|playoffs?|quarterback|touchdown|" +
            "head coach|roster|lineup|innings?|halftime|vs\\.?|game-time decision|draft pick|free agent|Raptors|Knicks|Lakers|Yankees|Cowboys)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val ENT_PATH = Regex("/(entertainment|celebrity|celebrities|movies?|tv|music|arts|culture|style|fashion)(/|$)")
    private val ENT_TITLE = Regex("\\b(box office|red carpet|Oscars?|Grammys?|Emmys?|album|celebrity|reality star|SNL|Taylor Swift|Kardashian|trailer)\\b", RegexOption.IGNORE_CASE)
    private val LIFE_PATH = Regex("/(lifestyle|life|food|recipes?|travel|home|wellness|horoscopes?|astrology|parenting)(/|$)")
    private val LIFE_TITLE = Regex("\\b(recipe|horoscope|zodiac|wellness|skincare|workout|dating)\\b", RegexOption.IGNORE_CASE)
    private val SHOP_TITLE = Regex("\\b(deals?|discount|% off|sale|best .{0,20} to buy|coupon|Black Friday|Cyber Monday|Prime Day|review:)\\b", RegexOption.IGNORE_CASE)
    private val HARD_TITLE = Regex(
        "\\b(Trump|Biden|White House|Congress|Senate|House|election|vote|court|judge|ruling|war|military|missile|strike|attack|killed|dead|" +
            "Iran|Israel|Gaza|Ukraine|Russia|China|NATO|tariffs?|economy|inflation|Fed|markets?|minister|president|government|police|shooting|" +
            "earthquake|hurricane|wildfire|flood|storm|crash|collision|investigation|indicted|sanctions|treaty|summit|protest)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun kind(a: Article, feedCategory: String?): Kind {
        val path = runCatching { java.net.URI(a.originalUrl ?: "").path.orEmpty().lowercase() }.getOrDefault("")
        val t = a.title
        return when {
            feedCategory == "Sports" || SPORTS_PATH.containsMatchIn(path) || SPORTS_TITLE.containsMatchIn(t) -> Kind.SPORTS
            SHOP_TITLE.containsMatchIn(t) -> Kind.SHOPPING
            feedCategory == "Entertainment" || ENT_PATH.containsMatchIn(path) || ENT_TITLE.containsMatchIn(t) -> Kind.ENTERTAINMENT
            feedCategory == "Lifestyle" || LIFE_PATH.containsMatchIn(path) || LIFE_TITLE.containsMatchIn(t) -> Kind.LIFESTYLE
            HARD_TITLE.containsMatchIn(t) -> Kind.HARD
            else -> Kind.OTHER
        }
    }

    /** Topics that are a "front page": hard news should lead. */
    private val FRONT_PAGES = setOf("TOP", "US", "WASHINGTON_DC", "INTERNATIONAL", "POLITICS", EVERYWHERE)

    /** Pseudo-topic for Discover's cross-edition front page. */
    const val EVERYWHERE = "EVERYWHERE"

    private fun sectionFit(topicId: String, kind: Kind, coverage: Int): Double {
        if (topicId !in FRONT_PAGES) return 0.0
        val everywhere = coverage >= 6 // a soft story every outlet is running still earns its place
        return when (kind) {
            Kind.HARD -> 0.08
            Kind.SPORTS, Kind.ENTERTAINMENT -> if (everywhere) -0.05 else -0.22
            Kind.LIFESTYLE -> -0.18
            Kind.SHOPPING -> -0.35
            Kind.OTHER -> 0.0
        }
    }

    /** Hours for a story to lose half its freshness. */
    private fun halfLifeHours(topicId: String) = when (topicId) {
        "TOP", "US", "WASHINGTON_DC", "POLITICS", "INTERNATIONAL" -> 10.0
        "BUSINESS", "FINANCE", "TECHNOLOGY", "AI", "SPORTS" -> 16.0
        "SCIENCE", "ENTERTAINMENT" -> 30.0
        "LIFESTYLE" -> 48.0
        else -> 18.0
    }

    // ---- Ranking -------------------------------------------------------------------------

    fun score(articles: List<Article>, ctx: Context): List<Scored> {
        val hl = halfLifeHours(ctx.topicId)
        return articles.mapIndexed { i, a ->
            val rank = a.editorialRank ?: i
            val prominence = 1.0 / (1.0 + rank / 6.0)
            val coverage = maxOf(ctx.coverageOf(a), a.coverage ?: 1)
            val cov = (ln(1.0 + coverage) / ln(1.0 + 10.0)).coerceIn(0.0, 1.0)
            val ageH = a.publishedAt?.let { ((ctx.nowMillis - it) / 3_600_000.0).coerceAtLeast(0.0) } ?: (hl * 1.5)
            val fresh = exp(-ageH * ln(2.0) / hl)
            val domain = ctx.normalizeDomain(a.domain ?: a.source ?: "")
            val kind = kind(a, a.feedUrl?.let(ctx.feedCategory))
            val s = 0.33 * prominence +
                0.25 * cov +
                0.22 * fresh +
                0.12 * authority(domain) +
                (if (a.imageUrl != null) 0.04 else 0.0) +
                0.06 * ctx.affinity(domain).coerceIn(0.0, 1.0) +
                sectionFit(ctx.topicId, kind, coverage) -
                (if (a.status == "locked") 0.1 else 0.0)
            Scored(a, s, kind, coverage).also { it.outletOf = domain }
        }
    }

    /**
     * Ranked feed. "LATEST" stays strictly chronological (that's its promise); every
     * other topic gets scored, then diversified with maximal marginal relevance.
     */
    fun rank(articles: List<Article>, ctx: Context): List<Article> {
        if (articles.size < 2) return articles
        if (ctx.topicId == "LATEST") return articles.sortedByDescending { it.publishedAt ?: 0L }
        return diversify(score(articles, ctx), ctx).map { it.article }
    }

    /**
     * Discover's "Top Across Editions": every loaded topic (each already in curated order)
     * merged into one front page. A story's prominence is its best placement in any topic;
     * the same story from several topics/outlets is shown once and counts as coverage.
     */
    fun everywhere(feeds: Map<String, List<Article>>, nowMillis: Long, normalizeDomain: (String) -> String, limit: Int = 60): List<Article> {
        val all = feeds.values.flatten()
        if (all.isEmpty()) return emptyList()
        val coverage = coverageIndex(all, normalizeDomain)
        val best = LinkedHashMap<String, Article>()
        feeds.filterKeys { it != "LATEST" }.values.forEach { list ->
            list.forEachIndexed { i, a ->
                val key = FeedOrdering.storyKey(a)
                val prev = best[key]
                if (prev == null || i < (prev.editorialRank ?: Int.MAX_VALUE)) {
                    best[key] = a.copy(editorialRank = i, imageUrl = a.imageUrl ?: prev?.imageUrl, summary = a.summary ?: prev?.summary)
                }
            }
        }
        // Collapse the same story told by different topics under different URLs.
        val unique = ArrayList<Article>()
        val seen = ArrayList<Pair<Set<String>, String>>()
        best.values.sortedBy { it.editorialRank }.take(limit * 4).forEach { a ->
            val w = similarityWords(a.title)
            val d = normalizeDomain(a.domain ?: a.source ?: "")
            // Same rule as FeedWeaver.isDuplicate (URLs were already merged via storyKey).
            val dup = seen.any { (ow, od) -> val sim = jaccard(w, ow); sim >= 0.75 || (d.isNotEmpty() && d == od && sim >= 0.25) }
            if (!dup) { unique += a; seen += w to d }
        }
        val ctx = Context(EVERYWHERE, nowMillis, coverageOf = coverage, normalizeDomain = normalizeDomain)
        // Score everything, keep the strongest, then diversify that slate.
        val top = score(unique, ctx).sortedByDescending { it.score }.take(limit)
        return diversify(top, ctx).map { it.article }
    }

    /** Greedy MMR: best remaining story, minus a penalty for resembling what's already placed. */
    fun diversify(scored: List<Scored>, ctx: Context, lambda: Double = 0.18): List<Scored> {
        val pool = scored.toMutableList()
        val out = ArrayList<Scored>(scored.size)
        // Lead: the best story among the top few that has a photo for the cover.
        val lead = pool.sortedByDescending { it.score }.take(4).firstOrNull { it.article.imageUrl != null } ?: pool.maxBy { it.score }
        out += lead; pool.remove(lead)
        while (pool.isNotEmpty()) {
            val recent = out.takeLast(6)
            val lastTwo = out.takeLast(2)
            val next = pool.maxBy { c ->
                val sim = recent.maxOf { jaccard(it.words, c.words) }
                val sameOutlet = lastTwo.count { it.outlet == c.outlet }
                val sameKind = if (c.kind != Kind.HARD && lastTwo.all { it.kind == c.kind }) 0.06 else 0.0
                c.score - lambda * sim - 0.08 * sameOutlet - sameKind
            }
            out += next; pool.remove(next)
        }
        return out
    }

    /**
     * Story clusters across a set of feeds: for each article, how many distinct outlets
     * carry a similar story. Cheap token-signature pre-grouping keeps this near-linear.
     */
    fun coverageIndex(all: List<Article>, normalizeDomain: (String) -> String): (Article) -> Int {
        val related = relatedCoverage(all, normalizeDomain)
        return { a -> (related(a).size + 1).coerceAtLeast(1) }
    }

    /** One other outlet's take on the same story. */
    data class Related(val domain: String, val source: String)

    /**
     * Story clusters across a set of feeds: for each article, the *other* outlets carrying
     * a similar story (one entry per outlet). Distinctive-token pre-grouping keeps this
     * near-linear; words that appear in many headlines don't count as evidence.
     */
    fun relatedCoverage(all: List<Article>, normalizeDomain: (String) -> String): (Article) -> List<Related> {
        fun tokens(title: String) = FeedWeaver.normalizeTitleForComparison(title).split(' ').filter { it.length > 3 }.toSet()
        val sig = all.map { it to tokens(it.title) }
        val words = all.map { similarityWords(it.title) }
        val domains = all.map { normalizeDomain(it.domain ?: it.source ?: "") }
        val byToken = HashMap<String, MutableList<Int>>()
        sig.forEachIndexed { i, (_, toks) -> toks.forEach { byToken.getOrPut(it) { mutableListOf() }.add(i) } }
        val common = maxOf(8, all.size / 20)
        val cache = HashMap<String, List<Related>>()
        return { a ->
            cache.getOrPut(a.id + "|" + a.title) {
                val own = normalizeDomain(a.domain ?: a.source ?: "")
                val mine = similarityWords(a.title)
                val toks = tokens(a.title).filter { (byToken[it]?.size ?: 0) <= common }
                val shared = toks.flatMap { byToken[it].orEmpty() }.groupingBy { it }.eachCount()
                val out = LinkedHashMap<String, Related>()
                shared.entries.sortedByDescending { it.value }.forEach { (j, n) ->
                    if (n < 2) return@forEach
                    val other = sig[j].first
                    val d = domains[j]
                    if (d.isEmpty() || d == own || d in out) return@forEach
                    if (n >= 3 || jaccard(mine, words[j]) >= 0.25) {
                        out[d] = Related(d, other.source?.takeIf { it.isNotBlank() } ?: d)
                    }
                }
                out.values.toList()
            }
        }
    }

    /**
     * On-device interest: how much the reader favours an outlet, from the outlets of the
     * stories they saved and liked. Needs at least two signals for an outlet to count, and
     * saturates so a single favourite can't take over the front page.
     */
    fun affinityFrom(interestDomains: List<String>, normalizeDomain: (String) -> String): (String) -> Double {
        if (interestDomains.size < 2) return { 0.0 }
        val counts = interestDomains.map(normalizeDomain).filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
        val top = counts.values.maxOrNull() ?: return { 0.0 }
        return { d -> counts[d]?.takeIf { it >= 2 }?.let { (it.toDouble() / top).coerceIn(0.0, 1.0) } ?: 0.0 }
    }
}
