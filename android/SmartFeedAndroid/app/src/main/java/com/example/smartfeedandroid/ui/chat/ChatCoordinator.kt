package com.example.smartfeedandroid.ui.chat

import com.example.smartfeedandroid.data.remote.ChatHistoryItem
import com.example.smartfeedandroid.data.repository.ChatStreamStatus
import com.example.smartfeedandroid.data.repository.ChatStreamEvent
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.ui.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class ChatCoordinator(
    private val chatRepository: ChatRepository
) {
    fun askFlow(
        query: String,
        activeUrl: String,
        messages: List<ChatMessage>
    ): Flow<ChatFlowEvent> = flow {
        val history = chatHistoryFrom(messages)
        try {
            chatRepository.streamChat(query, activeUrl, history).collect { event ->
                when (event) {
                    is ChatStreamEvent.Status -> emit(ChatFlowEvent.Status(event.status))
                    is ChatStreamEvent.Delta -> emit(ChatFlowEvent.Delta(event.text))
                    is ChatStreamEvent.Completed -> {
                        emit(ChatFlowEvent.Result(ChatResult.Answer(event.response)))
                    }
                }
            }
            return@flow
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            emit(ChatFlowEvent.Status(ChatStreamStatus.Fallback))
        }

        val result: ChatResult = chatRepository.ask(query, activeUrl, history).fold(
            onSuccess = { ChatResult.Answer(it) },
            onFailure = {
                ChatResult.Failed(it.message ?: "聊天请求失败。")
            }
        )
        emit(ChatFlowEvent.Result(result))
    }
}

sealed interface ChatFlowEvent {
    data class Status(val status: ChatStreamStatus) : ChatFlowEvent
    data class Delta(val text: String) : ChatFlowEvent
    data class Result(val result: ChatResult) : ChatFlowEvent
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
