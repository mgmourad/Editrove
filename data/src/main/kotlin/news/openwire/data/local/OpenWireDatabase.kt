package news.openwire.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArticleEntity::class], version = 1, exportSchema = false)
abstract class OpenWireDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        fun create(context: Context): OpenWireDatabase =
            Room.databaseBuilder(context, OpenWireDatabase::class.java, "openwire.db").build()
    }
}
