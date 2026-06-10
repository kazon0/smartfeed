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
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
            .map { conversation ->
                val messages = database.messageDao()
                    .getByConversationId(conversation.id)
                    .map { it.toStoredChatMessage() }
                    .ifEmpty {
                        val legacyMessages = conversation.decodeLegacyMessages()
                        if (legacyMessages.isNotEmpty()) {
                            database.messageDao().insertAll(
                                legacyMessages.mapIndexed { index, message ->
                                    message.toEntity(conversation.id, index)
                                }
                            )
                        }
                        legacyMessages
                    }
                conversation.toStoredConversation(messages)
            }
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
        val conversationEntities = conversations.map { it.toEntity() }
        val messageEntities = conversations.flatMap { conversation ->
                conversation.messages.mapIndexed { index, message ->
                    message.toEntity(conversation.id, index)
                }
            }

        database.withTransaction {
            database.messageDao().deleteAll()
            database.conversationDao().deleteAll()
            database.conversationDao().insertAll(conversationEntities)
            if (messageEntities.isNotEmpty()) {
                database.messageDao().insertAll(messageEntities)
            }
        }
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
        return toStoredConversation(decodeLegacyMessages())
    }

    private fun ConversationEntity.toStoredConversation(
        messages: List<StoredChatMessage>
    ): StoredConversation {
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

    private fun ConversationEntity.decodeLegacyMessages(): List<StoredChatMessage> {
        return runCatching {
            json.decodeFromString<List<StoredChatMessage>>(messagesJson)
        }.getOrDefault(emptyList())
    }

    private fun StoredChatMessage.toEntity(
        conversationId: String,
        index: Int
    ): MessageEntity {
        return MessageEntity(
            id = "$conversationId:$index",
            conversationId = conversationId,
            messageIndex = index,
            type = type,
            text = text,
            responseJson = response?.let { json.encodeToString(it) }.orEmpty()
        )
    }

    private fun MessageEntity.toStoredChatMessage(): StoredChatMessage {
        val response = responseJson
            .takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching {
                    json.decodeFromString<ChatResponse>(raw)
                }.getOrNull()
            }
        return StoredChatMessage(
            type = type,
            text = text,
            response = response
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

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageIndex: Int,
    val type: String,
    val text: String,
    val responseJson: String
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY messageIndex ASC")
    suspend fun getByConversationId(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
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
    }
}
