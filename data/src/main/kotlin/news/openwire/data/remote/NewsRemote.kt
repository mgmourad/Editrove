package news.openwire.data.remote

/** Fetches a topic's snapshot articles. */
interface NewsRemote {
    suspend fun fetchTopic(file: String): List<ArticleDto>
}
