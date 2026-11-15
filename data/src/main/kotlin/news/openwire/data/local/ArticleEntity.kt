package news.openwire.data.local

import androidx.room.Entity

@Entity(tableName = "articles", primaryKeys = ["id", "topicId"])
data class ArticleEntity(
    val id: String,
    val topicId: String,
    val title: String,
    val source: String? = null,
    val domain: String? = null,
    val originalUrl: String? = null,
    val publishedAt: Long? = null,
    val status: String? = null,
    val badge: String? = null,
    val targetUrl: String? = null,
    val archiveUrl: String? = null,
    val imageUrl: String? = null,
)
