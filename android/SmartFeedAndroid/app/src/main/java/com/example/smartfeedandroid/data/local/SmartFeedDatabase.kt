package com.example.smartfeedandroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 4,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_STATEMENTS.forEach { statement ->
                    db.execSQL(statement)
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_STATEMENTS.forEach { statement ->
                    db.execSQL(statement)
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_STATEMENTS.forEach { statement ->
                    db.execSQL(statement)
                }
            }
        }

        internal val MIGRATION_1_2_STATEMENTS = listOf(
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

        internal val MIGRATION_2_3_STATEMENTS = listOf(
            "ALTER TABLE conversations ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE conversations ADD COLUMN topic TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE conversations ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0",
            "UPDATE conversations SET sourceUrl = url WHERE sourceUrl = ''",
            "UPDATE conversations SET createdAtMillis = updatedAtMillis WHERE createdAtMillis = 0"
        )

        internal val MIGRATION_3_4_STATEMENTS = listOf(
            "ALTER TABLE conversations ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''"
        )
    }
}
