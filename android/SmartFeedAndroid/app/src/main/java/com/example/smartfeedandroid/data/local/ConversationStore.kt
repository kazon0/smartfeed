package com.example.smartfeedandroid.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.smartfeedandroid.data.remote.ChatResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConversationStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val database = SmartFeedDatabase.instance(applicationContext)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun load(): List<StoredConversation> {
        val roomConversations = database.conversationDao()
            .getAll()
            .map { it.toStoredConversation() }
        if (roomConversations.isNotEmpty()) {
            return roomConversations
        }

        val legacyConversations = loadLegacyConversations()
        if (legacyConversations.isNotEmpty()) {
            save(legacyConversations)
        }
        return legacyConversations
    }

    suspend fun save(conversations: List<StoredConversation>) {
        database.conversationDao().replaceAll(
            conversations.map { it.toEntity() }
        )
        preferences.edit()
            .remove(KEY_CONVERSATIONS)
            .apply()
    }

    private fun loadLegacyConversations(): List<StoredConversation> {
        val raw = preferences.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredConversation>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun StoredConversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            url = url,
            summary = summary,
            status = status,
            storedChunks = storedChunks,
            updatedAtMillis = updatedAtMillis,
            messagesJson = json.encodeToString(messages)
        )
    }

    private fun ConversationEntity.toStoredConversation(): StoredConversation {
        val messages = runCatching {
            json.decodeFromString<List<StoredChatMessage>>(messagesJson)
        }.getOrDefault(emptyList())

        return StoredConversation(
            id = id,
            title = title,
            url = url,
            summary = summary,
            status = status,
            storedChunks = storedChunks,
            updatedAtMillis = updatedAtMillis,
            messages = messages
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "smartfeed_conversations"
        const val KEY_CONVERSATIONS = "conversations"
    }
}

@Serializable
data class StoredConversation(
    val id: String,
    val title: String,
    val url: String = "",
    val summary: String = "",
    val status: String = "",
    val storedChunks: Int = 0,
    val updatedAtMillis: Long,
    val messages: List<StoredChatMessage> = emptyList()
)

@Serializable
data class StoredChatMessage(
    val type: String,
    val text: String = "",
    val response: ChatResponse? = null
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val summary: String,
    val status: String,
    val storedChunks: Int,
    val updatedAtMillis: Long,
    val messagesJson: String
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(conversations: List<ConversationEntity>) {
        deleteAll()
        insertAll(conversations)
    }
}

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SmartFeedDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: SmartFeedDatabase? = null

        fun instance(context: Context): SmartFeedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartFeedDatabase::class.java,
                    "smartfeed.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
