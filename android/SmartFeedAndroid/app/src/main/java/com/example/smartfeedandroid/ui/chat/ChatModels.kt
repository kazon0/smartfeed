package com.example.smartfeedandroid.ui.chat

import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.ui.model.ChatMessage

data class ChatSendContext(
    val conversationId: String,
    val activeUrl: String,
    val historyMessages: List<ChatMessage>
)

sealed interface ChatResult {
    data class Answer(val response: ChatResponse) : ChatResult
    data class Failed(val message: String) : ChatResult
}
