package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.remote.SavedArticle

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
class ConversationCoordinator(
    private val conversationManager: ConversationManager
) {
    fun selectConversation(state: HomeUiState, conversationId: String): HomeUiState {
        val conversation = state.conversations.firstOrNull { it.id == conversationId } ?: return state
        return state.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            activeUrl = conversation.sourceUrl.ifBlank { conversation.url },
            messages = conversation.messages,
            uploadResponse = null,
            errorMessage = null
        )
    }

    fun deleteConversation(state: HomeUiState, conversationId: String): HomeUiState {
        val conversations = state.conversations.filterNot { it.id == conversationId }
        val deletingActiveConversation = state.activeConversationId == conversationId
        return state.copy(
            conversations = conversations,
            activeConversationId = if (deletingActiveConversation) null else state.activeConversationId,
            activeUrl = if (deletingActiveConversation) "" else state.activeUrl,
            messages = if (deletingActiveConversation) emptyList() else state.messages,
            isChatOpen = if (deletingActiveConversation) false else state.isChatOpen,
            errorMessage = null
        )
    }

    fun startGlobalConversation(state: HomeUiState): HomeUiState {
        val conversation = conversationManager.createGlobalConversation()
        return openConversation(
            state = state,
            conversation = conversation,
            conversations = listOf(conversation) + state.conversations,
            messages = emptyList(),
            activeUrl = ""
        )
    }

    fun startArticleConversation(state: HomeUiState, article: SavedArticle): HomeUiState {
        val conversation = conversationManager.createArticleConversation(
            url = article.url,
            title = article.title,
            topic = article.topic,
            storedChunks = article.chunkCount
        )
        return openConversation(
            state = state,
            conversation = conversation,
            conversations = listOf(conversation) + state.conversations,
            messages = emptyList(),
            activeUrl = conversation.sourceUrl.ifBlank { conversation.url }
        )
    }

    fun startExistingArticleConversation(
        state: HomeUiState,
        url: String,
        title: String,
        topic: String,
        storedChunks: Int
    ): HomeUiState {
        val conversation = conversationManager.createArticleConversation(
            url = url,
            title = title,
            topic = topic,
            storedChunks = storedChunks
        )
        return openConversation(
            state = state,
            conversation = conversation,
            conversations = listOf(conversation) + state.conversations,
            messages = emptyList(),
            activeUrl = conversation.sourceUrl.ifBlank { conversation.url }
        )
    }

    fun openUploadedConversation(
        state: HomeUiState,
        url: String,
        title: String,
        summary: String,
        status: String,
        topic: String,
        storedChunks: Int
    ): HomeUiState {
        val conversations = conversationManager.upsertUploadedConversation(
            conversations = state.conversations,
            url = url,
            title = title,
            summary = summary,
            status = status,
            topic = topic,
            storedChunks = storedChunks
        )
        val conversation = conversations.first()
        return openConversation(
            state = state,
            conversation = conversation,
            conversations = conversations,
            messages = conversation.messages,
            activeUrl = url
        )
    }

    fun ensureActiveConversation(state: HomeUiState): Pair<HomeUiState, Conversation> {
        val activeConversation = state.conversations
            .firstOrNull { it.id == state.activeConversationId }
        if (activeConversation != null) {
            return state to activeConversation
        }

        val conversation = conversationManager.createGlobalConversation()
        val nextState = openConversation(
            state = state,
            conversation = conversation,
            conversations = listOf(conversation) + state.conversations,
            messages = emptyList(),
            activeUrl = ""
        )
        return nextState to conversation
    }

    fun updateMessages(
        state: HomeUiState,
        conversationId: String,
        messages: List<ChatMessage>
    ): HomeUiState {
        return state.copy(
            conversations = conversationManager.updateMessages(
                conversations = state.conversations,
                conversationId = conversationId,
                messages = messages
            ),
            activeConversationId = conversationId,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            messages = messages,
            errorMessage = null
        )
    }

    fun appendMessage(
        state: HomeUiState,
        conversationId: String,
        message: ChatMessage
    ): HomeUiState {
        val conversations = conversationManager.appendMessage(
            conversations = state.conversations,
            conversationId = conversationId,
            message = message
        )
        val messages = if (state.activeConversationId == conversationId) {
            conversations.firstOrNull { it.id == conversationId }?.messages.orEmpty()
        } else {
            state.messages
        }
        return state.copy(
            conversations = conversations,
            messages = messages
        )
    }

    private fun openConversation(
        state: HomeUiState,
        conversation: Conversation,
        conversations: List<Conversation>,
        messages: List<ChatMessage>,
        activeUrl: String
    ): HomeUiState {
        return state.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            activeUrl = activeUrl,
            uploadResponse = null,
            errorMessage = null,
            conversations = conversations,
            messages = messages
        )
    }
}
