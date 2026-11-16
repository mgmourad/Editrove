// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/** How much of each story the audio briefing reads. */
enum class BriefingDetail(val label: String) { HEADLINES("Headlines"), SUMMARIES("Summaries"), FULL("Full articles") }

/** Audio briefing preferences (Settings › Audio briefing). */
data class BriefingPrefs(
    /** On-device TTS voice name; null = the system default voice. */
    val voice: String? = null,
    /** Speech rate multiplier (1.0 = normal). */
    val speed: Float = 1.0f,
    /** Number of stories in a briefing. */
    val count: Int = 8,
    val detail: BriefingDetail = BriefingDetail.HEADLINES,
    /** Opening cue 1–4, or 0 for none. */
    val openingCue: Int = 1,
    /** Play the transition sting between stories. */
    val stings: Boolean = true,
) {
    companion object {
        val SPEEDS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)
        val COUNTS = listOf(3, 5, 8, 10, 12, 15, 20)
    }
}
