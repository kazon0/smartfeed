package com.example.smartfeedandroid.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.smartfeedandroid.data.remote.ChatResponse
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

    suspend fun load(ownerId: String): List<StoredConversation> {
        database.conversationDao().claimUnowned(ownerId)
        val roomConversations = database.conversationDao()
            .getAll(ownerId)
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
            save(ownerId, legacyConversations)
        }
        return legacyConversations
    }

    suspend fun save(ownerId: String, conversations: List<StoredConversation>) {
        val conversationEntities = conversations.map { it.toEntity(ownerId) }
        val messageEntities = conversations.flatMap { conversation ->
                conversation.messages.mapIndexed { index, message ->
                    message.toEntity(conversation.id, index)
                }
            }

        database.withTransaction {
            database.messageDao().deleteByOwner(ownerId)
            database.conversationDao().deleteByOwner(ownerId)
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

    private fun StoredConversation.toEntity(ownerId: String): ConversationEntity {
        return ConversationEntity(
            id = id,
            ownerId = ownerId,
            title = title,
            url = url,
            sourceUrl = sourceUrl.ifBlank { url },
            summary = summary,
            status = status,
            topic = topic,
            storedChunks = storedChunks,
            createdAtMillis = createdAtMillis,
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
            sourceUrl = sourceUrl.ifBlank { url },
            summary = summary,
            status = status,
            topic = topic,
            storedChunks = storedChunks,
            createdAtMillis = createdAtMillis,
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
