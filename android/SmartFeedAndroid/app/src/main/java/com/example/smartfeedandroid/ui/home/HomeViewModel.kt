package com.example.smartfeedandroid.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.data.repository.ArticleRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import com.example.smartfeedandroid.ui.chat.ChatSendContext
import kotlinx.coroutines.launch

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadRepository = UploadRepository()
    private val articleRepository = ArticleRepository()
    private val articleUploadCoordinator = ArticleUploadCoordinator(
        articleRepository = articleRepository,
        uploadRepository = uploadRepository
    )
    private val conversationStore = ConversationStore(application.applicationContext)
    private val conversationManager = ConversationManager()
    private val conversationCoordinator = ConversationCoordinator(conversationManager)

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

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun showError(message: String) {
        uiState = uiState.copy(errorMessage = message)
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
            isArticleManagerOpen = true
        )
    }

    fun closeArticleManager() {
        uiState = uiState.copy(
            isArticleManagerOpen = false
        )
    }

    fun selectConversation(conversationId: String) {
        uiState = conversationCoordinator.selectConversation(uiState, conversationId)
    }

    fun deleteConversation(conversationId: String) {
        uiState = conversationCoordinator.deleteConversation(uiState, conversationId)
        persistConversations(uiState.conversations)
    }

    fun startGlobalConversation() {
        uiState = conversationCoordinator.startGlobalConversation(uiState)
        persistConversations(uiState.conversations)
    }

    fun startArticleConversation(article: SavedArticle) {
        uiState = conversationCoordinator.startArticleConversation(uiState, article)
        persistConversations(uiState.conversations)
    }

    fun upload() {
        val cleanUrl = uiState.url.trim()
        if (cleanUrl.isEmpty()) {
            uiState = uiState.copy(
                errorMessage = "请输入文章链接。",
                uploadResponse = null
            )
            return
        }

        uiState = uiState.copy(
            isUploading = true,
            uploadProgress = UploadProgress.CheckingStatus,
            errorMessage = null,
            uploadResponse = null
        )

        viewModelScope.launch {
            when (
                val result = articleUploadCoordinator.openOrUpload(cleanUrl) { progress ->
                    uiState = uiState.copy(uploadProgress = progress)
                }
            ) {
                is ArticleUploadResult.ExistingArticle -> {
                    val article = result.article
                    uiState = conversationCoordinator.startExistingArticleConversation(
                        state = uiState,
                        url = article.url.ifBlank { cleanUrl },
                        title = article.title.ifBlank { cleanUrl },
                        storedChunks = article.chunkCount
                    )
                    uiState = uiState.copy(
                        uploadProgress = null,
                        isUploading = false
                    )
                    persistConversations(uiState.conversations)
                    return@launch
                }
                is ArticleUploadResult.Uploaded -> {
                    val response = result.response
                    val parsedUrl = response.data?.url?.takeIf { it.isNotBlank() } ?: cleanUrl
                    val title = response.data?.title?.takeIf { it.isNotBlank() } ?: parsedUrl
                    uiState = conversationCoordinator.openUploadedConversation(
                        state = uiState,
                        url = parsedUrl,
                        title = title,
                        summary = response.summary,
                        status = response.status,
                        storedChunks = response.storedChunks
                    )
                    persistConversations(uiState.conversations)
                }
                is ArticleUploadResult.Failed -> {
                    uiState = uiState.copy(
                        errorMessage = result.message,
                        uploadProgress = null
                    )
                }
            }

            uiState = uiState.copy(
                isUploading = false,
                uploadProgress = null
            )
        }
    }

    fun prepareUserMessage(query: String): ChatSendContext? {
        val ensured = conversationCoordinator.ensureActiveConversation(uiState)
        uiState = ensured.first
        val conversation = ensured.second
        val conversationId = conversation.id
        val activeUrl = conversation.url
        val userMessage = ChatMessage.User(query)
        val updatedMessages = conversation.messages + userMessage

        uiState = conversationCoordinator.updateMessages(
            state = uiState.copy(
                activeUrl = activeUrl,
            ),
            conversationId = conversationId,
            messages = updatedMessages
        )
        persistConversations(uiState.conversations)
        return ChatSendContext(
            conversationId = conversationId,
            activeUrl = activeUrl,
            historyMessages = conversation.messages
        )
    }

    fun appendChatMessage(conversationId: String, message: ChatMessage) {
        uiState = conversationCoordinator.appendMessage(
            state = uiState,
            conversationId = conversationId,
            message = message
        )
        persistConversations(uiState.conversations)
    }

    private fun persistConversations(conversations: List<Conversation>) {
        viewModelScope.launch {
            conversationStore.save(conversations.map { it.toStoredConversation() })
        }
    }

}
