package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.local.StoredChatMessage
import com.example.smartfeedandroid.data.local.StoredConversation

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
internal fun StoredConversation.toConversation(): Conversation {
    return Conversation(
        id = id,
        title = title,
        url = url,
        sourceUrl = sourceUrl.ifBlank { url },
        summary = summary,
        status = status,
        topic = topic,
        storedChunks = storedChunks,
        updatedAtMillis = updatedAtMillis,
        createdAtMillis = createdAtMillis,
        messages = messages.mapNotNull { it.toChatMessage() }
    )
}

internal fun Conversation.toStoredConversation(): StoredConversation {
    return StoredConversation(
        id = id,
        title = title,
        url = url,
        sourceUrl = sourceUrl.ifBlank { url },
        summary = summary,
        status = status,
        topic = topic,
        storedChunks = storedChunks,
        updatedAtMillis = updatedAtMillis,
        createdAtMillis = createdAtMillis,
        messages = messages.map { it.toStoredChatMessage() }
    )
}

private fun StoredChatMessage.toChatMessage(): ChatMessage? {
    return when (type) {
        MESSAGE_TYPE_USER -> ChatMessage.User(text)
        MESSAGE_TYPE_SUMMARY -> ChatMessage.Summary(text)
        MESSAGE_TYPE_ASSISTANT -> response?.let { ChatMessage.Assistant(it) }
        MESSAGE_TYPE_ERROR -> ChatMessage.Error(text)
        else -> null
    }
}

private fun ChatMessage.toStoredChatMessage(): StoredChatMessage {
    return when (this) {
        is ChatMessage.User -> StoredChatMessage(type = MESSAGE_TYPE_USER, text = text)
        is ChatMessage.Summary -> StoredChatMessage(type = MESSAGE_TYPE_SUMMARY, text = text)
        is ChatMessage.Assistant -> StoredChatMessage(
            type = MESSAGE_TYPE_ASSISTANT,
            response = response
        )
        is ChatMessage.Error -> StoredChatMessage(type = MESSAGE_TYPE_ERROR, text = text)
    }
}

private const val MESSAGE_TYPE_USER = "user"
private const val MESSAGE_TYPE_SUMMARY = "summary"
private const val MESSAGE_TYPE_ASSISTANT = "assistant"
private const val MESSAGE_TYPE_ERROR = "error"
