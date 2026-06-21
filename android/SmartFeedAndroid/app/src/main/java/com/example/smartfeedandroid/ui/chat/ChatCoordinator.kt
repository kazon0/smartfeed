package com.example.smartfeedandroid.ui.chat

import com.example.smartfeedandroid.data.remote.ChatHistoryItem
import com.example.smartfeedandroid.data.repository.ChatStreamStatus
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.ui.model.ChatMessage

class ChatCoordinator(
    private val chatRepository: ChatRepository
) {
    suspend fun ask(
        query: String,
        activeUrl: String,
        messages: List<ChatMessage>,
        onStatus: (ChatStreamStatus) -> Unit = {},
        onDelta: (String) -> Unit = {}
    ): ChatResult {
        val history = chatHistoryFrom(messages)
        val streamingResult = chatRepository.askStreaming(
            query = query,
            url = activeUrl,
            history = history,
            onStatus = onStatus,
            onDelta = onDelta
        )
        if (streamingResult.isSuccess) {
            return ChatResult.Answer(streamingResult.getOrThrow())
        }

        onStatus(ChatStreamStatus.Fallback)
        return chatRepository.ask(query, activeUrl, history).fold(
            onSuccess = { ChatResult.Answer(it) },
            onFailure = {
                ChatResult.Failed(it.message ?: "聊天请求失败。")
            }
        )
    }
}

internal fun chatHistoryFrom(messages: List<ChatMessage>): List<ChatHistoryItem> {
    return messages
        .mapNotNull { message ->
            when (message) {
                is ChatMessage.User -> ChatHistoryItem("user", message.text)
                is ChatMessage.Summary -> ChatHistoryItem("summary", message.text)
                is ChatMessage.Assistant -> {
                    val content = message.response.answer.ifBlank {
                        message.response.message
                    }
                    content.takeIf { it.isNotBlank() }?.let {
                        ChatHistoryItem("assistant", it)
                    }
                }
                is ChatMessage.Error -> null
            }
        }
        .takeLast(8)
}
