// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArticleEntity::class], version = 2, exportSchema = false)
abstract class OpenWireDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        /** The database is only a refetchable cache, so schema changes simply rebuild it. */
        fun create(context: Context): OpenWireDatabase =
            Room.databaseBuilder(context, OpenWireDatabase::class.java, "openwire.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
