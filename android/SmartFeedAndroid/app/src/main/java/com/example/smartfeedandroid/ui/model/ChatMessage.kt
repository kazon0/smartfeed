package com.example.smartfeedandroid.ui.model

import com.example.smartfeedandroid.data.remote.ChatResponse

sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Summary(val text: String) : ChatMessage
    data class Assistant(val response: ChatResponse) : ChatMessage
    data class Error(val text: String) : ChatMessage
}
