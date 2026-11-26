// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * Everything the user can personalize (SPEC §5 "Personalize" / §6.3 / §6.7).
 * Paywall rule overrides live in the DomainRegistry's own store.
 */
data class UserSettings(
    val edition: LocalEdition = LocalEdition.DEFAULT,
    /** Editrove's language ("" = the phone's). Also picks the language topic feeds read in. */
    val language: String = "",
    /** Edition and language before the last switch, for a one-tap way back. */
    val previousEdition: LocalEdition? = null,
    val previousLanguage: String? = null,
    /** Article text size, 1 = normal (the reader's A− / A+ and Appearance both set it). */
    val textScale: Float = 1f,
    /** Writers followed on the Analysis & Opinion page (bylines). */
    val followedAuthors: Set<String> = emptySet(),
    /** Bluesky accounts followed there (handles). */
    val followedAccounts: Set<String> = emptySet(),
    /** Topic tabs added there (from trending suggestions or typed). */
    val opinionTabs: List<String> = emptyList(),
    /** Followed writers ("a:"), accounts ("b:") and opinion topics ("t:") whose alerts are off (on by default when followed). */
    val opinionMuted: Set<String> = emptySet(),
    val customTopics: List<Topic> = emptyList(),
    val hiddenTopics: Set<String> = emptySet(),
    /** Topic ids in display order; null = default order. */
    val topicOrder: List<String>? = null,
    val customFeeds: List<FeedSource> = emptyList(),
    /** Source keys (feed URL, or "google_news") the user switched off. */
    val disabledSources: Set<String> = emptySet(),
    val sortMode: SortMode = SortMode.CURATED,
    /** Global master switch for local notifications (default off). */
    val notificationsEnabled: Boolean = false,
    /** Topics opted into notifications (default none). */
    val notifyTopics: Set<String> = emptySet(),
    /** Periodic background refresh of snapshots (SPEC §6.6). */
    val backgroundRefresh: Boolean = true,
    /** Bypass services the user switched off (e.g. one that changed or shut down); routing skips them. */
    val disabledServices: Set<Strategy> = emptySet(),
    /** Unfolded / tablet layout: list + reader side by side, or full-width pages. */
    val unfoldedLayout: UnfoldedLayout = UnfoldedLayout.SPLIT,
    /** Check GitHub releases for a newer build whenever the app opens. */
    val autoCheckUpdates: Boolean = true,
    /** Download and install updates without asking (where Android allows it). */
    val autoInstallUpdates: Boolean = false,
    /** Nudge Curated order toward outlets the reader saves and likes (computed on-device). */
    val personalize: Boolean = true,
    /** Minutes between background refreshes (feeds, widgets, alerts): 15, 30, 60, 180 or 360. */
    val refreshMinutes: Int = 60,
    /** Breaking-news alerts (separate from topic notifications). */
    val breakingEnabled: Boolean = false,
    /** Newsrooms watched for breaking news (keys from BreakingNews.OUTLETS). */
    val breakingOutlets: Set<String> = news.openwire.domain.engine.BreakingNews.DEFAULT_KEYS,
    /** Real-time breaking alerts over a kept-open connection (opt-in, off by default). */
    val realtimeAlerts: Boolean = false,
    /** Alert sounds, quiet hours and pause. */
    val alerts: news.openwire.domain.alerts.AlertPrefs = news.openwire.domain.alerts.AlertPrefs(),
    /** Audio briefing voice, speed, length, detail and sound design. */
    val briefing: BriefingPrefs = BriefingPrefs(),
)

enum class UnfoldedLayout(val label: String) { SPLIT("Split view"), FULL("Full view") }

/** Text sizes offered by the reader's A− / A+. */
val TEXT_SCALES = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f)

/** The next size up or down from [current]. */
fun stepTextScale(current: Float, up: Boolean): Float {
    val i = TEXT_SCALES.indexOfFirst { it >= current - 0.01f }.let { if (it < 0) TEXT_SCALES.lastIndex else it }
    return TEXT_SCALES[(if (up) i + 1 else i - 1).coerceIn(0, TEXT_SCALES.lastIndex)]
}
