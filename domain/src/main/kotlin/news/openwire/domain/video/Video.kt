// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.video

import news.openwire.domain.model.Article

/** One short vertical news video (a YouTube Short from a news outlet's official channel). */
data class ShortVideo(
    val id: String,
    val title: String,
    val channel: String,
    val channelAvatar: String?,
    val publishedAt: Long,
    val kind: VideoChannel.Kind,
    /** Topics the channel's outlet feeds. */
    val topics: List<String> = emptyList(),
) {
    /** Sharp 9:16 still (720 × 1280). */
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/oar2.jpg"
    val url: String get() = "https://www.youtube.com/shorts/$id"

    /** As a story, so it can be liked, saved and shared like any other. */
    fun asArticle(): Article = Article(
        id = "yt-$id", title = title, source = channel, domain = "youtube.com",
        originalUrl = url, publishedAt = publishedAt, imageUrl = thumbnail, badge = "Video",
    )
}

/** A news outlet's official YouTube channel; its Shorts come from YouTube's public feed (no account, no key). */
data class VideoChannel(
    val id: String,
    val name: String,
    val avatar: String?,
    val kind: Kind,
    /** The outlet's site, matched against the reader's sources (switched off → no videos from it). */
    val domain: String? = null,
    /** Editrove topics the outlet feeds (a local station feeds its city's tab). */
    val topics: List<String> = emptyList(),
) {
    enum class Kind { NEWS, SPORTS, TECH, BUSINESS, ENTERTAINMENT, SCIENCE }

    /** The channel's Shorts-only feed. */
    val feedUrl: String get() = "https://www.youtube.com/feeds/videos.xml?playlist_id=UUSH" + id.removePrefix("UC")
}

