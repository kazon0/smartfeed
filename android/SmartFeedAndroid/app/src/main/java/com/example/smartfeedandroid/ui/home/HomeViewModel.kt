package com.example.smartfeedandroid.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.local.ConversationStore
import com.example.smartfeedandroid.data.local.StoredChatMessage
import com.example.smartfeedandroid.data.local.StoredConversation
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.data.repository.StatsRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadRepository = UploadRepository()
    private val chatRepository = ChatRepository()
    private val statsRepository = StatsRepository()
    private val conversationStore = ConversationStore(application.applicationContext)

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        val conversations = conversationStore.load().map { it.toConversation() }
        uiState = uiState.copy(conversations = conversations)
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
        if (cleanUrl.isBlank() || cleanUrl == uiState.lastSharedUrl) {
            return
        }

        uiState = uiState.copy(
            url = cleanUrl,
            lastSharedUrl = cleanUrl,
            selectedTab = AppTab.Home,
            isChatOpen = false
        )
        upload()
    }

    fun selectTab(tab: AppTab) {
        uiState = if (tab == AppTab.Home) {
            uiState.copy(
                selectedTab = tab,
                isChatOpen = false
            )
        } else {
            uiState.copy(selectedTab = tab)
        }
        if (tab == AppTab.Analysis) {
            refreshStats()
        }
    }

    fun showConversationList() {
        uiState = uiState.copy(
            selectedTab = AppTab.Home,
            isChatOpen = false,
            errorMessage = null
        )
    }

    fun selectConversation(conversationId: String) {
        val conversation = uiState.conversations.firstOrNull { it.id == conversationId } ?: return
        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            activeUrl = conversation.url,
            messages = conversation.messages,
            uploadResponse = null,
            errorMessage = null
        )
    }

    fun startGlobalConversation() {
        val conversation = Conversation(
            id = createConversationId(),
            title = "Global knowledge chat",
            updatedAtMillis = System.currentTimeMillis()
        )

        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
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
                    val summaryMessages = response.summary
                        .takeIf { it.isNotBlank() }
                        ?.let { listOf(ChatMessage.Summary(it)) }
                        ?: emptyList()
                    val conversation = Conversation(
                        id = createConversationId(),
                        title = title,
                        url = parsedUrl,
                        summary = response.summary,
                        status = response.status,
                        storedChunks = response.storedChunks,
                        updatedAtMillis = System.currentTimeMillis(),
                        messages = summaryMessages
                    )

                    uiState = uiState.copy(
                        uploadResponse = null,
                        activeConversationId = conversation.id,
                        selectedTab = AppTab.Home,
                        isChatOpen = true,
                        activeUrl = parsedUrl,
                        conversations = listOf(conversation) + uiState.conversations,
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
            conversations = updateConversationMessages(
                conversationId = conversationId,
                messages = updatedMessages
            ),
            activeConversationId = conversationId,
            selectedTab = AppTab.Home,
            isChatOpen = true,
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
                    val conversations = appendConversationMessage(
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
                    val conversations = appendConversationMessage(
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

    private fun ensureActiveConversation(): Conversation {
        val activeConversation = uiState.conversations
            .firstOrNull { it.id == uiState.activeConversationId }
        if (activeConversation != null) {
            return activeConversation
        }

        val conversation = Conversation(
            id = createConversationId(),
            title = "Global knowledge chat",
            updatedAtMillis = System.currentTimeMillis()
        )
        uiState = uiState.copy(
            activeConversationId = conversation.id,
            selectedTab = AppTab.Home,
            isChatOpen = true,
            conversations = listOf(conversation) + uiState.conversations,
            messages = emptyList(),
            activeUrl = ""
        )
        persistConversations(uiState.conversations)
        return conversation
    }

    private fun updateConversationMessages(
        conversationId: String,
        messages: List<ChatMessage>
    ): List<Conversation> {
        return uiState.conversations.map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    messages = messages,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                conversation
            }
        }
    }

    private fun appendConversationMessage(
        conversationId: String,
        message: ChatMessage
    ): List<Conversation> {
        return uiState.conversations.map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    messages = conversation.messages + message,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                conversation
            }
        }
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

    private fun createConversationId(): String {
        return "${System.currentTimeMillis()}-${System.nanoTime()}"
    }

    private fun persistConversations(conversations: List<Conversation>) {
        conversationStore.save(conversations.map { it.toStoredConversation() })
    }

    private fun StoredConversation.toConversation(): Conversation {
        return Conversation(
            id = id,
            title = title,
            url = url,
            summary = summary,
            status = status,
            storedChunks = storedChunks,
            updatedAtMillis = updatedAtMillis,
            messages = messages.mapNotNull { it.toChatMessage() }
        )
    }

    private fun Conversation.toStoredConversation(): StoredConversation {
        return StoredConversation(
            id = id,
            title = title,
            url = url,
            summary = summary,
            status = status,
            storedChunks = storedChunks,
            updatedAtMillis = updatedAtMillis,
            messages = messages.map { it.toStoredChatMessage() }
        )
    }

    private fun StoredChatMessage.toChatMessage(): ChatMessage? {
        return when (type) {
            MESSAGE_TYPE_USER -> ChatMessage.User(text)
            MESSAGE_TYPE_SUMMARY -> ChatMessage.Summary(text)
            MESSAGE_TYPE_ASSISTANT -> response?.let { ChatMessage.Assistant(it) }
            MESSAGE_TYPE_ERROR -> ChatMessage.Error(text)
            else -> null
        }
    }

    private fun ChatMessage.toStoredChatMessage(): StoredChatMessage {
        return when (this) {
            is ChatMessage.User -> StoredChatMessage(type = MESSAGE_TYPE_USER, text = text)
            is ChatMessage.Summary -> StoredChatMessage(type = MESSAGE_TYPE_SUMMARY, text = text)
            is ChatMessage.Assistant -> StoredChatMessage(
                type = MESSAGE_TYPE_ASSISTANT,
                response = response
            )
            is ChatMessage.Error -> StoredChatMessage(type = MESSAGE_TYPE_ERROR, text = text)
        }
    }

    private companion object {
        const val MESSAGE_TYPE_USER = "user"
        const val MESSAGE_TYPE_SUMMARY = "summary"
        const val MESSAGE_TYPE_ASSISTANT = "assistant"
        const val MESSAGE_TYPE_ERROR = "error"
    }
}
