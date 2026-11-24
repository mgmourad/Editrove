// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A live news channel. [stream] is the broadcaster's own public HLS stream, played in the
 * app; channels that publish none link to their official live page instead ([webUrl]),
 * which opens outside Editrove (no embedded third-party players, no trackers).
 */
data class Channel(
    val id: String,
    val name: String,
    val language: String,
    val domain: String,
    val stream: String? = null,
    val webUrl: String? = null,
) {
    val playsInApp: Boolean get() = stream != null
}

/** A radio station: a direct audio stream (MP3/AAC/HLS). */
data class Station(
    val id: String,
    val name: String,
    val language: String?,
    val url: String,
    val domain: String? = null,
    val favicon: String? = null,
    val country: String? = null,
    val tags: List<String> = emptyList(),
)

object LiveCatalog {

    private fun yt(handle: String) = "https://www.youtube.com/@$handle/live"

    /** Broadcasters' own streams, checked to be public. */
    val CHANNELS: List<Channel> = listOf(
        // English
        Channel("aje", "Al Jazeera English", "en", "aljazeera.com", "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8"),
        Channel("f24en", "France 24 English", "en", "france24.com", webUrl = yt("France24_en")),
        Channel("dwen", "DW News", "en", "dw.com", "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8"),
        Channel("cbs", "CBS News 24/7", "en", "cbsnews.com", "https://cbsn-us.cbsnstream.cbsnews.com/out/v1/55a8648e8f134e82a470f83d562deeca/master.m3u8"),
        Channel("bloomberg", "Bloomberg Television", "en", "bloomberg.com", "https://bloomberg.com/media-manifest/streams/us.m3u8"),
        Channel("trtworld", "TRT World", "en", "trtworld.com", "https://tv-trtworld.medya.trt.com.tr/master.m3u8"),
        Channel("cna", "CNA", "en", "channelnewsasia.com", "https://d2e1asnsl7br7b.cloudfront.net/7782e205e72f43aeb4a48ec97f66ebbe/index.m3u8"),
        Channel("nhk", "NHK World-Japan", "en", "nhk.or.jp", webUrl = yt("NHKWORLDJAPAN")),
        Channel("livenow", "LiveNOW from FOX", "en", "livenowfox.com", "https://fox-foxnewsnow-vizio.amagi.tv/playlist.m3u8"),
        Channel("sky", "Sky News", "en", "news.sky.com", webUrl = yt("SkyNews")),
        Channel("nbc", "NBC News NOW", "en", "nbcnews.com", webUrl = yt("NBCNews")),
        Channel("abc", "ABC News Live", "en", "abcnews.go.com", webUrl = yt("ABCNews")),
        Channel("euronews", "Euronews", "en", "euronews.com", webUrl = yt("euronews")),
        // Arabic
        Channel("aja", "Al Jazeera · الجزيرة", "ar", "aljazeera.net", "https://live-hls-web-aja.getaj.net/AJA/index.m3u8"),
        Channel("alarabiya", "Al Arabiya · العربية", "ar", "alarabiya.net", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8"),
        Channel("sna", "Sky News Arabia · سكاي نيوز عربية", "ar", "skynewsarabia.com", "https://stream.skynewsarabia.com/hls/sna.m3u8"),
        Channel("f24ar", "France 24 Arabic · فرانس 24", "ar", "france24.com", webUrl = yt("France24_ar")),
        Channel("dwar", "DW Arabic · دي دبليو عربية", "ar", "dw.com", "https://dwamdstream103.akamaized.net/hls/live/2015526/dwstream103/index.m3u8"),
        Channel("trtarabi", "TRT Arabi · تي آر تي عربي", "ar", "trtarabi.com", "https://tv-trtarabi.medya.trt.com.tr/master.m3u8"),
        Channel("asharq", "Asharq News · الشرق", "ar", "asharq.com", webUrl = yt("AsharqNews")),
        Channel("bbcar", "BBC News Arabic · بي بي سي عربي", "ar", "bbc.com", webUrl = "https://www.youtube.com/bbcarabic/live"),
        Channel("alqahera", "Al Qahera News · القاهرة الإخبارية", "ar", "alqaheranews.net", webUrl = yt("AlQaheraNews")),
        // French
        Channel("f24fr", "France 24", "fr", "france24.com", webUrl = yt("FRANCE24")),
        Channel("euronewsfr", "Euronews en français", "fr", "fr.euronews.com", webUrl = yt("euronewsfr")),
        // Spanish
        Channel("f24es", "France 24 Español", "es", "france24.com", webUrl = yt("France24_es")),
        Channel("dwes", "DW Español", "es", "dw.com", "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8"),
        Channel("rtve24h", "RTVE Canal 24 Horas", "es", "rtve.es", webUrl = yt("rtvenoticias")),
        // German
        Channel("tagesschau24", "tagesschau24", "de", "tagesschau.de", "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8"),
    )

    /** News and talk radio, checked to stream. */
    val STATIONS: List<Station> = listOf(
        Station("bbcws", "BBC World Service", "en", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "bbc.co.uk"),
        Station("npr", "NPR News", "en", "https://npr-ice.streamguys1.com/live.mp3", "npr.org"),
        Station("wnyc", "WNYC 93.9 FM", "en", "https://fm939.wnyc.org/wnycfm", "wnyc.org"),
        Station("cbc", "CBC Radio One", "en", "https://cbcradiolive.akamaized.net/hls/live/2041057/ES_R1ETR/master.m3u8", "cbc.ca"),
        Station("bbcar", "BBC Arabic Radio · بي بي سي عربي", "ar", "https://stream.live.vc.bbcmedia.co.uk/bbc_arabic_radio", "bbc.com"),
        Station("mcd", "Monte Carlo Doualiya · مونت كارلو الدولية", "ar", "https://montecarlodoualiya128k.ice.infomaniak.ch/mc-doualiya.mp3", "mc-doualiya.com"),
        Station("alarabiyafm", "Al Arabiya FM · العربية إف إم", "ar", "https://fm.alarabiya.net/fm/myStream/playlist.m3u8", "alarabiya.net"),
        Station("alarabyradio", "Al-Araby Radio · راديو العربي", "ar", "https://l3.itworkscdn.net/alarabyradiolive/alarabyradio_audio/icecast.audio", "alaraby.co.uk"),
        Station("franceinfo", "franceinfo", "fr", "https://icecast.radiofrance.fr/franceinfo-midfi.mp3", "francetvinfo.fr"),
        Station("rfi", "RFI Monde", "fr", "https://rfimonde64k.ice.infomaniak.ch/rfimonde-64.mp3", "rfi.fr"),
        Station("dlf", "Deutschlandfunk", "de", "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3", "deutschlandfunk.de"),
        Station("ser", "Cadena SER", "es", "https://playerservices.streamtheworld.com/api/livestream-redirect/CADENASER.mp3", "cadenaser.com"),
    )

    /** [items] with Editrove's language first, then English, then the rest. */
    fun <T> ordered(items: List<T>, appLanguage: String, language: (T) -> String?): List<T> =
        items.sortedBy { when (language(it)) { appLanguage -> 0; "en" -> 1; else -> 2 } }

    /** Channels in Editrove's language first; within a language, those that play in the app first. */
    fun channels(appLanguage: String): List<Channel> =
        CHANNELS.sortedWith(compareBy<Channel>({ when (it.language) { appLanguage -> 0; "en" -> 1; else -> 2 } }, { if (it.playsInApp) 0 else 1 }))
}

/** The community Radio Browser directory (radio-browser.info): open, keyless, no tracking. */
object RadioBrowser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Servers to try in turn (the directory runs several mirrors). */
    val SERVERS = listOf("https://de1.api.radio-browser.info", "https://fi1.api.radio-browser.info", "https://at1.api.radio-browser.info")

