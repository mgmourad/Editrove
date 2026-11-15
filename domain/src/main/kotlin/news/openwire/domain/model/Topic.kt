package news.openwire.domain.model

/**
 * A news topic. Snapshot-backed topics carry [snapshotFile] (a JSON filename
 * under the data/ directory); live-only topics (US / Washington D.C. in v1)
 * carry [liveUrl] and [liveOnly] = true until their live fetch is implemented.
 */
data class Topic(
    val id: String,
    val label: String,
    val snapshotFile: String? = null,
    val liveUrl: String? = null,
    val liveOnly: Boolean = false,
)
