// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import news.openwire.domain.model.BriefingDetail

/**
 * Writes the words of an audio briefing the way a news anchor would say them:
 * a dated open, varied hand-offs between stories, attribution ("Reuters
 * reports…"), a sign-off — and never the same transition twice in a row.
 */
object BriefingScript {

    /**
     * One story as the narrator will read it.
     *
     * @param subtitle the publisher's standfirst (the deck under the headline).
     * @param keyPoints the on-device summary — the same key points the reader's Summary box shows.
     * @param summary a public excerpt, used when there's no subtitle or article text.
     */
    data class Story(
        val title: String,
        val source: String?,
        val summary: String? = null,
        val body: String? = null,
        val subtitle: String? = null,
        val keyPoints: List<String> = emptyList(),
    )

    /** How the anchor speaks in one language. */
    private class Phrases(
        val greeting: (Int) -> String,
        val dayPattern: String,
        val intro: (greeting: String, day: String, topic: String, what: String) -> String,
        val what: (BriefingDetail, Int) -> String,
        val lead: List<String>,
        val nextHeadlines: List<String>,
        val nextDetailed: List<String>,
        val final: List<String>,
        val intoSummary: List<String>,
        val attribution: List<(String) -> String>,
        val reports: (String) -> String,
        val thatReport: (String) -> String,
        val outro: (String) -> String,
    )

