package com.example.smartfeedandroid.ui.model

data class Conversation(
    val id: String,
    val title: String,
    val url: String = "",
    val summary: String = "",
    val status: String = "",
    val storedChunks: Int = 0,
    val updatedAtMillis: Long,
    val messages: List<ChatMessage> = emptyList()
)
