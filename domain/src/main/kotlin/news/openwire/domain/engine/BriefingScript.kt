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

    /** One story as the narrator will read it. */
    data class Story(val title: String, val source: String?, val summary: String? = null, val body: String? = null)

    fun intro(topic: String, count: Int, detail: BriefingDetail, now: LocalDateTime): String {
        val greeting = when (now.hour) { in 4..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        val day = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
        val what = when (detail) {
            BriefingDetail.HEADLINES -> "the top $count headlines"
            BriefingDetail.SUMMARIES -> "$count stories, with the key details on each"
            BriefingDetail.FULL -> "$count stories, read in full"
        }
        return "$greeting. It's $day, and this is your ${topic.trim()} briefing from Editrove. Here are $what."
    }

    private val LEAD = listOf("Our top story.", "We begin with our lead story.", "First up.")
    private val NEXT_HEADLINES = listOf("Next.", "Also making news.", "Meanwhile.", "In other news.", "Elsewhere.", "Another headline we're following.")
    private val NEXT_DETAILED = listOf(
        "Turning now to another story.", "In other news today.", "Moving on.", "Now to a story we're following closely.",
        "Elsewhere in the news.", "Our next story.", "Also developing today.",
    )
    private val FINAL = listOf("And finally.", "One last story before we go.", "Finally today.")
    private val INTO_SUMMARY = listOf("Here's what we know.", "The details.", "Here's the latest.")

    /** The hand-off into story [index] (0-based) of [total]. */
    fun lead(index: Int, total: Int, detail: BriefingDetail): String = when {
        index == 0 -> LEAD[0]
        index == total - 1 && total > 2 -> FINAL[index % FINAL.size]
        detail == BriefingDetail.HEADLINES -> NEXT_HEADLINES[(index - 1) % NEXT_HEADLINES.size]
        else -> NEXT_DETAILED[(index - 1) % NEXT_DETAILED.size]
    }

    private fun attribution(source: String?, index: Int): String? = source?.takeIf { it.isNotBlank() }?.let {
        listOf("$it reports.", "According to $it.", "From $it.", "$it has the story.")[index % 4]
    }

    /** Everything said for one story, as separate lines (natural pauses between them). */
    fun story(index: Int, total: Int, story: Story, detail: BriefingDetail): List<String> = buildList {
        add(lead(index, total, detail))
        add(clean(story.title).let { if (it.endsWith(".") || it.endsWith("?") || it.endsWith("!")) it else "$it." })
        when (detail) {
            BriefingDetail.HEADLINES -> attribution(story.source, index)?.let(::add)
            BriefingDetail.SUMMARIES -> {
                attribution(story.source, index)?.let(::add)
                val summary = story.summary?.let(::clean)?.takeIf { it.length > 40 }
                if (summary != null) { add(INTO_SUMMARY[index % INTO_SUMMARY.size]); add(summary) }
            }
            BriefingDetail.FULL -> {
                val body = story.body?.let(::clean)?.takeIf { it.length > 200 } ?: story.summary?.let(::clean)
                if (body != null) {
                    story.source?.takeIf { it.isNotBlank() }?.let { add("$it reports.") }
                    add(body)
                    story.source?.takeIf { it.isNotBlank() }?.let { add("That report from $it.") }
                } else attribution(story.source, index)?.let(::add)
            }
        }
    }

    fun outro(topic: String): String =
        "And that's your ${topic.trim()} briefing. For the full stories, open them in Editrove. Thanks for listening."

    /** Strips leftovers that read badly aloud (URLs, bracketed credits, repeated whitespace). */
    fun clean(text: String): String = text
        .replace(Regex("https?://\\S+"), "")
        .replace(Regex("\\[[^]]{0,60}]"), "")
        .replace(Regex("\\s*[|•]\\s*"), ". ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
