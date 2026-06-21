package com.example.smartfeedandroid.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatStreamStatus
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.ui.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val chatCoordinator = ChatCoordinator(ChatRepository())
    private var streamDrainJob: Job? = null

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
            val deltaChannel = Channel<String>(Channel.UNLIMITED)
            streamDrainJob?.cancel()
            streamDrainJob = launch {
                drainDeltas(deltaChannel)
            }

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
                        deltaChannel.trySend(delta)
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

            deltaChannel.close()
            streamDrainJob?.join()
            streamDrainJob = null

            uiState = uiState.copy(
                isAsking = false,
                streamStatusText = "",
                streamAnswerText = ""
            )
        }
    }

    private suspend fun drainDeltas(deltaChannel: Channel<String>) {
        val pending = StringBuilder()
        for (delta in deltaChannel) {
            pending.append(delta)
            while (pending.isNotEmpty()) {
                val takeCount = when {
                    pending.length >= 6 -> 3
                    pending.length >= 3 -> 2
                    else -> 1
                }
                val piece = pending.substring(0, takeCount)
                pending.delete(0, takeCount)
                uiState = uiState.copy(streamAnswerText = uiState.streamAnswerText + piece)
                delay(28)
            }
        }
    }
}

private fun ChatStreamStatus.displayText(): String {
    return when (this) {
        ChatStreamStatus.Connecting -> "正在连接实时回答..."
        ChatStreamStatus.Authenticated -> "连接成功，正在准备问题..."
        ChatStreamStatus.Retrieving -> "正在快速检索知识库..."
        ChatStreamStatus.Answering -> "正在生成回答..."
        ChatStreamStatus.Fallback -> "实时连接失败，正在切换普通回答..."
    }
}
