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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

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

    suspend fun askStreaming(
        query: String,
        url: String?,
        history: List<ChatHistoryItem> = emptyList(),
        onStatus: (ChatStreamStatus) -> Unit = {},
        onDelta: (String) -> Unit = {}
    ): Result<ChatResponse> {
        val token = AuthSession.accessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(IOException("请先登录。"))
        }

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                var webSocket: WebSocket? = null
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
                        onStatus(ChatStreamStatus.Connecting)
                        webSocket.send(json.encodeToString(payload))
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val event = runCatching {
                            json.parseToJsonElement(text).jsonObject
                        }.getOrNull() ?: return

                        when (event["type"]?.jsonPrimitive?.contentOrNull) {
                            "status" -> {
                                chatStreamStatusFrom(event["stage"]?.jsonPrimitive?.contentOrNull)
                                    ?.let(onStatus)
                            }
                            "delta" -> {
                                event["text"]?.jsonPrimitive?.contentOrNull
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(onDelta)
                            }
                            "completed" -> {
                                val responseElement = event["response"]
                                if (responseElement == null) {
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(IOException("WebSocket response missing."))
                                    }
                                    webSocket.close(1000, null)
                                    return
                                }
                                val chatResponse = json.decodeFromJsonElement(
                                    ChatResponse.serializer(),
                                    responseElement
                                )
                                if (continuation.isActive) {
                                    continuation.resume(chatResponse)
                                }
                                webSocket.close(1000, null)
                            }
                            "error" -> {
                                val message = event["message"]?.jsonPrimitive?.contentOrNull
                                    ?: "WebSocket chat failed."
                                if (continuation.isActive) {
                                    continuation.resumeWithException(IOException(message))
                                }
                                webSocket.close(1000, null)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkHttpResponse?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(t)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("WebSocket closed before response."))
                        }
                    }
                }

                webSocket = webSocketClient.newWebSocket(request, listener)
                continuation.invokeOnCancellation {
                    webSocket?.cancel()
                }
            }
        }
    }
}

enum class ChatStreamStatus {
    Connecting,
    Authenticated,
    Retrieving,
    Answering,
    Fallback
}

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
