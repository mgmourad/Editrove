package news.openwire.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ArticleEntity::class], version = 1, exportSchema = false)
abstract class OpenWireDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}
