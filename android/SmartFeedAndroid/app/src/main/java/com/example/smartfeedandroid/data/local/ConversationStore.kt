package com.example.smartfeedandroid.data.local

import android.content.Context
import com.example.smartfeedandroid.data.remote.ChatResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): List<StoredConversation> {
        val raw = preferences.getString(KEY_CONVERSATIONS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredConversation>>(raw)
        }.getOrDefault(emptyList())
    }

    fun save(conversations: List<StoredConversation>) {
        preferences.edit()
            .putString(KEY_CONVERSATIONS, json.encodeToString(conversations))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "smartfeed_conversations"
        const val KEY_CONVERSATIONS = "conversations"
    }
}

@Serializable
data class StoredConversation(
    val id: String,
    val title: String,
    val url: String = "",
    val summary: String = "",
    val status: String = "",
    val storedChunks: Int = 0,
    val updatedAtMillis: Long,
    val messages: List<StoredChatMessage> = emptyList()
)

@Serializable
data class StoredChatMessage(
    val type: String,
    val text: String = "",
    val response: ChatResponse? = null
)
