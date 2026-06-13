package com.example.smartfeedandroid.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatRepository
import kotlinx.coroutines.launch

import com.example.smartfeedandroid.ui.model.ChatMessage
class ChatViewModel : ViewModel() {
    private val chatCoordinator = ChatCoordinator(ChatRepository())

    var uiState by mutableStateOf(ChatUiState())
        private set

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
    }

    fun ask(
        prepareUserMessage: (String) -> ChatSendContext?,
        appendResultMessage: (String, ChatMessage) -> Unit,
        showError: (String) -> Unit
    ) {
        val cleanQuery = uiState.query.trim()
        if (cleanQuery.isEmpty()) {
            showError("请输入问题。")
            return
        }

        val context = prepareUserMessage(cleanQuery) ?: return
        uiState = uiState.copy(
            query = "",
            isAsking = true
        )

        viewModelScope.launch {
            when (
                val result = chatCoordinator.ask(
                    query = cleanQuery,
                    activeUrl = context.activeUrl,
                    messages = context.historyMessages
                )
            ) {
                is ChatResult.Answer -> {
                    appendResultMessage(
                        context.conversationId,
                        ChatMessage.Assistant(result.response)
                    )
                }
                is ChatResult.Failed -> {
                    appendResultMessage(
                        context.conversationId,
                        ChatMessage.Error(result.message)
                    )
                }
            }

            uiState = uiState.copy(isAsking = false)
        }
    }
}

data class ChatUiState(
    val query: String = "",
    val isAsking: Boolean = false
)

data class ChatSendContext(
    val conversationId: String,
    val activeUrl: String,
    val historyMessages: List<ChatMessage>
)
