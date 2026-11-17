// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.util.Locale

/**
 * The languages Editrove's own words come in. Each is named in its own script so the
 * list stays readable whatever language the app is in (the way back is always findable).
 */
object Languages {
    data class Language(val code: String, val nativeName: String, val englishName: String, val rtl: Boolean = false)

    val ALL: List<Language> = listOf(
        Language("en", "English", "English"),
        Language("ar", "العربية", "Arabic", rtl = true),
        Language("fr", "Français", "French"),
        Language("es", "Español", "Spanish"),
        Language("de", "Deutsch", "German"),
    )

    fun of(code: String?): Language? {
        val c = code?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
        return ALL.firstOrNull { it.code == c }
    }

    /** The language in use: the chosen one, else the phone's when Editrove speaks it, else English. */
    fun effective(chosen: String?): String =
        of(chosen?.takeIf { it.isNotBlank() })?.code ?: of(Locale.getDefault().language)?.code ?: "en"

    /** The app language an edition country reads in (null when Editrove isn't translated into it). */
    fun forCountry(code: String): Language? = of(TopicRegistry.country(code).language)

    /** "6h ago" in [lang]. */
    fun relativeTime(lang: String, minutes: Long): String {
        val h = minutes / 60
        val d = h / 24
        return when (of(lang)?.code ?: "en") {
            "ar" -> when {
                minutes < 1 -> "الآن"
                minutes < 60 -> "قبل $minutes د"
                h < 24 -> "قبل $h س"
                d == 1L -> "أمس"
                else -> "قبل $d أيام"
            }
            "fr" -> when {
                minutes < 1 -> "À l’instant"
                minutes < 60 -> "Il y a $minutes min"
                h < 24 -> "Il y a $h h"
                d == 1L -> "Hier"
                else -> "Il y a $d j"
            }
            "es" -> when {
                minutes < 1 -> "Ahora mismo"
                minutes < 60 -> "Hace $minutes min"
                h < 24 -> "Hace $h h"
                d == 1L -> "Ayer"
                else -> "Hace $d d"
            }
            "de" -> when {
                minutes < 1 -> "Gerade eben"
                minutes < 60 -> "Vor $minutes Min."
                h < 24 -> "Vor $h Std."
                d == 1L -> "Gestern"
                else -> "Vor $d T."
            }
            else -> when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                h < 24 -> "${h}h ago"
                d == 1L -> "Yesterday"
                else -> "${d}d ago"
            }
        }
    }

    /** Built-in topic names: id → (en, ar, fr, es, de). */
    private val TOPIC_NAMES = mapOf(
        "TOP" to listOf("Top Stories", "أهم الأخبار", "À la une", "Portada", "Topmeldungen"),
        "LATEST" to listOf("Latest", "الأحدث", "En continu", "Lo último", "Neueste"),
        "AI" to listOf("AI", "الذكاء الاصطناعي", "IA", "IA", "KI"),
        "BUSINESS" to listOf("Business", "اقتصاد", "Économie", "Economía", "Wirtschaft"),
        "TECHNOLOGY" to listOf("Tech", "تكنولوجيا", "Tech", "Tecnología", "Technik"),
        "INTERNATIONAL" to listOf("International", "دولي", "International", "Internacional", "International"),
        "SCIENCE" to listOf("Science", "علوم", "Sciences", "Ciencia", "Wissenschaft"),
        "ENTERTAINMENT" to listOf("Entertainment", "فن وترفيه", "Culture", "Entretenimiento", "Unterhaltung"),
        "SPORTS" to listOf("Sports", "رياضة", "Sport", "Deportes", "Sport"),
        "POLITICS" to listOf("Politics", "سياسة", "Politique", "Política", "Politik"),
        "FINANCE" to listOf("Finance", "مال وأسواق", "Finance", "Finanzas", "Finanzen"),
        "LIFESTYLE" to listOf("Lifestyle", "صحة وحياة", "Vie & santé", "Estilo de vida", "Leben"),
    )

    fun topicName(id: String, lang: String): String? =
        TOPIC_NAMES[id]?.get(ALL.indexOfFirst { it.code == (of(lang)?.code ?: "en") }.coerceAtLeast(0))

    /** A country's name for its national tab ("US", "مصر", "Égypte"). */
    fun countryName(code: String, lang: String): String {
        val l = of(lang)?.code ?: "en"
        if (l == "en") when (code.uppercase()) { "US" -> return "US"; "GB" -> return "UK"; "AE" -> return "UAE" }
        return Locale("", code.uppercase()).getDisplayCountry(Locale(l)).ifBlank { TopicRegistry.country(code).nationalLabel }
    }

    /** Google News searches for the search-built topics, in the feed's own language. */
    internal fun query(topicId: String, lang: String): String = when (topicId) {
        "AI" -> when (lang) {
            "ar" -> "\"الذكاء الاصطناعي\" when:2d"
            "fr" -> "\"intelligence artificielle\" OR IA when:2d"
            "es" -> "\"inteligencia artificial\" OR IA when:2d"
            "de" -> "\"künstliche Intelligenz\" OR KI when:2d"
            else -> "\"artificial intelligence\" OR AI when:2d"
        }
        "POLITICS" -> when (lang) {
            "ar" -> "سياسة when:1d"; "fr" -> "politique when:1d"; "es" -> "política when:1d"; "de" -> "Politik when:1d"
            else -> "politics when:1d"
        }
        else -> when (lang) { // FINANCE
            "ar" -> "البورصة OR الاقتصاد OR الأسواق when:1d"
            "fr" -> "bourse OR économie OR marchés when:1d"
            "es" -> "bolsa OR economía OR mercados when:1d"
            "de" -> "Börse OR Wirtschaft OR Märkte when:1d"
            else -> "stock market OR economy OR markets when:1d"
        }
    }

    /** The Google News language of a feed URL ("hl=ar" → "ar"); null when it isn't a Google feed. */
    fun feedLanguage(url: String?): String? =
        url?.let { RX_HL.find(it)?.groupValues?.get(1)?.lowercase() }
}

private val RX_HL = Regex("[?&]hl=([a-zA-Z]+)")