/** Which channels feed which tab, and how Top Stories mixes them. */
object VideoCatalog {
    val CHANNELS: List<VideoChannel> = listOf(
        VideoChannel("UCBi2mrWuNuyYy4gbM6fU18Q", "ABC News", "https://yt3.googleusercontent.com/GJ8V0NX6NddGh9bf4zED4tsjPjjBK2hdp5FWHMy09pV7sdSkkE3yEhCRSch4waEb9ZavyUrWfw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "abcnews.go.com", listOf("ENTERTAINMENT", "INTERNATIONAL", "LATEST", "SPORTS", "TOP")),
        VideoChannel("UCeY0bbntWzzVIaj2z3QigXg", "NBC News", "https://yt3.googleusercontent.com/PJj5jtuEOi5UmkFy4IBonj5WcabNcnJAIJe-jZMd1ArwIuVyQxFH_2zryBHwvfv6mJujwRpWDCM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "nbcnews.com", listOf("LATEST", "TOP", "US")),
        VideoChannel("UC8p1vwvWtl6T73JiExfWs1g", "CBS News", "https://yt3.googleusercontent.com/ytc/AIdro_niBFv49gSx4rr1afMZU_Pv7SeuPKO2SMHvv0Ar7OKxM4o=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "cbsnews.com", listOf("LATEST", "TOP", "US")),
        VideoChannel("UCoMdktPbSTixAyNGwb-UYkQ", "Sky News", "https://yt3.googleusercontent.com/dGnkztdrLtXRlzkdqReeL-NES2761xxmNVcJhGKqFpR0vQBoP9XaxnXF95FDpwrjyFr2iJvV8Es=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "news.sky.com", listOf()),
        VideoChannel("UC16niRr50-MSBwiO3YDb3RA", "BBC News", "https://yt3.googleusercontent.com/v4JamQ9B-PUiJHjmZQs9UwTaoLQW8vijJMMpV5QvA2wHQ6iwWM8Q1s6O4jgTl0dtDigVWAi7SA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "bbc.com", listOf()),
        VideoChannel("UCNye-wNBqNL5ZzHSJj3l8Bg", "Al Jazeera English", "https://yt3.googleusercontent.com/XsTga3Nsfc1E6ZgC6HfHfzTG_3zhuZleOnsKxSK2aILMjwkkIm-0vdALFaU-yt0Lw07iLtbSifk=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "aljazeera.com", listOf("INTERNATIONAL", "LATEST", "TOP")),
        VideoChannel("UCupvZG-5ko_eiXAupbDfxWw", "CNN", "https://yt3.googleusercontent.com/wSqAf5WdxsGsl7ZMtlyfz3qKULo_URoFjmKky0gLThm_Jtu2wsVHMu-XzGZPAb-z8zeMBYUUMYA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "cnn.com", listOf()),
        VideoChannel("UCIRYBXDze5krPDzAEOxFGVA", "Guardian News", "https://yt3.googleusercontent.com/ytc/AIdro_nDo1i4jEe90wqB2OCDy57NjTMVUHo1o7vFBjLzVzLeBQI=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "theguardian.com", listOf()),
        VideoChannel("UCknLrEdhRCp1aegoMqRaCZg", "DW News", "https://yt3.googleusercontent.com/NSOdTQTWlqMy8O_j32dx-ftfTCHMOt04Hm7KZ4pfAK6-eQzQSZMWvvss90kG8KQfJ7iNP3phyA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "dw.com", listOf()),
        VideoChannel("UCXIJgqnII2ZOINSWNOGFThA", "Fox News", "https://yt3.googleusercontent.com/G3gLy3HBgiZ21mEt1uzR0VPA6VXpsgJReuD7Z91nHwcgyFVu_QpHNpxuULN1D0YEQBwD0F1HwQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "foxnews.com", listOf("LATEST", "TOP")),
        VideoChannel("UChqUTb7kYRX8-EiaN3XFrSQ", "Reuters", "https://yt3.googleusercontent.com/bCcVVrrV0EhGFJKsSvmeZHA9Y-YzSL9Keqrrr0HWYUPQy3-mVUVNHMbjwt7IoVkpsHt4E6BZ3pM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "reuters.com", listOf()),
        VideoChannel("UC6ZFN9Tx6xh-skXCuRHCDpQ", "PBS NewsHour", "https://yt3.googleusercontent.com/3qIMp79Z-ynhtXTYyNcdybHwvtILVk_jNMKTLbasP8F1UYCdtU-FFjTJPNGQCp5KZuNUQ62n9Gk=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "pbs.org", listOf()),
        VideoChannel("UCiWLfSweyRNmLpgEHekhoAg", "ESPN", "https://yt3.googleusercontent.com/ytc/AIdro_mjPkMeFcQxySKuGOn-eSPaOaTK55KKtjF7LTkkwidfCP1D=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "espn.com", listOf()),
        VideoChannel("UCja8sZ2T4ylIqjggA1Zuukg", "CBS Sports", "https://yt3.googleusercontent.com/jnGO8vkeg1R9IAaIHIej_SS9tzYXtyk54KwbHovEq2No3HfvV6mw2CYVIoFg2ThbFTnkUiSIBw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "cbssports.com", listOf("SPORTS")),
        VideoChannel("UCqZQlzSHbVJrwrn5XvzrzcA", "NBC Sports", "https://yt3.googleusercontent.com/UX0rR-uHFPaCC86o7AVzm7YT6b6Pz51GeQH9O6KtEcE56-7iYfPErXVybWve6aWHIelFV06OCuE=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "nbcsports.com", listOf()),
        VideoChannel("UC9-OpMMVoNP5o10_Iyq7Ndw", "Bleacher Report", "https://yt3.googleusercontent.com/W69_zlpfbzJG5Ai0bMMGdb-4roFiOFuEsi5x_GfqghUWnt8jRB-id1aLqiRFTfcT51TyI5WJfw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "bleacherreport.com", listOf()),
        VideoChannel("UCcw05gGzjLIs5dnxGkQHMvw", "Sky Sports News", "https://yt3.googleusercontent.com/DoESxkAU4oFxt4j2Ge52qp-z8tTshio3hHk_s8JVGGWpHYftkXh0KW8alS9-pfQT-oA7KrNf4A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "skysports.com", listOf()),
        VideoChannel("UCTU_wC79Dgi9rh4e9-baTqA", "Sky Sports", "https://yt3.googleusercontent.com/pNdTb249ls8s1f41xPqWZ-m0d85mmasR3JypJ_vVDBiwjjZOzebP7jECCuwi11bb5zbVjodK4w=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "skysports.com", listOf()),
        VideoChannel("UCddiUEpeqJcYeBxX1IVBKvQ", "The Verge", "https://yt3.googleusercontent.com/ZIj_dq7beCkAkhufNqCid_SjWW4mkv4tqIDtv7_AAKzWdhBWI-rpsRXYXB9X3mB0s0zNzNtYdQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "theverge.com", listOf("AI", "TECHNOLOGY")),
        VideoChannel("UCOmcA3f_RrH6b9NmcNa4tdg", "CNET", "https://yt3.googleusercontent.com/SGoD0SodPuysPRUSFI8jd_eqR-ZCgvxhZk905338O1NUYI5eUqMZ1lQaekFNiDggJh0lA_5kQg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "cnet.com", listOf()),
        VideoChannel("UC-6OW5aJYBFM33zXQlBKPNA", "Engadget", "https://yt3.googleusercontent.com/ytc/AIdro_nAJAaCJcqqNW790kTYUsStZJ1hmC_84Z8sBFPmE-9ld7jz=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "engadget.com", listOf()),
        VideoChannel("UCftwRNsjfRo08xYE31tkiyw", "WIRED", "https://yt3.googleusercontent.com/3xLaRljIs8OZjbxyGnMlpHXtzrxY-keMiy9foklNwa9G5wzB2stC4ND56rZfZAuyLKmRLMm2cik=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "wired.com", listOf()),
        VideoChannel("UCvJJ_dzjViJCoLf5uKUTwoA", "CNBC", "https://yt3.googleusercontent.com/sS9OtcPLL1vQNfGnRudALfNzfpHVy7qwRRKTYF4HEfyICGTfXfHsfP2hhIPgKK0iBxW7p1lH1g=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "cnbc.com", listOf("BUSINESS", "FINANCE", "INTERNATIONAL", "LATEST", "TOP")),
        VideoChannel("UCIALMKvObZNtJ6AmdCLP7Lg", "Bloomberg Television", "https://yt3.googleusercontent.com/4-w44catDebzaDSUWgereql0G5_z5o7VpJXuIYL_eZ3H1vXy0lTmrole9V500jTUXCnQPZ5CuKQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "bloomberg.com", listOf()),
        VideoChannel("UCEAZeUIeJs0IjQiqTCdVSIg", "Yahoo Finance", "https://yt3.googleusercontent.com/Z6BMAACHMcGAFniv21LArJ3gc2nFDpjP4QAmEuTxUi3VZLBbkNH2Hqc3n2rIvCbVVJQ8lnCjsg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "finance.yahoo.com", listOf()),
        VideoChannel("UCK7tptUDHh-RYDsdxO1-5QQ", "The Wall Street Journal", "https://yt3.googleusercontent.com/zbPzRWTIiIg_sNPoAgBf4COWvAoac3vgbiTEu1dfpqH3YPDNvnxMjDtAGftuw5EPx8wfSjMzr2I=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "wsj.com", listOf()),
        VideoChannel("UCoUxsWakJucWg46KW5RsvPw", "Financial Times", "https://yt3.googleusercontent.com/ytc/AIdro_ndI2xm7Vhj_ZGTdd1hmWfv40vS_Pj1K9S31-NSBatSCLZb=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "ft.com", listOf()),
        VideoChannel("UCgRQHK8Ttr1j9xCEpCAlgbQ", "Variety", "https://yt3.googleusercontent.com/ytc/AIdro_lUeys_Ht_DIAe_8BwFUqST-fU7kvBdoh5R7oxBlwzJX8Y=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT, "variety.com", listOf("ENTERTAINMENT")),
        VideoChannel("UCjDsbbzHgTrGc4Ff26TJtsA", "E! News", "https://yt3.googleusercontent.com/ZneyTDwevIWwwNmi4_8DKRkSFxVyGT1QAU7kqxwxjCi4zYGu4Py_Nkt3uSn6-iBnDEHDEW1D_Q=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT, "eonline.com", listOf()),
        VideoChannel("UCGbQJy-531_5vfphay-rChQ", "People", "https://yt3.googleusercontent.com/bMHXmGrmT__If7T5MtiYTFMeYmhzKY-EbZnuLDHXtk3TbgjhtyvUN4ZUYtyC6VQSrapTQT7YE4c=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT, "people.com", listOf()),
        VideoChannel("UCLA_DiR1FfKNvjuUpBHmylQ", "NASA", "https://yt3.googleusercontent.com/eIf5fNPcIcj9ig-wZBeq4stFy1lgjWTW1nLT5dYlFkHZprZ03QBiMcbpwNMB6XSBjrSFGtAGQg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE, "nasa.gov", listOf()),
        VideoChannel("UCt5OA3LingpZBeEyPYmputQ", "New Scientist", "https://yt3.googleusercontent.com/ytc/AIdro_nyxhbSW03VTQ4gwRlGcVuZs6wLzV97VEZhF2a7TezLX9aM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE, "newscientist.com", listOf()),
        VideoChannel("UC_xYMXx_-mAzheKyEtwtCAQ", "Scientific American", "https://yt3.googleusercontent.com/bIUIF4Urp1ovk4GtgYv4683tmm92uBusvQqrmDT_HNKm4X95m1sDol3KCHz-XzLKivK-LFPR8A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE, "scientificamerican.com", listOf()),
        VideoChannel("UCpVm7bg6pXKo1Pr6k5kxG9A", "National Geographic", "https://yt3.googleusercontent.com/4o29ZduZnGmxuRtqNf50bJtzmUtWaeh4W4cYuEXvQBz887R75UMRboX4zbNe71EO4hGgwwnr=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE, "nationalgeographic.com", listOf()),
        VideoChannel("UChLtXXpo4Ge1ReTEboVvTDg", "Global News", "https://yt3.googleusercontent.com/0HLQQOWZ7NCOsJIicFg_3UgyWCg4C_LK27PIAqReZlvkVYY0dp8QzGhp57FON4QeJTH5IrYlCw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "globalnews.ca", listOf("BC", "CANADA", "LATEST", "TOP", "VANCOUVER")),
        VideoChannel("UC4SUWizzKc1tptprBkWjX2Q", "South China Morning Post", "https://yt3.googleusercontent.com/4Q9yos_fKmzf0o36ETS4IZelXBRLwJ5_4pSGvWNbLLhhGNs4RqmAmsYKPkc-Dx_EgVTCxaBOmdU=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "scmp.com", listOf("INTERNATIONAL", "LATEST", "TOP")),
        VideoChannel("UCP2gdh7HfjgCoi8lO50dA5w", "Fortune Magazine", "https://yt3.googleusercontent.com/nBIXPvAjxAnv-X4STe6HSqDjF3DpVBw5NZ-o0TF4_ConYbh4gyNvYX-Ia0nM_ARaaHVtJMD3yA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS, "fortune.com", listOf("BUSINESS", "FINANCE")),
        VideoChannel("UCZ8Sxmkweh65HetaZfR8YuA", "The Hollywood Reporter", "https://yt3.googleusercontent.com/m0t8WJ8m1xbASmYx50KkRPaNoAQcpC9HzLTbJLhfwpaKzCwa7WsaVPODLY1VpCHOELchSisJwg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT, "hollywoodreporter.com", listOf("ENTERTAINMENT")),
        VideoChannel("UCw3SYO_euO0TSPC_m_0Pzgw", "Los Angeles Times", "https://yt3.googleusercontent.com/2hoRUHM9kVY7PjjpnbxLuv6VXawi0Plj-tG4R5HlXtOFPWwMIv20_RMZfEy3ogg2abrtBler=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "latimes.com", listOf("INTERNATIONAL", "US")),
        VideoChannel("UC2a35q7eyzkfoIusBzdH4Hw", "Newsweek", "https://yt3.googleusercontent.com/p4PNCkoT51NTXjZXmqBYoYJlWdlDeVzz1FbqwVnMI-ZmYgWNDqIgr0V5-vm6BzbPgsh15IfD=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "newsweek.com", listOf("LATEST", "US")),
        VideoChannel("UCLXo7UDZvByw2ixzpQCufnA", "Vox", "https://yt3.googleusercontent.com/iak9xjDjDoQHn9UNWSvkobPuxAzK1ApefjPH7HGObwo71AbaiEqdafMjmUJCgOoBFWNKPS2t2eI=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "vox.com", listOf("POLITICS")),
        VideoChannel("UCSrZ3UV4jOidv8ppoVuvW9Q", "euronews", "https://yt3.googleusercontent.com/8MyE7rxMBfLZOpYkJVJFm1C8I9jxbceBbOJS9OhrepZMVGxGV-OEJU-UdLOew_qR_l-knETWeu4=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "euronews.com", listOf("INTERNATIONAL")),
        VideoChannel("UCcyq283he07B7_KUX07mmtA", "Business Insider", "https://yt3.googleusercontent.com/pjDcIrc21oYPCkKpfY1zElDRHhw907VyW4zpuhJP5YCCvZPpyt0de08XM-uECMPQFAsidNK0bw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "businessinsider.com", listOf("BUSINESS", "TECHNOLOGY")),
        VideoChannel("UCi9ACGG8NptsQhaMyezITEw", "Yahoo! Sports", "https://yt3.googleusercontent.com/ASDe6fc-PoWpK0yirVGIlWlz6D46KwEFJ6c_6c9ikMboYK0S-Tr29iN53nZpKMzn5cC8V1R5Ig=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS, "sports.yahoo.com", listOf("SPORTS")),
        VideoChannel("UCXZCJLdBC09xxGZ6gcdrc6A", "OpenAI", "https://yt3.googleusercontent.com/MopgmVAFV9BqlzOJ-UINtmutvEPcNe5IbKMmP_4vZZo3vnJXcZGtybUBsXaEVxkmxKyGqX9R=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "openai.com", listOf("AI")),
        VideoChannel("UCIFD9TMCLBXvJJMe2OpCtCA", "WTOP News", "https://yt3.googleusercontent.com/vWeBWuhGOV_7nE_o169pNbrSCT3qdIuOOmp20igEt4GT6itRsDZHvIEGmR41LDtYdvdVo-_14A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS, "wtop.com", listOf("WASHINGTON_DC")),
        VideoChannel("UCAY_M9HyJb8oMKPV1utQQyA", "TechRadar", "https://yt3.googleusercontent.com/0zm_XVRNEbjoYZ66yjL2BdZxEFFMKLDAMneyy5WNXnmYz2BVGYrz2AXZPL_OgWXd_kQw9eDr1Kk=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "techradar.com", listOf()),
        VideoChannel("UCL8Nxsa1LB9DrMTHtt3IKiw", "Mashable", "https://yt3.googleusercontent.com/DkwGNTtaOJN8J1D_FCNwHwTWDJa2IVrtT0QHGrFDNE6PTC1T6RlbppkdIxv5590EPa0jdUdZ7g=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "mashable.com", listOf()),
        VideoChannel("UCgyqtNWZmIxTx3b6OxTSALw", "Android Authority", "https://yt3.googleusercontent.com/1EiH0cEXTu1pTOf_EFtXYgjWV5OgmlBUvDh1ghWBJ8wP1H6_EHvDgOwFNXXEZiyJa1FPYbo25A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "androidauthority.com", listOf()),
        VideoChannel("UCzIO0iX4yKW2P4NkmmKq1PA", "9to5Google", "https://yt3.googleusercontent.com/FRA_4XHHggh2Oe8iL7F7xFDnIJh1ceOWQqGcw1fsoUhf3K7S2Kua9Eaku47SX58iW60seC6TrbU=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "9to5google.com", listOf()),
        VideoChannel("UCRhADYLTpsb0JA-uaLWovGw", "PCMag", "https://yt3.googleusercontent.com/cUp-DHH4z550JI9fdZylUwkWy5ma3UE2oHiLRIF0SwSbRQCVvitot1xDGhoGjDwKH7t9cfwYwg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "pcmag.com", listOf()),
        VideoChannel("UC8wXC0ZCfGt3HaVLy_fdTQw", "Digital Trends", "https://yt3.googleusercontent.com/ytc/AIdro_kNubMg5M7TC0RNRPLFtc-OwBejUR6wTVKIu38AsMVexwY=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH, "digitaltrends.com", listOf()),
    )

    /** The kinds a tab shows. Top Stories (and Latest) mix every flavour; others keep to theirs. */
    fun kindsFor(topicId: String): List<VideoChannel.Kind> = when (topicId.uppercase()) {
        "TOP", "LATEST" -> listOf(VideoChannel.Kind.NEWS, VideoChannel.Kind.SPORTS, VideoChannel.Kind.TECH, VideoChannel.Kind.BUSINESS, VideoChannel.Kind.ENTERTAINMENT, VideoChannel.Kind.NEWS)
        "SPORTS" -> listOf(VideoChannel.Kind.SPORTS)
        "TECHNOLOGY", "AI" -> listOf(VideoChannel.Kind.TECH)
        "BUSINESS", "FINANCE" -> listOf(VideoChannel.Kind.BUSINESS)
        "ENTERTAINMENT", "LIFESTYLE" -> listOf(VideoChannel.Kind.ENTERTAINMENT)
        "SCIENCE" -> listOf(VideoChannel.Kind.SCIENCE)
        else -> listOf(VideoChannel.Kind.NEWS) // US, Politics, International, local and custom topics
    }

    /**
     * The channels a tab draws on: those of its kind and those whose outlet feeds the topic, minus
     * any whose outlet the reader switched off ([switchedOff] tells by site).
     */
    fun channelsFor(topicId: String, switchedOff: (String) -> Boolean = { false }): List<VideoChannel> {
        val kinds = kindsFor(topicId).toSet()
        return CHANNELS.filter { (it.kind in kinds || topicId.uppercase() in it.topics) && (it.domain == null || !switchedOff(it.domain)) }
    }

    /** How far back a carousel reaches. */
    const val WINDOW_MS = 7 * 24 * 3_600_000L

    /**
     * The carousel's order: newest first within each kind, kinds taking turns (Top Stories:
     * news, sports, tech, business, entertainment, news…), no channel twice in a row, the last
     * week only, [max] in all.
     */
    fun mix(topicId: String, videos: List<ShortVideo>, now: Long, max: Int = 40): List<ShortVideo> {
        val recent = videos.filter { now - it.publishedAt < WINDOW_MS }.distinctBy { it.id }
        val pattern = kindsFor(topicId)
        // A video of another kind (an outlet that feeds this topic) joins the tab's main kind.
        fun poolOf(v: ShortVideo) = when { v.kind in pattern -> v.kind; topicId.uppercase() in v.topics -> pattern.first(); else -> null }
        val pools = pattern.distinct().associateWith { k -> recent.filter { poolOf(it) == k }.sortedByDescending { it.publishedAt }.toMutableList() }
        val out = ArrayList<ShortVideo>()
        var i = 0
        while (out.size < max && pools.values.any { it.isNotEmpty() }) {
            val pool = pools.getValue(pattern[i % pattern.size])
            i++
            if (pool.isEmpty()) continue
            val pick = pool.firstOrNull { v -> out.lastOrNull()?.channel != v.channel } ?: pool.first()
            pool.remove(pick)
            out += pick
        }
        return out
    }
}
