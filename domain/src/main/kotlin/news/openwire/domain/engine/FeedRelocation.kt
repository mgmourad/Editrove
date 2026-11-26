// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/**
 * Finding where a feed moved to. When a site retires a feed address, its pages still list its
 * feeds (a homepage `<link rel=alternate>`, or an "RSS" directory page of links); the new address
 * for the same section shares the old one's distinctive words ("rss-canada-britishcolumbia").
 */
object FeedRelocation {
    private val NOISE = setOf("rss", "rss2", "feed", "feeds", "xml", "atom", "cmlink", "webfeed", "index", "www", "http", "https", "com", "default", "posts", "news", "php", "html")
    private val SPLIT = Regex("[^a-z0-9]+")
    private val LINK = Regex("<link[^>]+type=[\"']application/(?:rss|atom)\\+xml[\"'][^>]*>", RegexOption.IGNORE_CASE)
    private val HREF = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val ANCHOR = Regex("<a[^>]+href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val FEEDISH = Regex("(?i)(/rss|/feed|/atom|\\.rss\\b|\\.xml\\b|[?&]feed=|webfeed)")

    /** Pages that commonly list a site's feeds, after the homepage and the feed's own folder. */
    val DIRECTORY_PATHS = listOf("/rss", "/rss/", "/feeds", "/feeds/", "/rss-feeds", "/services/rss", "/about/rss", "/help/rss")

    /** The distinctive words of a feed address's path. */
    fun words(url: String): Set<String> {
        val path = url.substringAfter("://").substringAfter('/', "").substringBefore('#').lowercase()
        return path.split(SPLIT).filter { it.length > 1 && it !in NOISE && !it.all(Char::isDigit) }.toSet()
    }

    /** Every feed-looking link on a page (advertised alternates first), absolute, same site only. */
    fun feedLinks(html: String, pageUrl: String): List<String> {
        val scheme = pageUrl.substringBefore("://", "https")
        val host = pageUrl.substringAfter("://").substringBefore('/')
        val dir = pageUrl.substringAfter("://").substringAfter('/', "").substringBeforeLast('/', "")
        fun abs(h: String): String? {
            val v = h.trim().replace("&amp;", "&")
            return when {
                v.startsWith("http://") || v.startsWith("https://") -> v
                v.startsWith("//") -> "$scheme:$v"
                v.startsWith("/") -> "$scheme://$host$v"
                v.isEmpty() || v.startsWith("#") || v.startsWith("mailto:") || v.startsWith("javascript:") -> null
                else -> "$scheme://$host/${if (dir.isEmpty()) "" else "$dir/"}$v"
            }
        }
        val site = siteOf(host)
        val alternates = LINK.findAll(html).mapNotNull { HREF.find(it.value)?.groupValues?.get(1) }.mapNotNull(::abs)
        val anchors = ANCHOR.findAll(html).map { it.groupValues[1] }.filter { FEEDISH.containsMatchIn(it) }.mapNotNull(::abs)
        return (alternates + anchors).filter { siteOf(it.substringAfter("://").substringBefore('/')) == site }.distinct().toList()
    }

    /**
     * The candidates most likely to be the same feed, best first: the most shared words with the
     * old address (all of them, ideally), then the fewest extra words. A feed with nothing
     * distinctive in its address ("/rss") takes the site's main feed.
     */
    fun rank(oldUrl: String, candidates: List<String>): List<String> {
        val old = words(oldUrl)
        val scored = candidates.distinct().filter { it != oldUrl }.map { c ->
            val w = words(c)
            Triple(c, (old intersect w).size, (w - old).size)
        }
        if (old.isEmpty()) return scored.sortedBy { it.third }.map { it.first }
        return scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third }.thenBy { it.first.length })
            .map { it.first }
    }

    private fun siteOf(host: String): String = host.lowercase().removePrefix("www.").split('.').takeLast(2).joinToString(".")
}
