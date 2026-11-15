// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * A direct RSS/Atom source in the library (built-in catalog or user-added).
 * [topics] are the topic ids it is woven into; [maxArticles] is its fair-share quota.
 */
data class FeedSource(
    val id: String,
    val name: String,
    val url: String,
    val domain: String = "",
    val topics: List<String> = emptyList(),
    val maxArticles: Int = 8,
    val enabled: Boolean = true,
    val category: String? = null,
    val kind: String = "publisher",
    val isCustom: Boolean = false,
    val access: String? = null,
    /** The primary aggregator (Google News) pseudo-source. */
    val isPrimary: Boolean = false,
    /**
     * Added by Editrove on top of the shared pipeline catalog, so published snapshots
     * don't include it yet — woven in live on top of the snapshot.
     */
    val extra: Boolean = false,
)

/** Feed ordering (SPEC §6.1): curated fair-share weave, or by date. */
enum class SortMode(val label: String) { CURATED("Curated"), NEWEST("Newest"), OLDEST("Oldest") }
