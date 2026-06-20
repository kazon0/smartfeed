package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.local.StoredChatMessage
import com.example.smartfeedandroid.data.local.StoredConversation
import com.example.smartfeedandroid.data.remote.ConversationMessageSync
import com.example.smartfeedandroid.data.remote.ConversationSyncRequest
import com.example.smartfeedandroid.data.remote.ConversationSyncResponse
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork

class ConversationSyncRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun fetch(): Result<List<StoredConversation>> {
        return runCatching {
            api.conversations().conversations.map { it.toStoredConversation() }
        }
    }

    suspend fun replace(conversation: StoredConversation): Result<StoredConversation> {
        return runCatching {
            api.putConversation(
                id = conversation.id,
                request = conversation.toSyncRequest()
            ).toStoredConversation()
        }
    }

    suspend fun delete(conversationId: String): Result<Unit> {
        return runCatching {
            api.deleteConversation(conversationId)
            Unit
        }
    }

    companion object {
        fun merge(
            local: List<StoredConversation>,
            remote: List<StoredConversation>
        ): List<StoredConversation> {
            return (local + remote)
                .groupBy { it.id }
                .values
                .map { conversations ->
                    conversations.maxWith(
                        compareBy<StoredConversation> { it.updatedAtMillis }
                            .thenBy { it.messages.size }
                    )
                }
                .sortedByDescending { it.updatedAtMillis }
        }
    }
}

private fun ConversationSyncResponse.toStoredConversation(): StoredConversation {
    return StoredConversation(
        id = id,
        title = title,
        url = url.ifBlank { sourceUrl },
        sourceUrl = sourceUrl.ifBlank { url },
        summary = summary,
        status = status,
        topic = topic,
        storedChunks = storedChunks,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        messages = messages.map { it.toStoredChatMessage() }
    )
}

private fun ConversationMessageSync.toStoredChatMessage(): StoredChatMessage {
    return StoredChatMessage(
        type = type,
        text = text,
        response = response
    )
}

private fun StoredConversation.toSyncRequest(): ConversationSyncRequest {
    return ConversationSyncRequest(
        title = title,
        sourceUrl = sourceUrl.ifBlank { url },
        summary = summary,
        status = status,
        topic = topic,
        storedChunks = storedChunks,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        messages = messages.map { it.toConversationMessageSync() }
    )
}

private fun StoredChatMessage.toConversationMessageSync(): ConversationMessageSync {
    return ConversationMessageSync(
        type = type,
        text = text,
        response = response
    )
}
