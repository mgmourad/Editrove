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
data class VideoChannel(val id: String, val name: String, val avatar: String?, val kind: Kind) {
    enum class Kind { NEWS, SPORTS, TECH, BUSINESS, ENTERTAINMENT, SCIENCE }

    /** The channel's Shorts-only feed. */
    val feedUrl: String get() = "https://www.youtube.com/feeds/videos.xml?playlist_id=UUSH" + id.removePrefix("UC")
}

/** Which channels feed which tab, and how Top Stories mixes them. */
object VideoCatalog {
    val CHANNELS: List<VideoChannel> = listOf(
        VideoChannel("UCBi2mrWuNuyYy4gbM6fU18Q", "ABC News", "https://yt3.googleusercontent.com/GJ8V0NX6NddGh9bf4zED4tsjPjjBK2hdp5FWHMy09pV7sdSkkE3yEhCRSch4waEb9ZavyUrWfw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCeY0bbntWzzVIaj2z3QigXg", "NBC News", "https://yt3.googleusercontent.com/PJj5jtuEOi5UmkFy4IBonj5WcabNcnJAIJe-jZMd1ArwIuVyQxFH_2zryBHwvfv6mJujwRpWDCM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UC8p1vwvWtl6T73JiExfWs1g", "CBS News", "https://yt3.googleusercontent.com/ytc/AIdro_niBFv49gSx4rr1afMZU_Pv7SeuPKO2SMHvv0Ar7OKxM4o=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCoMdktPbSTixAyNGwb-UYkQ", "Sky News", "https://yt3.googleusercontent.com/dGnkztdrLtXRlzkdqReeL-NES2761xxmNVcJhGKqFpR0vQBoP9XaxnXF95FDpwrjyFr2iJvV8Es=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UC16niRr50-MSBwiO3YDb3RA", "BBC News", "https://yt3.googleusercontent.com/v4JamQ9B-PUiJHjmZQs9UwTaoLQW8vijJMMpV5QvA2wHQ6iwWM8Q1s6O4jgTl0dtDigVWAi7SA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCNye-wNBqNL5ZzHSJj3l8Bg", "Al Jazeera English", "https://yt3.googleusercontent.com/XsTga3Nsfc1E6ZgC6HfHfzTG_3zhuZleOnsKxSK2aILMjwkkIm-0vdALFaU-yt0Lw07iLtbSifk=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCupvZG-5ko_eiXAupbDfxWw", "CNN", "https://yt3.googleusercontent.com/wSqAf5WdxsGsl7ZMtlyfz3qKULo_URoFjmKky0gLThm_Jtu2wsVHMu-XzGZPAb-z8zeMBYUUMYA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCIRYBXDze5krPDzAEOxFGVA", "Guardian News", "https://yt3.googleusercontent.com/ytc/AIdro_nDo1i4jEe90wqB2OCDy57NjTMVUHo1o7vFBjLzVzLeBQI=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCknLrEdhRCp1aegoMqRaCZg", "DW News", "https://yt3.googleusercontent.com/NSOdTQTWlqMy8O_j32dx-ftfTCHMOt04Hm7KZ4pfAK6-eQzQSZMWvvss90kG8KQfJ7iNP3phyA=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCXIJgqnII2ZOINSWNOGFThA", "Fox News", "https://yt3.googleusercontent.com/G3gLy3HBgiZ21mEt1uzR0VPA6VXpsgJReuD7Z91nHwcgyFVu_QpHNpxuULN1D0YEQBwD0F1HwQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UChqUTb7kYRX8-EiaN3XFrSQ", "Reuters", "https://yt3.googleusercontent.com/bCcVVrrV0EhGFJKsSvmeZHA9Y-YzSL9Keqrrr0HWYUPQy3-mVUVNHMbjwt7IoVkpsHt4E6BZ3pM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UC6ZFN9Tx6xh-skXCuRHCDpQ", "PBS NewsHour", "https://yt3.googleusercontent.com/3qIMp79Z-ynhtXTYyNcdybHwvtILVk_jNMKTLbasP8F1UYCdtU-FFjTJPNGQCp5KZuNUQ62n9Gk=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.NEWS),
        VideoChannel("UCiWLfSweyRNmLpgEHekhoAg", "ESPN", "https://yt3.googleusercontent.com/ytc/AIdro_mjPkMeFcQxySKuGOn-eSPaOaTK55KKtjF7LTkkwidfCP1D=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UCja8sZ2T4ylIqjggA1Zuukg", "CBS Sports", "https://yt3.googleusercontent.com/jnGO8vkeg1R9IAaIHIej_SS9tzYXtyk54KwbHovEq2No3HfvV6mw2CYVIoFg2ThbFTnkUiSIBw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UCqZQlzSHbVJrwrn5XvzrzcA", "NBC Sports", "https://yt3.googleusercontent.com/UX0rR-uHFPaCC86o7AVzm7YT6b6Pz51GeQH9O6KtEcE56-7iYfPErXVybWve6aWHIelFV06OCuE=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UC9-OpMMVoNP5o10_Iyq7Ndw", "Bleacher Report", "https://yt3.googleusercontent.com/W69_zlpfbzJG5Ai0bMMGdb-4roFiOFuEsi5x_GfqghUWnt8jRB-id1aLqiRFTfcT51TyI5WJfw=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UCcw05gGzjLIs5dnxGkQHMvw", "Sky Sports News", "https://yt3.googleusercontent.com/DoESxkAU4oFxt4j2Ge52qp-z8tTshio3hHk_s8JVGGWpHYftkXh0KW8alS9-pfQT-oA7KrNf4A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UCTU_wC79Dgi9rh4e9-baTqA", "Sky Sports", "https://yt3.googleusercontent.com/pNdTb249ls8s1f41xPqWZ-m0d85mmasR3JypJ_vVDBiwjjZOzebP7jECCuwi11bb5zbVjodK4w=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SPORTS),
        VideoChannel("UCddiUEpeqJcYeBxX1IVBKvQ", "The Verge", "https://yt3.googleusercontent.com/ZIj_dq7beCkAkhufNqCid_SjWW4mkv4tqIDtv7_AAKzWdhBWI-rpsRXYXB9X3mB0s0zNzNtYdQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH),
        VideoChannel("UCOmcA3f_RrH6b9NmcNa4tdg", "CNET", "https://yt3.googleusercontent.com/SGoD0SodPuysPRUSFI8jd_eqR-ZCgvxhZk905338O1NUYI5eUqMZ1lQaekFNiDggJh0lA_5kQg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH),
        VideoChannel("UC-6OW5aJYBFM33zXQlBKPNA", "Engadget", "https://yt3.googleusercontent.com/ytc/AIdro_nAJAaCJcqqNW790kTYUsStZJ1hmC_84Z8sBFPmE-9ld7jz=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH),
        VideoChannel("UCftwRNsjfRo08xYE31tkiyw", "WIRED", "https://yt3.googleusercontent.com/3xLaRljIs8OZjbxyGnMlpHXtzrxY-keMiy9foklNwa9G5wzB2stC4ND56rZfZAuyLKmRLMm2cik=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.TECH),
        VideoChannel("UCvJJ_dzjViJCoLf5uKUTwoA", "CNBC", "https://yt3.googleusercontent.com/sS9OtcPLL1vQNfGnRudALfNzfpHVy7qwRRKTYF4HEfyICGTfXfHsfP2hhIPgKK0iBxW7p1lH1g=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS),
        VideoChannel("UCIALMKvObZNtJ6AmdCLP7Lg", "Bloomberg Television", "https://yt3.googleusercontent.com/4-w44catDebzaDSUWgereql0G5_z5o7VpJXuIYL_eZ3H1vXy0lTmrole9V500jTUXCnQPZ5CuKQ=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS),
        VideoChannel("UCEAZeUIeJs0IjQiqTCdVSIg", "Yahoo Finance", "https://yt3.googleusercontent.com/Z6BMAACHMcGAFniv21LArJ3gc2nFDpjP4QAmEuTxUi3VZLBbkNH2Hqc3n2rIvCbVVJQ8lnCjsg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS),
        VideoChannel("UCK7tptUDHh-RYDsdxO1-5QQ", "The Wall Street Journal", "https://yt3.googleusercontent.com/zbPzRWTIiIg_sNPoAgBf4COWvAoac3vgbiTEu1dfpqH3YPDNvnxMjDtAGftuw5EPx8wfSjMzr2I=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS),
        VideoChannel("UCoUxsWakJucWg46KW5RsvPw", "Financial Times", "https://yt3.googleusercontent.com/ytc/AIdro_ndI2xm7Vhj_ZGTdd1hmWfv40vS_Pj1K9S31-NSBatSCLZb=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.BUSINESS),
        VideoChannel("UCgRQHK8Ttr1j9xCEpCAlgbQ", "Variety", "https://yt3.googleusercontent.com/ytc/AIdro_lUeys_Ht_DIAe_8BwFUqST-fU7kvBdoh5R7oxBlwzJX8Y=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT),
        VideoChannel("UCjDsbbzHgTrGc4Ff26TJtsA", "E! News", "https://yt3.googleusercontent.com/ZneyTDwevIWwwNmi4_8DKRkSFxVyGT1QAU7kqxwxjCi4zYGu4Py_Nkt3uSn6-iBnDEHDEW1D_Q=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT),
        VideoChannel("UCGbQJy-531_5vfphay-rChQ", "People", "https://yt3.googleusercontent.com/bMHXmGrmT__If7T5MtiYTFMeYmhzKY-EbZnuLDHXtk3TbgjhtyvUN4ZUYtyC6VQSrapTQT7YE4c=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.ENTERTAINMENT),
        VideoChannel("UCLA_DiR1FfKNvjuUpBHmylQ", "NASA", "https://yt3.googleusercontent.com/eIf5fNPcIcj9ig-wZBeq4stFy1lgjWTW1nLT5dYlFkHZprZ03QBiMcbpwNMB6XSBjrSFGtAGQg=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE),
        VideoChannel("UCt5OA3LingpZBeEyPYmputQ", "New Scientist", "https://yt3.googleusercontent.com/ytc/AIdro_nyxhbSW03VTQ4gwRlGcVuZs6wLzV97VEZhF2a7TezLX9aM=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE),
        VideoChannel("UC_xYMXx_-mAzheKyEtwtCAQ", "Scientific American", "https://yt3.googleusercontent.com/bIUIF4Urp1ovk4GtgYv4683tmm92uBusvQqrmDT_HNKm4X95m1sDol3KCHz-XzLKivK-LFPR8A=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE),
        VideoChannel("UCpVm7bg6pXKo1Pr6k5kxG9A", "National Geographic", "https://yt3.googleusercontent.com/4o29ZduZnGmxuRtqNf50bJtzmUtWaeh4W4cYuEXvQBz887R75UMRboX4zbNe71EO4hGgwwnr=s176-c-k-c0x00ffffff-no-rj", VideoChannel.Kind.SCIENCE),
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

    fun channelsFor(topicId: String): List<VideoChannel> = kindsFor(topicId).distinct().flatMap { k -> CHANNELS.filter { it.kind == k } }

    /**
     * The carousel's order: newest first within each kind, kinds taking turns (Top Stories:
     * news, sports, tech, business, entertainment, news…), no channel twice in a row, the last
     * two days only, [max] in all.
     */
    fun mix(topicId: String, videos: List<ShortVideo>, now: Long, max: Int = 16): List<ShortVideo> {
        val recent = videos.filter { now - it.publishedAt < 48 * 3_600_000L }.distinctBy { it.id }
        val pattern = kindsFor(topicId)
        val pools = pattern.distinct().associateWith { k -> recent.filter { it.kind == k }.sortedByDescending { it.publishedAt }.toMutableList() }
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
