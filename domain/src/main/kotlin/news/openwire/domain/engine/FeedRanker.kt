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

    data class Scored(val article: Article, val score: Double, val kind: Kind, val coverage: Int)

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
            Scored(a, s, kind, coverage)
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
        best.values.sortedBy { it.editorialRank }.take(limit * 4).forEach { a -> if (unique.none { FeedWeaver.isDuplicate(it, a, normalizeDomain) }) unique += a }
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
            val next = pool.maxBy { c ->
                val sim = recent.maxOf { FeedWeaver.calculateTitleSimilarity(it.article.title, c.article.title) }
                val d = ctx.normalizeDomain(c.article.domain ?: c.article.source ?: "")
                val sameOutlet = out.takeLast(2).count { ctx.normalizeDomain(it.article.domain ?: it.article.source ?: "") == d }
                val sameKind = if (c.kind != Kind.HARD && out.takeLast(2).all { it.kind == c.kind }) 0.06 else 0.0
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
        fun tokens(title: String) = FeedWeaver.normalizeTitleForComparison(title).split(' ').filter { it.length > 3 }.toSet()
        val sig = all.map { it to tokens(it.title) }
        val byToken = HashMap<String, MutableList<Int>>()
        sig.forEachIndexed { i, (_, toks) -> toks.forEach { byToken.getOrPut(it) { mutableListOf() }.add(i) } }
        // Words in a large share of headlines ("says", "after", "trump") don't identify a story.
        val common = maxOf(8, all.size / 20)
        val cache = HashMap<String, Int>()
        return { a ->
            cache.getOrPut(a.id + "|" + a.title) {
                val toks = tokens(a.title).filter { (byToken[it]?.size ?: 0) <= common }
                val shared = toks.flatMap { byToken[it].orEmpty() }.groupingBy { it }.eachCount()
                val outlets = HashSet<String>()
                outlets += normalizeDomain(a.domain ?: a.source ?: "")
                shared.forEach { (j, n) ->
                    if (n < 2) return@forEach
                    val other = sig[j].first
                    if (n >= 3 || FeedWeaver.calculateTitleSimilarity(a.title, other.title) >= 0.25) {
                        outlets += normalizeDomain(other.domain ?: other.source ?: "")
                    }
                }
                outlets.count { it.isNotEmpty() }.coerceAtLeast(1)
            }
        }
    }
}
