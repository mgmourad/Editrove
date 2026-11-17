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

    /** A search for stories citing an outlet (for agencies whose own site isn't indexed well). */
    private fun cites(name: String, phrase: String, domain: String, country: String, lang: String): Suggestion {
        val c = TopicRegistry.COUNTRIES.first { it.code == country }
        return Suggestion(name, TopicRegistry.googleNewsSearchUrl("\"$phrase\" when:1d", c), domain, lang)
    }

    /** The edition country's own newsrooms. */
    fun forCountry(code: String): List<Suggestion> = when (code.uppercase()) {
        "EG" -> listOf(
            site("Al-Ahram · الأهرام", "ahram.org.eg", "EG", "ar"),
            rss("Al-Masry Al-Youm · المصري اليوم", "https://www.almasryalyoum.com/rss/rssfeeds", "almasryalyoum.com", "ar"),
            site("Youm7 · اليوم السابع", "youm7.com", "EG", "ar"),
            site("Shorouk News · الشروق", "shorouknews.com", "EG", "ar"),
            cites("MENA · وكالة أنباء الشرق الأوسط", "وكالة أنباء الشرق الأوسط", "mena.org.eg", "EG", "ar"),
            site("Al-Watan · الوطن", "elwatannews.com", "EG", "ar"),
            site("Al-Dostor · الدستور", "dostor.org", "EG", "ar"),
            site("Akhbar El-Yom · أخبار اليوم", "akhbarelyom.com", "EG", "ar"),
            rss("Masrawy · مصراوي", "https://www.masrawy.com/rss/feed/25", "masrawy.com", "ar"),
            rss("Sada El-Balad · صدى البلد", "https://www.elbalad.news/rss.aspx", "elbalad.news", "ar"),
            rss("Veto · فيتو", "https://www.vetogate.com/rss.aspx", "vetogate.com", "ar"),
            site("Cairo 24 · القاهرة 24", "cairo24.com", "EG", "ar"),
            site("Ahram Online", "english.ahram.org.eg", "US", "en"),
            rss("Mada Masr", "https://www.madamasr.com/en/feed/", "madamasr.com", "en"),
            site("Egypt Independent", "egyptindependent.com", "US", "en"),
            rss("Daily News Egypt", "https://www.dailynewsegypt.com/feed/", "dailynewsegypt.com", "en"),
        )
        "SA" -> listOf(
            rss("Okaz · عكاظ", "https://www.okaz.com.sa/rssFeed/0", "okaz.com.sa", "ar"),
            site("Al Riyadh · الرياض", "alriyadh.com", "SA", "ar"),
            site("Al Watan · الوطن", "alwatan.com.sa", "SA", "ar"),
            site("Sabq · سبق", "sabq.org", "SA", "ar"),
            rss("Arab News", "https://www.arabnews.com/rss.xml", "arabnews.com", "en"),
            site("Saudi Gazette", "saudigazette.com.sa", "US", "en"),
        )
        "AE" -> listOf(
            rss("Emarat Al Youm · الإمارات اليوم", "https://www.emaratalyoum.com/1.533091?ot=ot.AjaxPageLayout", "emaratalyoum.com", "ar"),
            site("Al Khaleej · الخليج", "alkhaleej.ae", "AE", "ar"),
            site("Al Bayan · البيان", "albayan.ae", "AE", "ar"),
            rss("The National", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", "thenationalnews.com", "en"),
            site("Gulf News", "gulfnews.com", "AE", "en"),
            site("Khaleej Times", "khaleejtimes.com", "AE", "en"),
        )
        "LB" -> listOf(
            rss("Annahar · النهار", "https://www.annahar.com/rss", "annahar.com", "ar"),
            site("LBCI", "lbcgroup.tv", "LB", "ar"),
            site("El Nashra · النشرة", "elnashra.com", "LB", "ar"),
            site("Al Modon · المدن", "almodon.com", "LB", "ar"),
            site("L’Orient-Le Jour", "lorientlejour.com", "FR", "fr"),
        )
        "MA" -> listOf(
            rss("Hespress · هسبريس", "https://www.hespress.com/feed", "hespress.com", "ar"),
            rss("Hespress FR", "https://fr.hespress.com/feed", "fr.hespress.com", "fr"),
            rss("Le360", "https://www.le360.ma/rss", "le360.ma", "fr"),
            site("Médias24", "medias24.com", "MA", "fr"),
        )
        "FR" -> listOf(
            rss("Le Monde", "https://www.lemonde.fr/rss/une.xml", "lemonde.fr", "fr"),
            rss("franceinfo", "https://www.francetvinfo.fr/titres.rss", "francetvinfo.fr", "fr"),
            rss("Le Figaro", "https://www.lefigaro.fr/rss/figaro_actualites.xml", "lefigaro.fr", "fr"),
            rss("Libération", "https://www.liberation.fr/arc/outboundfeeds/rss-all/collection/accueil-une/?outputType=xml", "liberation.fr", "fr"),
            rss("L’Express", "https://www.lexpress.fr/arc/outboundfeeds/rss/alaune.xml", "lexpress.fr", "fr"),
            rss("20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml", "20minutes.fr", "fr"),
            site("Le Parisien", "leparisien.fr", "FR", "fr"),
            site("Ouest-France", "ouest-france.fr", "FR", "fr"),
        )
        "BE" -> listOf(
            rss("La Libre", "https://www.lalibre.be/arc/outboundfeeds/rss/?outputType=xml", "lalibre.be", "fr"),
            site("Le Soir", "lesoir.be", "BE", "fr"),
            site("RTBF", "rtbf.be", "BE", "fr"),
        )
        "DE" -> listOf(
            rss("ZDFheute", "https://www.zdf.de/rss/zdf/nachrichten", "zdf.de", "de"),
            rss("n-tv", "https://www.n-tv.de/rss", "n-tv.de", "de"),
            rss("Der Tagesspiegel", "https://www.tagesspiegel.de/contentexport/feed/home", "tagesspiegel.de", "de"),
            rss("Die Welt", "https://www.welt.de/feeds/topnews.rss", "welt.de", "de"),
            rss("Stern", "https://www.stern.de/feed/standard/alle-nachrichten/", "stern.de", "de"),
            rss("Handelsblatt", "https://www.handelsblatt.com/contentexport/feed/top-themen", "handelsblatt.com", "de"),
            site("FAZ", "faz.net", "DE", "de"),
        )
        "AT" -> listOf(
            rss("ORF", "https://rss.orf.at/news.xml", "orf.at", "de"),
            rss("Der Standard", "https://www.derstandard.at/rss", "derstandard.at", "de"),
            site("Die Presse", "diepresse.com", "AT", "de"),
            site("Kurier", "kurier.at", "AT", "de"),
        )
        "CH" -> listOf(
            rss("SRF News", "https://www.srf.ch/news/bnf/rss/1646", "srf.ch", "de"),
            rss("NZZ", "https://www.nzz.ch/recent.rss", "nzz.ch", "de"),
            site("Tages-Anzeiger", "tagesanzeiger.ch", "CH", "de"),
            site("watson", "watson.ch", "CH", "de"),
        )
        "ES" -> listOf(
            rss("El País", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "elpais.com", "es"),
            rss("El Mundo", "https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml", "elmundo.es", "es"),
            rss("ABC", "https://www.abc.es/rss/2.0/portada/", "abc.es", "es"),
            rss("20minutos", "https://www.20minutos.es/rss/", "20minutos.es", "es"),
            rss("elDiario.es", "https://www.eldiario.es/rss/", "eldiario.es", "es"),
            site("RTVE", "rtve.es", "ES", "es"),
        )
        "MX" -> listOf(
            rss("La Jornada", "https://www.jornada.com.mx/rss/edicion.xml", "jornada.com.mx", "es"),
            site("El Universal", "eluniversal.com.mx", "MX", "es"),
            site("Milenio", "milenio.com", "MX", "es"),
            site("Reforma", "reforma.com", "MX", "es"),
        )
        "AR" -> listOf(
            rss("Clarín", "https://www.clarin.com/rss/lo-ultimo/", "clarin.com", "es"),
            rss("La Nación", "https://www.lanacion.com.ar/arc/outboundfeeds/rss/?outputType=xml", "lanacion.com.ar", "es"),
            site("Página/12", "pagina12.com.ar", "AR", "es"),
        )
        "CO" -> listOf(
            rss("El Tiempo", "https://www.eltiempo.com/rss/colombia.xml", "eltiempo.com", "es"),
            site("El Espectador", "elespectador.com", "CO", "es"),
            site("Semana", "semana.com", "CO", "es"),
        )
        "CL" -> listOf(
            rss("La Tercera", "https://www.latercera.com/arc/outboundfeeds/rss/?outputType=xml", "latercera.com", "es"),
            site("BioBioChile", "biobiochile.cl", "CL", "es"),
            site("Emol", "emol.com", "CL", "es"),
        )
        else -> emptyList()
    }

    /** A language area's shared newsrooms (pan-Arab channels, francophone, German- and Spanish-language). */
    enum class Region { ARAB, FRENCH, GERMAN, SPANISH }

    fun regionFor(code: String): Region? = when (code.uppercase()) {
        "EG", "SA", "AE", "LB" -> Region.ARAB
        "MA" -> Region.ARAB // Arabic and French both: the Arab-world group is the wider one
        "FR", "BE" -> Region.FRENCH
        "DE", "AT", "CH" -> Region.GERMAN
        "ES", "MX", "AR", "CO", "CL" -> Region.SPANISH
        else -> null
    }

    fun forRegion(region: Region, country: String): List<Suggestion> {
        val c = country.uppercase().takeIf { k -> TopicRegistry.COUNTRIES.any { it.code == k } } ?: "US"
        fun ar(name: String, domain: String) = site(name, domain, if (TopicRegistry.country(c).language == "ar") c else "EG", "ar")
        val list = when (region) {
            Region.ARAB -> listOf(
                rss("Al Jazeera · الجزيرة", "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b135-bfdff8b8cab9", "aljazeera.net", "ar"),
                ar("Al Arabiya · العربية", "alarabiya.net"),
                rss("Sky News Arabia · سكاي نيوز عربية", "https://www.skynewsarabia.com/rss", "skynewsarabia.com", "ar"),
                rss("BBC Arabic · بي بي سي عربي", "https://feeds.bbci.co.uk/arabic/rss.xml", "bbc.com", "ar"),
                rss("CNN Arabic · سي إن إن بالعربية", "https://arabic.cnn.com/rss", "arabic.cnn.com", "ar"),
                ar("TRT Arabic · تي آر تي عربي", "trtarabi.com"),
                rss("France 24 Arabic · فرانس 24", "https://www.france24.com/ar/rss", "france24.com", "ar"),
                rss("DW Arabic · دويتشه فيله", "https://rss.dw.com/rdf/rss-ar-all", "dw.com", "ar"),
                rss("Anadolu Arabic · الأناضول", "https://www.aa.com.tr/ar/rss/default?cat=guncel", "aa.com.tr", "ar"),
                ar("Euronews Arabic · يورونيوز", "arabic.euronews.com"),
                ar("Monte Carlo Doualiya · مونت كارلو الدولية", "mc-doualiya.com"),
                rss("Asharq Al-Awsat · الشرق الأوسط", "https://aawsat.com/feed", "aawsat.com", "ar"),
                ar("Asharq News · الشرق", "asharq.com"),
                rss("Independent Arabia · إندبندنت عربية", "https://www.independentarabia.com/rss.xml", "independentarabia.com", "ar"),
                rss("Al-Quds Al-Arabi · القدس العربي", "https://www.alquds.co.uk/feed/", "alquds.co.uk", "ar"),
                ar("Al-Araby Al-Jadeed · العربي الجديد", "alaraby.co.uk"),
                ar("Arabi21 · عربي21", "arabi21.com"),
                ar("Al-Ain News · العين الإخبارية", "al-ain.com"),
                ar("Erem News · إرم نيوز", "eremnews.com"),
                rss("Alhurra · الحرة", "https://www.alhurra.com/feed", "alhurra.com", "ar"),
                ar("Al Mayadeen · الميادين", "almayadeen.net"),
                rss("RT Arabic · آر تي عربي", "https://arabic.rt.com/rss/", "arabic.rt.com", "ar"),
                rss("Al Jazeera English", "https://www.aljazeera.com/xml/rss/all.xml", "aljazeera.com", "en"),
                site("Middle East Eye", "middleeasteye.net", "US", "en"),
                rss("Al-Monitor", "https://www.al-monitor.com/rss", "al-monitor.com", "en"),
                rss("Arab News", "https://www.arabnews.com/rss.xml", "arabnews.com", "en"),
                rss("Asharq Al-Awsat English", "https://english.aawsat.com/feed", "english.aawsat.com", "en"),
                rss("The National", "https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml", "thenationalnews.com", "en"),
                rss("Middle East Monitor", "https://www.middleeastmonitor.com/feed/", "middleeastmonitor.com", "en"),
            )
            Region.FRENCH -> listOf(
                rss("RFI", "https://www.rfi.fr/fr/rss", "rfi.fr", "fr"),
                rss("France 24", "https://www.france24.com/fr/rss", "france24.com", "fr"),
                rss("TV5Monde Info", "https://information.tv5monde.com/rss.xml", "tv5monde.com", "fr"),
                rss("Le Monde Afrique", "https://www.lemonde.fr/afrique/rss_full.xml", "lemonde.fr", "fr"),
                rss("Jeune Afrique", "https://www.jeuneafrique.com/feed/", "jeuneafrique.com", "fr"),
                rss("BBC Afrique", "https://feeds.bbci.co.uk/afrique/rss.xml", "bbc.com", "fr"),
                rss("RFI Afrique", "https://www.rfi.fr/fr/afrique/rss", "rfi.fr", "fr"),
                rss("France 24 Afrique", "https://www.france24.com/fr/afrique/rss", "france24.com", "fr"),
                rss("Le Monde", "https://www.lemonde.fr/rss/une.xml", "lemonde.fr", "fr"),
            )
            Region.GERMAN -> listOf(
                rss("tagesschau", "https://www.tagesschau.de/xml/rss2/", "tagesschau.de", "de"),
                rss("Deutschlandfunk", "https://www.deutschlandfunk.de/nachrichten-100.rss", "deutschlandfunk.de", "de"),
                rss("DW Deutsch", "https://rss.dw.com/rdf/rss-de-all", "dw.com", "de"),
                rss("Der Spiegel", "https://www.spiegel.de/schlagzeilen/index.rss", "spiegel.de", "de"),
                rss("Die Zeit", "https://newsfeed.zeit.de/index", "zeit.de", "de"),
                rss("Süddeutsche Zeitung", "https://rss.sueddeutsche.de/rss/Topthemen", "sueddeutsche.de", "de"),
                rss("NZZ", "https://www.nzz.ch/recent.rss", "nzz.ch", "de"),
                rss("ORF", "https://rss.orf.at/news.xml", "orf.at", "de"),
            )
            Region.SPANISH -> listOf(
                rss("BBC Mundo", "https://feeds.bbci.co.uk/mundo/rss.xml", "bbc.com", "es"),
                rss("El País América", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/section/america/portada", "elpais.com", "es"),
                rss("Infobae", "https://www.infobae.com/arc/outboundfeeds/rss/", "infobae.com", "es"),
                site("CNN en Español", "cnnespanol.cnn.com", if (TopicRegistry.country(c).language == "es") c else "MX", "es"),
                site("Agencia EFE", "efe.com", if (TopicRegistry.country(c).language == "es") c else "ES", "es"),
                rss("DW Español", "https://rss.dw.com/rdf/rss-sp-all", "dw.com", "es"),
                rss("France 24 Español", "https://www.france24.com/es/rss", "france24.com", "es"),
            )
        }
        // A country's own list already offers some of these (Le Monde in France, NZZ in Switzerland).
        val own = forCountry(country).map { it.url }.toSet()
        return list.filter { it.url !in own }
    }

    /**
     * The suggestion as a source: it joins the national tab when it reads in the country's
     * language, and Top Stories when it reads in the app's.
     */
    fun toFeed(s: Suggestion, countryCode: String, appLanguage: String, nowMillis: Long, regional: Boolean = false): FeedSource {
        val topics = buildList {
            if (regional) {
                // A region's channels cover the whole area: Top Stories and International when they
                // read in the app's language, else the national tab (in the country's language).
                when (s.language) {
                    appLanguage -> { add("TOP"); add("INTERNATIONAL") }
                    TopicRegistry.country(countryCode).language -> add(TopicRegistry.NATIONAL_ID)
                    else -> add("INTERNATIONAL")
                }
            } else {
                if (s.language == TopicRegistry.country(countryCode).language) add(TopicRegistry.NATIONAL_ID)
                if (s.language == appLanguage) add("TOP")
                if (isEmpty()) add(TopicRegistry.NATIONAL_ID)
            }
        }
        return FeedSource(
            id = "local_${s.domain.replace(Regex("[^a-z0-9]"), "_")}_${nowMillis.toString().takeLast(4)}",
            name = s.name, url = s.url, domain = s.domain, topics = topics, maxArticles = 8,
            category = "Custom", isCustom = true,
        )
    }
}
