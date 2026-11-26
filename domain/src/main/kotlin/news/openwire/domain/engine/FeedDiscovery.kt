// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Finding a news source by its name or website, for readers who've never heard of RSS:
 * candidates come from Feedly's public feed index, from Google News (which knows each
 * outlet's real website) and from the site itself; the app then checks each one live.
 */
object FeedDiscovery {

    /** A candidate feed from Feedly's index (often stale: always checked before it's offered). */
    data class Indexed(val url: String, val title: String, val website: String?, val subscribers: Long, val icon: String?)

    /** Addresses sites commonly publish their feed at, tried when a homepage advertises none. */
    val COMMON_PATHS = listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/index.xml", "/atom.xml", "/feeds/posts/default", "/?feed=rss2")

    fun feedlyUrl(query: String, language: String, count: Int = 20) =
        "https://cloud.feedly.com/v3/search/feeds?query=${java.net.URLEncoder.encode(query.trim(), "UTF-8")}&count=$count&locale=$language"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseFeedly(body: String): List<Indexed> {
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"] as? JsonArray }.getOrNull() ?: return emptyList()
        return results.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val url = s("feedId")?.removePrefix("feed/") ?: return@mapNotNull null
            if (!url.startsWith("http") || "youtube.com" in url || "gdata.youtube" in url) return@mapNotNull null
            Indexed(
                url = url,
                title = s("title") ?: return@mapNotNull null,
                website = s("website"),
                subscribers = o["subscribers"]?.jsonPrimitive?.longOrNull ?: 0L,
                icon = s("iconUrl") ?: s("visualUrl"),
            )
        }
    }

    /** True for "youm7.com", "https://www.ahram.org.eg/…"; false for "Al-Ahram". */
    fun looksLikeSite(q: String): Boolean {
        val t = q.trim()
        return t.startsWith("http://") || t.startsWith("https://") || (' ' !in t && RX_DOMAIN.matches(t.substringBefore('/')))
    }

    /** "https://www.youm7.com/x" → "youm7.com". */
    fun domainOf(q: String): String? {
        val t = q.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore('?').lowercase()
        return t.removePrefix("www.").removePrefix("m.").takeIf { RX_DOMAIN.matches(it) }
    }

    private val STOPS = mapOf(
        "en" to setOf("the", "and", "of", "to", "in", "for", "on", "with", "is", "says", "after", "over"),
        "fr" to setOf("le", "la", "les", "des", "du", "et", "pour", "dans", "une", "sur", "au", "aux"),
        "es" to setOf("el", "los", "las", "del", "y", "para", "con", "una", "por", "que", "en", "se"),
        "de" to setOf("der", "die", "das", "und", "mit", "für", "von", "den", "ist", "ein", "im", "auf"),
    )

    /** The language headlines are written in (Editrove's five), or null when unclear. */
    fun detectLanguage(titles: List<String>): String? {
        if (titles.isEmpty()) return null
        val arabic = titles.count { t -> t.count { it in '؀'..'ۿ' } > t.length / 4 }
        if (arabic * 2 >= titles.size) return "ar"
        val words = titles.flatMap { RX_WORD.findAll(it.lowercase()).map { m -> m.value }.toList() }
        val scores = STOPS.mapValues { (_, set) -> words.count { it in set } }
        val best = scores.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= 2 }
    }

    /**
     * Where a found source belongs: Top Stories when it writes in Editrove's language, the
     * national tab when it writes in the edition's; one of the two at least.
     */
    fun topicsFor(language: String?, appLanguage: String, countryCode: String): List<String> {
        val national = TopicRegistry.country(countryCode).language
        return buildList {
            if (language == null || language == appLanguage) add("TOP")
            if (language == national) add(TopicRegistry.NATIONAL_ID)
            if (isEmpty()) add(if (language == national) TopicRegistry.NATIONAL_ID else "TOP")
        }.distinct()
    }

    /** How well a found source answers [query] (name and site matches first, then freshness and readership). */
    fun score(query: String, name: String, domain: String, subscribers: Long, newestAgeHours: Long, viaGoogle: Boolean): Double {
        val q = normalize(query)
        val n = normalize(name)
        val d = domain.lowercase()
        val qCompact = q.replace(" ", "")
        var s = 0.0
        if (q.isNotEmpty() && (n.contains(q) || q.contains(n.take(12)) && n.length >= 4)) s += 6
        if (qCompact.isNotEmpty() && d.replace("-", "").contains(qCompact)) s += 6
        q.split(' ').filter { it.length >= 3 }.forEach { w -> if (n.contains(w) || d.contains(w)) s += 1.5 }
        s += kotlin.math.ln(1.0 + subscribers) / 3
        s += when { newestAgeHours <= 6 -> 2.0; newestAgeHours <= 24 -> 1.0; newestAgeHours <= 72 -> 0.3; else -> -1.0 }
        if (viaGoogle) s -= 1.5
        return s
    }

    /**
     * A tidy name: "rss-أخبار عاجلة" from youm7 → "اليوم السابع · أخبار عاجلة",
     * "Masrawy-اقتصاد" → "Masrawy · اقتصاد", "Tech | The Verge" stays readable.
     */
    fun cleanName(raw: String, site: String?): String {
        // "CNN.com - RSS Channel - App Tech Section" → "CNN.com – Tech".
        var n = raw.trim().replace(Regex("\\s*[-–|]\\s*RSS Channel\\s*([-–|]\\s*)?", RegexOption.IGNORE_CASE), " – ")
            .replace(Regex("\\bApp\\s+(\\w+)\\s+Section\\b", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("\\s+Section$", RegexOption.IGNORE_CASE), "")
        n = n.replace(Regex("^(rss|feed|atom)\\s*[-:|–]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–|]\\s*(rss|feed)$", RegexOption.IGNORE_CASE), "").trim()
        n = n.replace(Regex("(?<=\\p{L})-(?=\\p{L}{2,}\\s|\\p{IsArabic})"), " · ")
        val s = site?.trim()?.takeIf { it.isNotBlank() } ?: return n.ifBlank { raw }
        val ns = normalize(s)
        return if (ns.isNotEmpty() && !normalize(n).contains(ns) && !ns.contains(normalize(n))) "$s · $n" else n.ifBlank { s }
    }

    /** Lower-case, Arabic letter variants folded, punctuation dropped ("Al-Ahram" ≈ "al ahram"). */
    fun normalize(t: String): String = t.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private val RX_DOMAIN = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,}$", RegexOption.IGNORE_CASE)
    private val RX_WORD = Regex("\\p{L}+")
}
