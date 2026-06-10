package com.example.smartfeedandroid.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.data.repository.ArticleRepository
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.data.repository.StatsRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadRepository = UploadRepository()
    private val chatRepository = ChatRepository()
    private val statsRepository = StatsRepository()
    private val articleRepository = ArticleRepository()
    private val conversationStore = ConversationStore(application.applicationContext)
    private val conversationManager = ConversationManager()

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        viewModelScope.launch {
            val conversations = conversationStore.load().map { it.toConversation() }
            uiState = uiState.copy(conversations = conversations)
        }
    }

    fun onUrlChange(value: String) {
        uiState = uiState.copy(url = value)
    }

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun handleSharedUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return
        }

        uiState = uiState.copy(
            url = cleanUrl,
            lastSharedUrl = cleanUrl,
            selectedTab = AppTab.Home,
            isChatOpen = false,
            isArticleManagerOpen = false
        )
        upload()
    }

    fun selectTab(tab: AppTab) {
        uiState = if (tab == AppTab.Home) {
            uiState.copy(
                selectedTab = tab,
                isChatOpen = false,
                isArticleManagerOpen = false
            )
        } else {
            uiState.copy(
                selectedTab = tab,
                isArticleManagerOpen = false
            )
        }
        if (tab == AppTab.Analysis) {
            refreshStats()
            refreshArticles()
        }
    }

    fun showConversationList() {
        uiState = uiState.copy(
            selectedTab = AppTab.Home,
            isChatOpen = false,
            isArticleManagerOpen = false,
            errorMessage = null
        )
    }

    fun openArticleManager() {
        uiState = uiState.copy(
            selectedTab = AppTab.Analysis,
            isArticleManagerOpen = true,
            articlesErrorMessage = null
        )
        refreshArticles()
    }

    fun closeArticleManager() {
        uiState = uiState.copy(
            isArticleManagerOpen = false,
            articlesErrorMessage = null
        )
    }

    fun selectConversation(conversationId: String) {
        val conversation = uiState.conversations.firstOrNull { it.id == conversationId } ?: return
        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            isArticleManagerOpen = false,
            activeUrl = conversation.url,
            messages = conversation.messages,
            uploadResponse = null,
            errorMessage = null
        )
    }

    fun deleteConversation(conversationId: String) {
        val conversations = uiState.conversations.filterNot { it.id == conversationId }
        val deletingActiveConversation = uiState.activeConversationId == conversationId
        uiState = uiState.copy(
            conversations = conversations,
            activeConversationId = if (deletingActiveConversation) null else uiState.activeConversationId,
            activeUrl = if (deletingActiveConversation) "" else uiState.activeUrl,
            messages = if (deletingActiveConversation) emptyList() else uiState.messages,
            isChatOpen = if (deletingActiveConversation) false else uiState.isChatOpen,
            errorMessage = null
        )
        persistConversations(conversations)
    }

    fun startGlobalConversation() {
        val conversation = conversationManager.createGlobalConversation()

        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            isArticleManagerOpen = false,
            activeUrl = "",
            uploadResponse = null,
            errorMessage = null,
            conversations = listOf(conversation) + uiState.conversations,
            messages = emptyList()
        )
        persistConversations(uiState.conversations)
    }

    fun upload() {
        val cleanUrl = uiState.url.trim()
        if (cleanUrl.isEmpty()) {
            uiState = uiState.copy(
                errorMessage = "Please enter a URL.",
                uploadResponse = null
            )
            return
        }

        uiState = uiState.copy(
            isUploading = true,
            errorMessage = null,
            uploadResponse = null
        )

        viewModelScope.launch {
            uploadRepository.upload(cleanUrl)
                .onSuccess { response ->
                    val parsedUrl = response.data?.url?.takeIf { it.isNotBlank() } ?: cleanUrl
                    val title = response.data?.title?.takeIf { it.isNotBlank() } ?: parsedUrl
                    val conversations = conversationManager.upsertUploadedConversation(
                        conversations = uiState.conversations,
                        url = parsedUrl,
                        title = title,
                        summary = response.summary,
                        status = response.status,
                        storedChunks = response.storedChunks
                    )
                    val conversation = conversations.first()

                    uiState = uiState.copy(
                        uploadResponse = null,
                        activeConversationId = conversation.id,
                        selectedTab = AppTab.Home,
                        isChatOpen = true,
                        isArticleManagerOpen = false,
                        activeUrl = parsedUrl,
                        conversations = conversations,
                        messages = conversation.messages
                    )
                    persistConversations(uiState.conversations)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        errorMessage = error.message ?: "Upload failed."
                    )
                }

            uiState = uiState.copy(isUploading = false)
        }
    }

    fun ask() {
        val cleanQuery = uiState.query.trim()
        if (cleanQuery.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Please enter a question.")
            return
        }

        val conversation = ensureActiveConversation()
        val conversationId = conversation.id
        val activeUrl = conversation.url
        val userMessage = ChatMessage.User(cleanQuery)
        val updatedMessages = conversation.messages + userMessage

        uiState = uiState.copy(
            conversations = conversationManager.updateMessages(
                conversations = uiState.conversations,
                conversationId = conversationId,
                messages = updatedMessages
            ),
            activeConversationId = conversationId,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            isArticleManagerOpen = false,
            activeUrl = activeUrl,
            messages = updatedMessages,
            query = "",
            isAsking = true,
            errorMessage = null
        )
        persistConversations(uiState.conversations)

        viewModelScope.launch {
            chatRepository.ask(cleanQuery, activeUrl)
                .onSuccess { response ->
                    val conversations = conversationManager.appendMessage(
                        conversations = uiState.conversations,
                        conversationId = conversationId,
                        message = ChatMessage.Assistant(response)
                    )
                    uiState = uiState.copy(
                        conversations = conversations,
                        messages = activeMessagesFrom(conversations, conversationId)
                    )
                    persistConversations(conversations)
                }
                .onFailure { error ->
                    val conversations = conversationManager.appendMessage(
                        conversations = uiState.conversations,
                        conversationId = conversationId,
                        message = ChatMessage.Error(error.message ?: "Chat request failed.")
                    )
                    uiState = uiState.copy(
                        conversations = conversations,
                        messages = activeMessagesFrom(conversations, conversationId)
                    )
                    persistConversations(conversations)
                }

            uiState = uiState.copy(isAsking = false)
        }
    }

    fun refreshStats() {
        uiState = uiState.copy(
            isLoadingStats = true,
            statsErrorMessage = null
        )

        viewModelScope.launch {
            statsRepository.getStats()
                .onSuccess { stats ->
                    uiState = uiState.copy(statsResponse = stats)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        statsErrorMessage = error.message ?: "Failed to load stats."
                    )
                }

            uiState = uiState.copy(isLoadingStats = false)
        }
    }

    fun refreshArticles() {
        uiState = uiState.copy(
            isLoadingArticles = true,
            articlesErrorMessage = null
        )

        viewModelScope.launch {
            articleRepository.getArticles()
                .onSuccess { articles ->
                    uiState = uiState.copy(articlesResponse = articles)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        articlesErrorMessage = error.message ?: "Failed to load articles."
                    )
                }

            uiState = uiState.copy(isLoadingArticles = false)
        }
    }

    fun deleteArticle(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return
        }

        uiState = uiState.copy(
            deletingArticleUrl = cleanUrl,
            articlesErrorMessage = null
        )

        viewModelScope.launch {
            articleRepository.deleteArticle(cleanUrl)
                .onSuccess {
                    refreshArticles()
                    refreshStats()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        articlesErrorMessage = error.message ?: "Failed to delete article."
                    )
                }

            uiState = uiState.copy(deletingArticleUrl = null)
        }
    }

    private fun ensureActiveConversation(): Conversation {
        val activeConversation = uiState.conversations
            .firstOrNull { it.id == uiState.activeConversationId }
        if (activeConversation != null) {
            return activeConversation
        }

        val conversation = conversationManager.createGlobalConversation()
        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            isArticleManagerOpen = false,
            conversations = listOf(conversation) + uiState.conversations,
            messages = emptyList(),
            activeUrl = ""
        )
        persistConversations(uiState.conversations)
        return conversation
    }

    private fun activeMessagesFrom(
        conversations: List<Conversation>,
        updatedConversationId: String
    ): List<ChatMessage> {
        return if (uiState.activeConversationId == updatedConversationId) {
            conversations.firstOrNull { it.id == updatedConversationId }?.messages.orEmpty()
        } else {
            uiState.messages
        }
    }

    private fun persistConversations(conversations: List<Conversation>) {
        viewModelScope.launch {
            conversationStore.save(conversations.map { it.toStoredConversation() })
        }
    }
}
