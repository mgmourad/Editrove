// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.FeedSource

/**
 * Newsrooms suggested for an edition's country, offered when the reader moves there.
 * Where an outlet publishes no working RSS, its stories come from a Google News search
 * limited to its site (resolved to the publisher's own pages like any Google link).
 */
object LocalSources {
    data class Suggestion(val name: String, val url: String, val domain: String, val language: String)

    private fun rss(name: String, url: String, domain: String, lang: String) = Suggestion(name, url, domain, lang)

    private fun site(name: String, domain: String, country: String, lang: String): Suggestion {
        val c = TopicRegistry.COUNTRIES.firstOrNull { it.code == country && it.language == lang }
            ?: TopicRegistry.COUNTRIES.first { it.language == lang }
        val locale = if (c.code == country) c else c.copy(code = country, ceid = "$country:${c.ceid.substringAfter(':')}")
        return Suggestion(name, TopicRegistry.googleNewsSearchUrl("site:$domain when:2d", locale), domain, lang)
    }

    fun forCountry(code: String): List<Suggestion> = when (code.uppercase()) {
        "EG" -> listOf(
            site("Al-Ahram", "ahram.org.eg", "EG", "ar"),
            rss("Al-Masry Al-Youm", "https://www.almasryalyoum.com/rss/rssfeeds", "almasryalyoum.com", "ar"),
            site("Youm7", "youm7.com", "EG", "ar"),
            site("Shorouk News", "shorouknews.com", "EG", "ar"),
            rss("Masrawy", "https://www.masrawy.com/rss/feed/25", "masrawy.com", "ar"),
            rss("BBC Arabic", "https://feeds.bbci.co.uk/arabic/rss.xml", "bbc.com", "ar"),
            site("Ahram Online", "english.ahram.org.eg", "US", "en"),
            rss("Mada Masr", "https://www.madamasr.com/en/feed/", "madamasr.com", "en"),
            site("Egypt Independent", "egyptindependent.com", "US", "en"),
            rss("Daily News Egypt", "https://www.dailynewsegypt.com/feed/", "dailynewsegypt.com", "en"),
        )
        "SA" -> listOf(
            rss("Asharq Al-Awsat", "https://aawsat.com/feed", "aawsat.com", "ar"),
            rss("Okaz", "https://www.okaz.com.sa/rssFeed/0", "okaz.com.sa", "ar"),
            site("Al Arabiya", "alarabiya.net", "SA", "ar"),
            rss("Sky News Arabia", "https://www.skynewsarabia.com/rss", "skynewsarabia.com", "ar"),
            rss("Independent Arabia", "https://www.independentarabia.com/rss.xml", "independentarabia.com", "ar"),
            rss("BBC Arabic", "https://feeds.bbci.co.uk/arabic/rss.xml", "bbc.com", "ar"),
        )
        "AE" -> listOf(
            rss("The National", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", "thenationalnews.com", "en"),
            site("Gulf News", "gulfnews.com", "AE", "en"),
            site("Khaleej Times", "khaleejtimes.com", "AE", "en"),
            rss("Emarat Al Youm", "https://www.emaratalyoum.com/1.533091?ot=ot.AjaxPageLayout", "emaratalyoum.com", "ar"),
            rss("Sky News Arabia", "https://www.skynewsarabia.com/rss", "skynewsarabia.com", "ar"),
        )
        "LB" -> listOf(
            rss("Annahar", "https://www.annahar.com/rss", "annahar.com", "ar"),
            rss("Sky News Arabia", "https://www.skynewsarabia.com/rss", "skynewsarabia.com", "ar"),
            rss("BBC Arabic", "https://feeds.bbci.co.uk/arabic/rss.xml", "bbc.com", "ar"),
        )
        "MA" -> listOf(
            rss("Hespress", "https://www.hespress.com/feed", "hespress.com", "ar"),
            rss("Hespress FR", "https://fr.hespress.com/feed", "fr.hespress.com", "fr"),
            rss("Le360", "https://www.le360.ma/rss", "le360.ma", "fr"),
        )
        "FR" -> listOf(
            rss("Le Monde", "https://www.lemonde.fr/rss/une.xml", "lemonde.fr", "fr"),
            rss("franceinfo", "https://www.francetvinfo.fr/titres.rss", "francetvinfo.fr", "fr"),
            rss("Le Figaro", "https://www.lefigaro.fr/rss/figaro_actualites.xml", "lefigaro.fr", "fr"),
            rss("Libération", "https://www.liberation.fr/arc/outboundfeeds/rss-all/collection/accueil-une/?outputType=xml", "liberation.fr", "fr"),
            rss("20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml", "20minutes.fr", "fr"),
            rss("RFI", "https://www.rfi.fr/fr/rss", "rfi.fr", "fr"),
            rss("France 24", "https://www.france24.com/fr/rss", "france24.com", "fr"),
        )
        "BE" -> listOf(
            rss("La Libre", "https://www.lalibre.be/arc/outboundfeeds/rss/?outputType=xml", "lalibre.be", "fr"),
            site("Le Soir", "lesoir.be", "BE", "fr"),
            site("RTBF", "rtbf.be", "BE", "fr"),
        )
        "DE" -> listOf(
            rss("tagesschau", "https://www.tagesschau.de/xml/rss2/", "tagesschau.de", "de"),
            rss("Der Spiegel", "https://www.spiegel.de/schlagzeilen/index.rss", "spiegel.de", "de"),
            rss("Die Zeit", "https://newsfeed.zeit.de/index", "zeit.de", "de"),
            rss("Süddeutsche Zeitung", "https://rss.sueddeutsche.de/rss/Topthemen", "sueddeutsche.de", "de"),
            rss("ZDFheute", "https://www.zdf.de/rss/zdf/nachrichten", "zdf.de", "de"),
            rss("DW Deutsch", "https://rss.dw.com/rdf/rss-de-all", "dw.com", "de"),
        )
        "AT" -> listOf(
            rss("ORF", "https://rss.orf.at/news.xml", "orf.at", "de"),
            rss("Der Standard", "https://www.derstandard.at/rss", "derstandard.at", "de"),
        )
        "CH" -> listOf(
            rss("SRF News", "https://www.srf.ch/news/bnf/rss/1646", "srf.ch", "de"),
            rss("NZZ", "https://www.nzz.ch/recent.rss", "nzz.ch", "de"),
        )
        "ES" -> listOf(
            rss("El País", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "elpais.com", "es"),
            rss("El Mundo", "https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml", "elmundo.es", "es"),
            rss("ABC", "https://www.abc.es/rss/2.0/portada/", "abc.es", "es"),
            rss("20minutos", "https://www.20minutos.es/rss/", "20minutos.es", "es"),
            rss("elDiario.es", "https://www.eldiario.es/rss/", "eldiario.es", "es"),
        )
        "MX" -> listOf(
            rss("La Jornada", "https://www.jornada.com.mx/rss/edicion.xml", "jornada.com.mx", "es"),
            site("El Universal", "eluniversal.com.mx", "MX", "es"),
            site("Milenio", "milenio.com", "MX", "es"),
        )
        "AR" -> listOf(
            rss("Clarín", "https://www.clarin.com/rss/lo-ultimo/", "clarin.com", "es"),
            rss("La Nación", "https://www.lanacion.com.ar/arc/outboundfeeds/rss/?outputType=xml", "lanacion.com.ar", "es"),
            rss("Infobae", "https://www.infobae.com/arc/outboundfeeds/rss/", "infobae.com", "es"),
        )
        "CO" -> listOf(
            rss("El Tiempo", "https://www.eltiempo.com/rss/colombia.xml", "eltiempo.com", "es"),
            site("El Espectador", "elespectador.com", "CO", "es"),
        )
        "CL" -> listOf(
            rss("La Tercera", "https://www.latercera.com/arc/outboundfeeds/rss/?outputType=xml", "latercera.com", "es"),
            site("BioBioChile", "biobiochile.cl", "CL", "es"),
        )
        else -> emptyList()
    }

    /**
     * The suggestion as a source: it joins the national tab when it reads in the country's
     * language, and Top Stories when it reads in the app's.
     */
    fun toFeed(s: Suggestion, countryCode: String, appLanguage: String, nowMillis: Long): FeedSource {
        val topics = buildList {
            if (s.language == TopicRegistry.country(countryCode).language) add(TopicRegistry.NATIONAL_ID)
            if (s.language == appLanguage) add("TOP")
            if (isEmpty()) add(TopicRegistry.NATIONAL_ID)
        }
        return FeedSource(
            id = "local_${s.domain.replace(Regex("[^a-z0-9]"), "_")}_${nowMillis.toString().takeLast(4)}",
            name = s.name, url = s.url, domain = s.domain, topics = topics, maxArticles = 8,
            category = "Custom", isCustom = true,
        )
    }
}
