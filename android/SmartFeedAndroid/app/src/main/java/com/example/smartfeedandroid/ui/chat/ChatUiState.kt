package com.example.smartfeedandroid.ui.chat

data class ChatUiState(
    val query: String = "",
    val isAsking: Boolean = false,
    val streamingConversationId: String? = null,
    val streamStatusText: String = "",
    val streamAnswerText: String = ""
)
