package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.auth.AuthSession
import com.example.smartfeedandroid.data.remote.ChatRequest
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatHistoryItem
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class ChatRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api,
    private val webSocketClient: OkHttpClient = SmartFeedNetwork.okHttpClient,
    private val json: Json = SmartFeedNetwork.json
) {
    suspend fun ask(
        query: String,
        url: String?,
        history: List<ChatHistoryItem> = emptyList()
    ): Result<ChatResponse> {
        return runCatching {
            api.chat(
                ChatRequest(
                    query = query,
                    url = url?.takeIf { it.isNotBlank() },
                    history = history
                )
            )
        }
    }

    fun streamChat(
        query: String,
        url: String?,
        history: List<ChatHistoryItem> = emptyList()
    ): Flow<ChatStreamEvent> = flow {
        val token = AuthSession.accessToken()
        if (token.isNullOrBlank()) {
            throw IOException("请先登录。")
        }

        var lastFailure: Throwable = IOException("WebSocket chat failed.")
        repeat(MAX_CHAT_STREAM_ATTEMPTS) { attempt ->
            var receivedDelta = false
            try {
                streamChatAttempt(
                    token = token,
                    query = query,
                    url = url,
                    history = history
                ).collect { event ->
                    if (event is ChatStreamEvent.Delta) {
                        receivedDelta = true
                    }
                    emit(event)
                }
                return@flow
            } catch (failure: Throwable) {
                lastFailure = failure
            }

            if (!shouldRetryChatStream(attempt, receivedDelta, lastFailure)) {
                throw lastFailure
            }
            emit(ChatStreamEvent.Status(ChatStreamStatus.Reconnecting))
            delay(CHAT_RECONNECT_DELAY_MILLIS)
        }
        throw lastFailure
    }

    private fun streamChatAttempt(
        token: String,
        query: String,
        url: String?,
        history: List<ChatHistoryItem>
    ): Flow<ChatStreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(SmartFeedNetwork.chatWebSocketUrl(token))
            .build()
        val payload = WebSocketChatRequest(
            query = query,
            url = url?.takeIf { it.isNotBlank() },
            mode = if (url.isNullOrBlank()) "global" else "page",
            history = history
        )

        val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: OkHttpResponse) {
                        trySend(ChatStreamEvent.Status(ChatStreamStatus.Connecting))
                        webSocket.send(json.encodeToString(payload))
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val event = runCatching {
                            json.parseToJsonElement(text).jsonObject
                        }.getOrNull() ?: return

                        when (event["type"]?.jsonPrimitive?.contentOrNull) {
                            "status" -> {
                                chatStreamStatusFrom(event["stage"]?.jsonPrimitive?.contentOrNull)
                                    ?.let { trySend(ChatStreamEvent.Status(it)) }
                            }
                            "delta" -> {
                                event["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { trySend(ChatStreamEvent.Delta(it)) }
                            }
                            "completed" -> {
                                val responseElement = event["response"]
                                if (responseElement == null) {
                                    close(NonRetryableWebSocketException("WebSocket response missing."))
                                    webSocket.close(1000, null)
                                    return
                                }
                                val chatResponse = runCatching {
                                    json.decodeFromJsonElement(
                                        ChatResponse.serializer(),
                                        responseElement
                                    )
                                }.getOrElse {
                                    close(NonRetryableWebSocketException("Invalid WebSocket response."))
                                    webSocket.close(1000, null)
                                    return
                                }
                                trySend(ChatStreamEvent.Completed(chatResponse))
                                close()
                                webSocket.close(1000, null)
                            }
                            "error" -> {
                                val message = event["message"]?.jsonPrimitive?.contentOrNull
                                    ?: "WebSocket chat failed."
                                close(NonRetryableWebSocketException(message))
                                webSocket.close(1000, null)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkHttpResponse?) {
                        close(t)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        close(IOException("WebSocket closed before response."))
                    }
                }

        val webSocket = webSocketClient.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }
}

enum class ChatStreamStatus {
    Connecting,
    Reconnecting,
    Authenticated,
    Retrieving,
    Answering,
    Fallback
}

sealed interface ChatStreamEvent {
    data class Status(val status: ChatStreamStatus) : ChatStreamEvent
    data class Delta(val text: String) : ChatStreamEvent
    data class Completed(val response: ChatResponse) : ChatStreamEvent
}

internal class NonRetryableWebSocketException(message: String) : IOException(message)

internal fun shouldRetryChatStream(
    attempt: Int,
    receivedDelta: Boolean,
    failure: Throwable
): Boolean {
    return attempt + 1 < MAX_CHAT_STREAM_ATTEMPTS &&
        !receivedDelta &&
        failure is IOException &&
        failure !is NonRetryableWebSocketException
}

private const val MAX_CHAT_STREAM_ATTEMPTS = 2
private const val CHAT_RECONNECT_DELAY_MILLIS = 350L

@Serializable
private data class WebSocketChatRequest(
    val query: String,
    val mode: String,
    val url: String? = null,
    val history: List<ChatHistoryItem> = emptyList()
)

private fun chatStreamStatusFrom(stage: String?): ChatStreamStatus? {
    return when (stage) {
        "connected" -> ChatStreamStatus.Connecting
        "authenticated" -> ChatStreamStatus.Authenticated
        "retrieving" -> ChatStreamStatus.Retrieving
        "answering" -> ChatStreamStatus.Answering
        else -> null
    }
}
