package com.example.smartfeedandroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SmartFeedDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: SmartFeedDatabase? = null

        fun instance(context: Context): SmartFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartFeedDatabase::class.java,
                    "smartfeed.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        messageIndex INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT NOT NULL,
                        responseJson TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE conversations SET sourceUrl = url WHERE sourceUrl = ''")
                db.execSQL("UPDATE conversations SET createdAtMillis = updatedAtMillis WHERE createdAtMillis = 0")
            }
        }
    }
}
