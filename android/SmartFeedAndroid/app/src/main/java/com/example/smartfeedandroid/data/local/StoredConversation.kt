package com.example.smartfeedandroid.data.local

import com.example.smartfeedandroid.data.remote.ChatResponse
import kotlinx.serialization.Serializable

@Serializable
data class StoredConversation(
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
    val messages: List<StoredChatMessage> = emptyList()
)

@Serializable
data class StoredChatMessage(
    val type: String,
    val text: String = "",
    val response: ChatResponse? = null
)
