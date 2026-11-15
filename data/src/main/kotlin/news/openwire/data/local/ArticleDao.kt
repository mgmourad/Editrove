// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE topicId = :topicId ORDER BY position")
    suspend fun getByTopic(topicId: String): List<ArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles WHERE topicId = :topicId")
    suspend fun deleteTopic(topicId: String)

    /** Replaces a topic's cached feed atomically so stale stories don't accumulate. */
    @Transaction
    suspend fun replaceTopic(topicId: String, articles: List<ArticleEntity>) {
        deleteTopic(topicId)
        upsertAll(articles)
    }
}
