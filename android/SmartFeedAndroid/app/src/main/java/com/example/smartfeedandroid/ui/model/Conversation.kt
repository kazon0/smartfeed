package com.example.smartfeedandroid.ui.model

data class Conversation(
    val id: String,
    val title: String,
    val url: String = "",
    val sourceUrl: String = url,
    val summary: String = "",
    val status: String = "",
    val topic: String = "",
    val storedChunks: Int = 0,
    val updatedAtMillis: Long,
    val createdAtMillis: Long = updatedAtMillis,
    val messages: List<ChatMessage> = emptyList()
)
