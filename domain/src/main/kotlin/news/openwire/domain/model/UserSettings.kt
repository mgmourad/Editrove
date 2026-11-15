// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * Everything the user can personalize (SPEC §5 "Personalize" / §6.3 / §6.7).
 * Paywall rule overrides live in the DomainRegistry's own store.
 */
data class UserSettings(
    val edition: LocalEdition = LocalEdition.DEFAULT,
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
)
