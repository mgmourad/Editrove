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
    /** Scheduled briefings (Morning / Afternoon / Evening), each off until the reader turns it on. */
    val slots: List<BriefingSlot> = BriefingSlot.DEFAULTS,
) {
    companion object {
        val SPEEDS = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)
        val COUNTS = listOf(3, 5, 8, 10, 12, 15, 20)
        /** Opening cues bundled with the app. */
        const val CUE_COUNT = 11
    }
}

/** A scheduled briefing: a notification at [minuteOfDay] that plays the latest briefing. */
data class BriefingSlot(val kind: Kind, val enabled: Boolean, val minuteOfDay: Int) {
    enum class Kind(val label: String) { MORNING("Morning Briefing"), AFTERNOON("Afternoon Briefing"), EVENING("Evening Briefing") }

    val hour: Int get() = minuteOfDay / 60
    val minute: Int get() = minuteOfDay % 60

    companion object {
        val DEFAULTS = listOf(
            BriefingSlot(Kind.MORNING, false, 7 * 60),
            BriefingSlot(Kind.AFTERNOON, false, 12 * 60 + 30),
            BriefingSlot(Kind.EVENING, false, 18 * 60),
        )

        /** "MORNING:1:420,AFTERNOON:0:750,…" — compact storage form. */
        fun encode(slots: List<BriefingSlot>) = slots.joinToString(",") { "${it.kind.name}:${if (it.enabled) 1 else 0}:${it.minuteOfDay}" }

        fun decode(raw: String?): List<BriefingSlot> {
            val parsed = raw.orEmpty().split(',').mapNotNull { part ->
                val f = part.split(':')
                val kind = Kind.entries.firstOrNull { it.name == f.getOrNull(0) } ?: return@mapNotNull null
                BriefingSlot(kind, f.getOrNull(1) == "1", f.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 24 * 60 - 1) ?: return@mapNotNull null)
            }
            return DEFAULTS.map { d -> parsed.firstOrNull { it.kind == d.kind } ?: d }
        }
    }
}
