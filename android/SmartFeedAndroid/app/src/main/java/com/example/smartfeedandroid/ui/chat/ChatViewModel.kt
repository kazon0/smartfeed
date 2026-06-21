package com.example.smartfeedandroid.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatStreamStatus
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.ui.model.ChatMessage
import kotlinx.coroutines.launch

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
            isAsking = true,
            streamStatusText = "正在连接实时回答...",
            streamAnswerText = ""
        )

        viewModelScope.launch {
            when (
                val result = chatCoordinator.ask(
                    query = cleanQuery,
                    activeUrl = context.activeUrl,
                    messages = context.historyMessages,
                    onStatus = { status ->
                        viewModelScope.launch {
                            uiState = uiState.copy(streamStatusText = status.displayText())
                        }
                    },
                    onDelta = { delta ->
                        viewModelScope.launch {
                            uiState = uiState.copy(
                                streamAnswerText = uiState.streamAnswerText + delta
                            )
                        }
                    }
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

            uiState = uiState.copy(
                isAsking = false,
                streamStatusText = "",
                streamAnswerText = ""
            )
        }
    }
}

private fun ChatStreamStatus.displayText(): String {
    return when (this) {
        ChatStreamStatus.Connecting -> "正在连接实时回答..."
        ChatStreamStatus.Authenticated -> "连接成功，正在准备问题..."
        ChatStreamStatus.Retrieving -> "正在检索相关文章..."
        ChatStreamStatus.Answering -> "正在生成回答..."
        ChatStreamStatus.Fallback -> "实时连接失败，正在切换普通回答..."
    }
}
