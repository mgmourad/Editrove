package news.openwire.domain.engine

import news.openwire.domain.model.Topic

/**
 * The default topic lineup. The World topic is labeled "International", and the
 * default edition is US / Washington D.C. (live-only in v1 — see ADR-0005).
 */
object TopicRegistry {

    fun defaultTopics(): List<Topic> = listOf(
        Topic(id = "TOP", label = "Top Stories", snapshotFile = "news_top.json"),
        Topic(id = "LATEST", label = "Latest", snapshotFile = "news_latest.json"),
        Topic(id = "AI", label = "AI", snapshotFile = "news_ai.json"),
        Topic(
            id = "US",
            label = "US",
            liveUrl = "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en",
            liveOnly = true,
        ),
        Topic(
            id = "WASHINGTON_DC",
            label = "Washington D.C.",
            liveUrl = "https://news.google.com/rss/search?q=%22Washington+D.C.%22&hl=en-US&gl=US&ceid=US:en",
            liveOnly = true,
        ),
        Topic(id = "BUSINESS", label = "Business", snapshotFile = "news_business.json"),
        Topic(id = "TECHNOLOGY", label = "Tech", snapshotFile = "news_technology.json"),
        Topic(id = "INTERNATIONAL", label = "International", snapshotFile = "news_world.json"),
        Topic(id = "SCIENCE", label = "Science", snapshotFile = "news_science.json"),
        Topic(id = "ENTERTAINMENT", label = "Entertainment", snapshotFile = "news_entertainment.json"),
        Topic(id = "SPORTS", label = "Sports", snapshotFile = "news_sports.json"),
        Topic(id = "POLITICS", label = "Politics", snapshotFile = "news_politics.json"),
        Topic(id = "FINANCE", label = "Finance", snapshotFile = "news_finance.json"),
        Topic(id = "LIFESTYLE", label = "Lifestyle", snapshotFile = "news_lifestyle.json"),
    )

    /** Snapshot-backed topics only (excludes live-only topics). */
    fun snapshotTopics(): List<Topic> = defaultTopics().filter { !it.liveOnly }
}
