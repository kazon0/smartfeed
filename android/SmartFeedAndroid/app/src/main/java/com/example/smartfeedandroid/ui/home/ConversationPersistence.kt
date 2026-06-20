package com.example.smartfeedandroid.ui.home

import android.content.Context
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.ui.model.Conversation

class ConversationPersistence(context: Context) {
    private val conversationStore = ConversationStore(context.applicationContext)

    suspend fun load(ownerId: String): List<Conversation> {
        return conversationStore.load(ownerId).map { it.toConversation() }
    }

    suspend fun save(ownerId: String, conversations: List<Conversation>) {
        conversationStore.save(ownerId, conversations.map { it.toStoredConversation() })
    }
}
