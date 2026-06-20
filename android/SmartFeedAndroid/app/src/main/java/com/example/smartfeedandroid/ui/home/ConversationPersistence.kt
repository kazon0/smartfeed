package com.example.smartfeedandroid.ui.home

import android.content.Context
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.ui.model.Conversation

class ConversationPersistence(context: Context) {
    private val conversationStore = ConversationStore(context.applicationContext)

    suspend fun load(): List<Conversation> {
        return conversationStore.load().map { it.toConversation() }
    }

    suspend fun save(conversations: List<Conversation>) {
        conversationStore.save(conversations.map { it.toStoredConversation() })
    }
}
