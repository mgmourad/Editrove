// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * A news topic. Snapshot-backed topics carry [snapshotFile] (a JSON filename under
 * the data/ directory); live topics carry [liveUrl] (Google News RSS) and
 * [liveOnly] = true. Custom topics are built from [keywords] and/or assigned
 * [sources] (feed source ids).
 */
data class Topic(
    val id: String,
    val label: String,
    val snapshotFile: String? = null,
    val liveUrl: String? = null,
    val liveOnly: Boolean = false,
    val keywords: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val isCustom: Boolean = false,
    val isVisible: Boolean = true,
)