    fun countryPath(country: String, limit: Int = 30) =
        "/json/stations/search?countrycode=${country.uppercase()}&order=votes&reverse=true&hidebroken=true&limit=$limit"

    fun searchPath(name: String, limit: Int = 30) =
        "/json/stations/search?name=${java.net.URLEncoder.encode(name.trim(), "UTF-8")}&order=votes&reverse=true&hidebroken=true&limit=$limit"

    private val LANGS = mapOf("english" to "en", "arabic" to "ar", "french" to "fr", "spanish" to "es", "german" to "de")

    fun parse(body: String): List<Station> {
        val arr = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            val url = s("url_resolved") ?: s("url") ?: return@mapNotNull null
            if (o["lastcheckok"]?.jsonPrimitive?.intOrNull == 0) return@mapNotNull null
            if (!url.startsWith("http")) return@mapNotNull null
            val name = s("name") ?: return@mapNotNull null
            Station(
                id = "rb_" + (s("stationuuid") ?: url.hashCode().toString()),
                name = name.replace(Regex("\\s+"), " "),
                language = s("language")?.split(',')?.firstNotNullOfOrNull { LANGS[it.trim().lowercase()] },
                url = url,
                domain = s("homepage")?.let { h -> runCatching { java.net.URI(h).host?.removePrefix("www.") }.getOrNull() },
                favicon = s("favicon")?.takeIf { it.startsWith("https") },
                country = s("countrycode"),
                tags = s("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            )
        }.distinctBy { it.name.lowercase() }
    }
}

/**
 * Large brand art for each channel's thumbnail: the broadcaster's own share card (1200 × 630)
 * where its site publishes one, else its official channel avatar (900 px). Checked by eye; a
 * missing or broken one falls back to the publisher-logo lookup.
 */
object ChannelArt {
    private val ART = mapOf(
        "abc" to "https://s.abcnews.com/images/US/abc_news_default_2000x2000_update_16x9_992.jpg",
        "aja" to "https://www.aljazeera.net/images/aljazeera-arabic-social-preview-v2.jpg",
        "aje" to "https://www.aljazeera.com/images/logo_aje_social.png",
        "alarabiya" to "https://yt3.googleusercontent.com/GoZXY6BOD_V0vV3ok3fAzIbNMx3og4z2Jd2Up0BnocvBn35rMB4NG3vMOdzOpQ4-HFBUfDQ-JQ=s900-c-k-c0x00ffffff-no-rj",
        "alqahera" to "https://alqaheranews.net/assets/imgs/post-default.jpg",
        "asharq" to "https://yt3.googleusercontent.com/ytc/AIdro_kZQjNcecj0_Z8VHxjNt6zKzLtCPpnNRF0rSjyNXm4AZg=s900-c-k-c0x00ffffff-no-rj",
        "bloomberg" to "https://yt3.googleusercontent.com/4-w44catDebzaDSUWgereql0G5_z5o7VpJXuIYL_eZ3H1vXy0lTmrole9V500jTUXCnQPZ5CuKQ=s900-c-k-c0x00ffffff-no-rj",
        "cbs" to "https://assets1.cbsnewsstatic.com/hub/i/2022/01/22/4fc70efe-a2d7-4eb3-8f23-05db514ef3bb/news-slate-stream.jpg",
        "cna" to "https://yt3.googleusercontent.com/-rE0W1GTFDHNy9pd2pkbItVQM6Jcp_hWwD4pUURoArQ2TvZ1TFernWqXFGJXKIYTN5IQtnxkUA=s900-c-k-c0x00ffffff-no-rj",
        "dwar" to "https://yt3.googleusercontent.com/1diREnd5d5mj0zM8AmZYfuccwIppbzSBz0ERjzRl9kydXSPKREif3GyigSpYq2P_d8GH0RMKj5I=s900-c-k-c0x00ffffff-no-rj",
        "dwen" to "https://yt3.googleusercontent.com/NSOdTQTWlqMy8O_j32dx-ftfTCHMOt04Hm7KZ4pfAK6-eQzQSZMWvvss90kG8KQfJ7iNP3phyA=s900-c-k-c0x00ffffff-no-rj",
        "dwes" to "https://yt3.googleusercontent.com/TOHGshxAMW2Meq-N9EWB3T71en-ziaWoiFYWcjrhxQpOW4Wog3HwrAjDZIeCvj1q9VLPt_YsCA=s900-c-k-c0x00ffffff-no-rj",
        "euronews" to "https://yt3.googleusercontent.com/8MyE7rxMBfLZOpYkJVJFm1C8I9jxbceBbOJS9OhrepZMVGxGV-OEJU-UdLOew_qR_l-knETWeu4=s900-c-k-c0x00ffffff-no-rj",
        "euronewsfr" to "https://yt3.googleusercontent.com/U0I3pGqWPE2d3E7LqMnjZ_vsH6qZkCv5606viZpjjK7CzkvcEmTa94NUnOnYZ-6CZWkDRY6H_w=s900-c-k-c0x00ffffff-no-rj",
        "f24ar" to "https://yt3.googleusercontent.com/ytc/AIdro_n_QTJ5-GLGnftQFkb7Kp36W2eBH3H-7ZqTQxzFkkqGrIE=s900-c-k-c0x00ffffff-no-rj",
        "f24en" to "https://yt3.googleusercontent.com/ytc/AIdro_n_QTJ5-GLGnftQFkb7Kp36W2eBH3H-7ZqTQxzFkkqGrIE=s900-c-k-c0x00ffffff-no-rj",
        "f24es" to "https://yt3.googleusercontent.com/ytc/AIdro_kzqAgL2TukPL1ijpuPdoOgU75Av9cdQVf0mahAZSySyA=s900-c-k-c0x00ffffff-no-rj",
        "f24fr" to "https://yt3.googleusercontent.com/ytc/AIdro_k9aU_SRhYAWJjQ6AO7uzQDZE5mb7gmv4synLrC7hEWGjE=s900-c-k-c0x00ffffff-no-rj",
        "livenow" to "https://images.foxtv.com/www.livenowfox.com/img/1200/630/default-image.png?ve=1&tl=1",
        "nbc" to "https://media-cldnry.s-nbcnews.com/image/upload/t_nbcnews-fp-1200-630,f_auto,q_auto:best/v1696280772/newsgroup-logos/nbcnews/social/primary-color-1680x840.png",
        "nhk" to "https://yt3.googleusercontent.com/ytc/AIdro_m9YDtPWqgZW91_-QTdsVWLbnR0ZQ1BaHX38ZcSRVGnfd0=s900-c-k-c0x00ffffff-no-rj",
        "rtve24h" to "https://www.rtve.es/contenidos/imagenes/rtve_facebook.jpg",
        "sky" to "https://yt3.googleusercontent.com/dGnkztdrLtXRlzkdqReeL-NES2761xxmNVcJhGKqFpR0vQBoP9XaxnXF95FDpwrjyFr2iJvV8Es=s900-c-k-c0x00ffffff-no-rj",
        "sna" to "https://yt3.googleusercontent.com/C_tTYi9T3TlVewh6A5Yon_NOd3XxSjAduqRCqhaUcHkNGt67zyD0Cb6jJePYZTUMw-0VQt9TCA=s900-c-k-c0x00ffffff-no-rj",
        "tagesschau24" to "https://images.tagesschau.de/image/89045d82-5cd5-46ad-8f91-73911add30ee/AAABlpITK5s/AAABnSSvrFg/16x9-big/tagesschau-logo-100.jpg?width=1280",
        "trtarabi" to "https://yt3.googleusercontent.com/gYgMDstQrKquZlKMpynGak3fik58-0UdhvIFvRNk8Z1KAGDzn6Kuz8uULtWRwVM3bJcqBuWfxQ=s900-c-k-c0x00ffffff-no-rj",
        "trtworld" to "https://yt3.googleusercontent.com/luLQWmGFG4iDC1U_2JlzZ1mquci_sUdfZfFl4eWgBkDpW6tvZT0MEA4c4JebJdi9hCo518q1=s900-c-k-c0x00ffffff-no-rj",
    )

    fun of(channelId: String): String? = ART[channelId]
}
