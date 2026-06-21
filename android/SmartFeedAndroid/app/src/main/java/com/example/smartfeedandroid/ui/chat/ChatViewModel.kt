package com.example.smartfeedandroid.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatStreamStatus
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.ui.model.ChatMessage
import kotlinx.coroutines.flow.collect
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
            streamingConversationId = context.conversationId,
            streamStatusText = "正在连接实时回答...",
            streamAnswerText = ""
        )

        viewModelScope.launch {
            var result: ChatResult? = null
            chatCoordinator.askFlow(
                query = cleanQuery,
                activeUrl = context.activeUrl,
                messages = context.historyMessages
            ).collect { event ->
                when (event) {
                    is ChatFlowEvent.Status -> {
                        val status = event.status
                        uiState = uiState.copy(streamStatusText = status.displayText())
                    }
                    is ChatFlowEvent.Delta -> {
                        uiState = uiState.copy(
                            streamAnswerText = uiState.streamAnswerText + event.text
                        )
                    }
                    is ChatFlowEvent.Result -> result = event.result
                }
            }

            when (val finalResult = result) {
                is ChatResult.Answer -> {
                    appendResultMessage(
                        context.conversationId,
                        ChatMessage.Assistant(finalResult.response)
                    )
                }
                is ChatResult.Failed -> {
                    appendResultMessage(
                        context.conversationId,
                        ChatMessage.Error(finalResult.message)
                    )
                }
                null -> Unit
            }

            uiState = uiState.copy(
                isAsking = false,
                streamingConversationId = null,
                streamStatusText = "",
                streamAnswerText = ""
            )
        }
    }
}

private fun ChatStreamStatus.displayText(): String {
    return when (this) {
        ChatStreamStatus.Connecting -> "正在连接实时回答..."
        ChatStreamStatus.Reconnecting -> "连接中断，正在重连..."
        ChatStreamStatus.Authenticated -> "连接成功，正在准备问题..."
        ChatStreamStatus.Retrieving -> "正在快速检索知识库..."
        ChatStreamStatus.Answering -> "正在生成回答..."
        ChatStreamStatus.Fallback -> "实时连接失败，正在切换普通回答..."
    }
}
