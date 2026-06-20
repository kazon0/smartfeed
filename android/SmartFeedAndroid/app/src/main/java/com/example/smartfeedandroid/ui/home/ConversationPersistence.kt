package com.example.smartfeedandroid.ui.home

import android.content.Context
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.data.repository.ConversationSyncRepository
import com.example.smartfeedandroid.ui.model.Conversation

class ConversationPersistence(context: Context) {
    private val conversationStore = ConversationStore(context.applicationContext)
    private val syncRepository = ConversationSyncRepository()

    suspend fun load(ownerId: String): List<Conversation> {
        val localConversations = conversationStore.load(ownerId)
        val remoteConversations = syncRepository.fetch().getOrNull()
        val conversations = if (remoteConversations == null) {
            localConversations
        } else {
            ConversationSyncRepository.merge(
                local = localConversations,
                remote = remoteConversations
            ).also { merged ->
                conversationStore.save(ownerId, merged)
                merged.forEach { syncRepository.replace(it) }
            }
        }
        return conversations.map { it.toConversation() }
    }

    suspend fun save(ownerId: String, conversations: List<Conversation>) {
        val storedConversations = conversations.map { it.toStoredConversation() }
        conversationStore.save(ownerId, storedConversations)
        storedConversations.forEach { syncRepository.replace(it) }
    }

    suspend fun delete(ownerId: String, conversationId: String, conversations: List<Conversation>) {
        save(ownerId, conversations)
        syncRepository.delete(conversationId)
    }
}
