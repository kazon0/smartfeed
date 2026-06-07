package com.example.smartfeedandroid.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

class HomeViewModel(
    private val uploadRepository: UploadRepository = UploadRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {
    var uiState by mutableStateOf(HomeUiState())
        private set

    fun onUrlChange(value: String) {
        uiState = uiState.copy(url = value)
    }

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
    }

    fun selectConversation(conversationId: String) {
        val conversation = uiState.conversations.firstOrNull { it.id == conversationId } ?: return
        uiState = uiState.copy(
            activeConversationId = conversation.id,
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
            activeUrl = "",
            uploadResponse = null,
            errorMessage = null,
            conversations = listOf(conversation) + uiState.conversations,
            messages = emptyList()
        )
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
                        uploadResponse = response,
                        activeConversationId = conversation.id,
                        activeUrl = parsedUrl,
                        conversations = listOf(conversation) + uiState.conversations,
                        messages = conversation.messages
                    )
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
            activeUrl = activeUrl,
            messages = updatedMessages,
            query = "",
            isAsking = true,
            errorMessage = null
        )

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
                }

            uiState = uiState.copy(isAsking = false)
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
            conversations = listOf(conversation) + uiState.conversations,
            messages = emptyList(),
            activeUrl = ""
        )
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
}