    private val EN = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" } },
        dayPattern = "EEEE, MMMM d",
        intro = { g, d, t, w -> "$g. It's $d, and this is your $t briefing from Editrove. Here are $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> "the top $n headlines"
            BriefingDetail.SUMMARIES -> "$n stories, with the key details on each"
            BriefingDetail.FULL -> "$n stories, read in full"
        } },
        lead = listOf("Our top story.", "We begin with our lead story.", "First up."),
        nextHeadlines = listOf("Next.", "Also making news.", "Meanwhile.", "In other news.", "Elsewhere.", "Another headline we're following."),
        nextDetailed = listOf(
            "Turning now to another story.", "In other news today.", "Moving on.", "Now to a story we're following closely.",
            "Elsewhere in the news.", "Our next story.", "Also developing today.",
        ),
        final = listOf("And finally.", "One last story before we go.", "Finally today."),
        intoSummary = listOf("Here's what we know.", "The details.", "Here's the latest."),
        attribution = listOf({ s -> "$s reports." }, { s -> "According to $s." }, { s -> "From $s." }, { s -> "$s has the story." }),
        reports = { "$it reports." },
        thatReport = { "That report from $it." },
        outro = { t -> "And that's your $t briefing. For the full stories, open them in Editrove. Thanks for listening." },
    )

    private val AR = Phrases(
        greeting = { h -> if (h in 4..11) "صباح الخير" else "مساء الخير" },
        dayPattern = "EEEE d MMMM",
        intro = { g, d, t, w -> "$g. اليوم $d، وهذه نشرة $t من Editrove. إليكم $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> "أهم $n عناوين"
            BriefingDetail.SUMMARIES -> "$n أخبار مع أبرز تفاصيل كل منها"
            BriefingDetail.FULL -> "$n أخبار كاملة"
        } },
        lead = listOf("خبرنا الأول.", "نبدأ بالخبر الأبرز.", "أولاً."),
        nextHeadlines = listOf("التالي.", "ومن الأخبار أيضاً.", "في الأثناء.", "وفي خبر آخر.", "وفي مكان آخر.", "ونتابع خبراً آخر."),
        nextDetailed = listOf(
            "ننتقل الآن إلى خبر آخر.", "ومن أخبار اليوم أيضاً.", "نواصل.", "والآن إلى خبر نتابعه عن كثب.",
            "وفي أخبار أخرى.", "خبرنا التالي.", "ومن التطورات اليوم أيضاً.",
        ),
        final = listOf("وأخيراً.", "خبر أخير قبل أن نختم.", "وختاماً لهذا اليوم."),
        intoSummary = listOf("إليكم ما نعرفه.", "التفاصيل.", "وإليكم آخر المستجدات."),
        attribution = listOf({ s -> "بحسب $s." }, { s -> "نقلاً عن $s." }, { s -> "من $s." }, { s -> "والتفاصيل لدى $s." }),
        reports = { "بحسب $it." },
        thatReport = { "كان ذلك تقرير $it." },
        outro = { t -> "وبهذا نختتم نشرة $t. لقراءة الأخبار كاملة، افتحها في Editrove. شكراً لاستماعكم." },
    )

    private val FR = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Bonjour"; in 12..16 -> "Bon après-midi"; else -> "Bonsoir" } },
        dayPattern = "EEEE d MMMM",
        intro = { g, d, t, w -> "$g. Nous sommes le $d, et voici votre point $t d’Editrove. Voici $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> "les $n principaux titres"
            BriefingDetail.SUMMARIES -> "$n sujets, avec l’essentiel de chacun"
            BriefingDetail.FULL -> "$n articles, lus en entier"
        } },
        lead = listOf("Notre titre principal.", "Nous commençons par notre sujet phare.", "Pour commencer."),
        nextHeadlines = listOf("Ensuite.", "Également à la une.", "Pendant ce temps.", "Dans l’actualité également.", "Ailleurs.", "Un autre titre que nous suivons."),
        nextDetailed = listOf(
            "Passons à un autre sujet.", "Dans l’actualité du jour également.", "On continue.", "Place à un sujet que nous suivons de près.",
            "Ailleurs dans l’actualité.", "Notre sujet suivant.", "Également aujourd’hui.",
        ),
        final = listOf("Et pour finir.", "Un dernier sujet avant de nous quitter.", "Enfin aujourd’hui."),
        intoSummary = listOf("Voici ce que l’on sait.", "Les détails.", "Voici les dernières informations."),
        attribution = listOf({ s -> "Selon $s." }, { s -> "D’après $s." }, { s -> "Source : $s." }, { s -> "$s rapporte l’information." }),
        reports = { "Selon $it." },
        thatReport = { "C’était un article de $it." },
        outro = { t -> "C’était votre point $t. Pour lire les articles en entier, ouvrez-les dans Editrove. Merci de votre écoute." },
    )

    private val ES = Phrases(
        greeting = { h -> when (h) { in 4..11 -> "Buenos días"; in 12..19 -> "Buenas tardes"; else -> "Buenas noches" } },
        dayPattern = "EEEE d 'de' MMMM",
        intro = { g, d, t, w -> "$g. Hoy es $d, y este es tu resumen de $t de Editrove. Aquí tienes $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> "los $n titulares principales"
            BriefingDetail.SUMMARIES -> "$n noticias, con los datos clave de cada una"
            BriefingDetail.FULL -> "$n noticias, leídas completas"
        } },
        lead = listOf("Nuestra noticia principal.", "Comenzamos con nuestra noticia destacada.", "En primer lugar."),
        nextHeadlines = listOf("A continuación.", "También es noticia.", "Mientras tanto.", "En otras noticias.", "En otro orden.", "Otro titular que seguimos."),
        nextDetailed = listOf(
            "Pasamos ahora a otra noticia.", "En otras noticias de hoy.", "Seguimos.", "Ahora, una noticia que seguimos de cerca.",
            "En otras partes.", "Nuestra siguiente noticia.", "También hoy.",
        ),
        final = listOf("Y para terminar.", "Una última noticia antes de despedirnos.", "Por último hoy."),
        intoSummary = listOf("Esto es lo que sabemos.", "Los detalles.", "Esto es lo último."),
        attribution = listOf({ s -> "Informa $s." }, { s -> "Según $s." }, { s -> "De $s." }, { s -> "$s tiene la noticia." }),
        reports = { "Informa $it." },
        thatReport = { "Esa fue la información de $it." },
        outro = { t -> "Y este fue tu resumen de $t. Para leer las noticias completas, ábrelas en Editrove. Gracias por escuchar." },
    )

    private val DE = Phrases(
        greeting = { h -> when (h) { in 4..10 -> "Guten Morgen"; in 11..17 -> "Guten Tag"; else -> "Guten Abend" } },
        dayPattern = "EEEE, d. MMMM",
        intro = { g, d, t, w -> "$g. Heute ist $d, und das ist Ihr Briefing $t von Editrove. Hier sind $w." },
        what = { detail, n -> when (detail) {
            BriefingDetail.HEADLINES -> "die $n wichtigsten Schlagzeilen"
            BriefingDetail.SUMMARIES -> "$n Meldungen mit den wichtigsten Details"
            BriefingDetail.FULL -> "$n Meldungen in voller Länge"
        } },
        lead = listOf("Unser Top-Thema.", "Wir beginnen mit unserer Hauptmeldung.", "Zuerst."),
        nextHeadlines = listOf("Weiter.", "Ebenfalls in den Nachrichten.", "Unterdessen.", "In weiteren Nachrichten.", "Anderswo.", "Eine weitere Schlagzeile, die wir verfolgen."),
        nextDetailed = listOf(
            "Nun zu einer anderen Meldung.", "Weitere Nachrichten des Tages.", "Weiter geht’s.", "Nun zu einer Geschichte, die wir genau verfolgen.",
            "Weitere Nachrichten.", "Unsere nächste Meldung.", "Ebenfalls heute.",
        ),
        final = listOf("Und zum Schluss.", "Eine letzte Meldung, bevor wir uns verabschieden.", "Zum Abschluss für heute."),
        intoSummary = listOf("Das wissen wir.", "Die Einzelheiten.", "Der aktuelle Stand."),
        attribution = listOf({ s -> "$s berichtet." }, { s -> "Laut $s." }, { s -> "Von $s." }, { s -> "$s hat die Geschichte." }),
        reports = { "$it berichtet." },
        thatReport = { "Das war der Bericht von $it." },
        outro = { t -> "Das war Ihr Briefing $t. Die vollständigen Artikel finden Sie in Editrove. Danke fürs Zuhören." },
    )

    private fun phrases(lang: String) = when (Languages.of(lang)?.code) { "ar" -> AR; "fr" -> FR; "es" -> ES; "de" -> DE; else -> EN }

    fun intro(topic: String, count: Int, detail: BriefingDetail, now: LocalDateTime, lang: String = "en"): String {
        val p = phrases(lang)
        val locale = Locale(Languages.of(lang)?.code ?: "en")
        val day = now.format(DateTimeFormatter.ofPattern(p.dayPattern, locale))
        return p.intro(p.greeting(now.hour), day, topic.trim(), p.what(detail, count))
    }

    /** The hand-off into story [index] (0-based) of [total]. */
    fun lead(index: Int, total: Int, detail: BriefingDetail, lang: String = "en"): String {
        val p = phrases(lang)
        return when {
            index == 0 -> p.lead[0]
            index == total - 1 && total > 2 -> p.final[index % p.final.size]
            detail == BriefingDetail.HEADLINES -> p.nextHeadlines[(index - 1) % p.nextHeadlines.size]
            else -> p.nextDetailed[(index - 1) % p.nextDetailed.size]
        }
    }

    private fun attribution(source: String?, index: Int, p: Phrases): String? =
        source?.takeIf { it.isNotBlank() }?.let { p.attribution[index % p.attribution.size](it) }

    /** Everything said for one story, as separate lines (natural pauses between them). */
    fun story(index: Int, total: Int, story: Story, detail: BriefingDetail, lang: String = "en"): List<String> = buildList {
        val p = phrases(lang)
        add(lead(index, total, detail, lang))
        val title = sentence(clean(story.title))
        add(title)
        // Headlines and Summaries both read the subtitle (or, without one, the public excerpt).
        val subtitle = deck(story)?.takeIf { FeedWeaver.calculateTitleSimilarity(it, title) < 0.7 }
        when (detail) {
            BriefingDetail.HEADLINES -> {
                subtitle?.let(::add)
                attribution(story.source, index, p)?.let(::add)
            }
            BriefingDetail.SUMMARIES -> {
                subtitle?.let(::add)
                attribution(story.source, index, p)?.let(::add)
                // The Summary box's key points, minus any that just repeat the subtitle.
                val points = story.keyPoints.map { sentence(clean(it)) }.filter { p ->
                    p.length > 20 && (subtitle == null || FeedWeaver.calculateTitleSimilarity(p, subtitle) < 0.6)
                }
                if (points.isNotEmpty()) {
                    add(p.intoSummary[index % p.intoSummary.size])
                    addAll(points)
                }
            }
            BriefingDetail.FULL -> {
                val body = story.body?.let(::clean)?.takeIf { it.length > 200 } ?: (story.subtitle ?: story.summary)?.let(::clean)
                if (body != null) {
                    story.source?.takeIf { it.isNotBlank() }?.let { add(p.reports(it)) }
                    add(body)
                    story.source?.takeIf { it.isNotBlank() }?.let { add(p.thatReport(it)) }
                } else attribution(story.source, index, p)?.let(::add)
            }
        }
    }

    /** The standfirst to read: the subtitle, else the excerpt; at most two sentences. */
    private fun deck(story: Story): String? {
        val raw = (story.subtitle?.takeIf { it.isNotBlank() } ?: story.summary)?.let(::clean)?.takeIf { it.length > 20 } ?: return null
        val sentences = Summarizer.split(raw)
        val short = if (sentences.size > 2) sentences.take(2).joinToString(" ") else raw
        return sentence(short.take(400))
    }

    private fun sentence(t: String) = if (t.endsWith("。") || t.endsWith(".") || t.endsWith("?") || t.endsWith("!") || t.endsWith("”") || t.endsWith("\"")) t else "$t."

    fun outro(topic: String, lang: String = "en"): String = phrases(lang).outro(topic.trim())

    /** Strips leftovers that read badly aloud (URLs, bracketed credits, repeated whitespace). */
    fun clean(text: String): String = text
        .replace(RX_1, "")
        .replace(RX_2, "")
        .replace(RX_3, ". ")
        .replace(RX_4, " ")
        .trim()
}

// Compiled once: Android compiles regexes natively (ICU), so building them per call is costly.
private val RX_1 = Regex("https?://\\S+")
private val RX_2 = Regex("\\[[^]]{0,60}]")
private val RX_3 = Regex("\\s*[|•]\\s*")
private val RX_4 = Regex("\\s+")
