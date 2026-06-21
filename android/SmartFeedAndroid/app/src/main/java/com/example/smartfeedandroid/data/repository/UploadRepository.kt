package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.auth.AuthSession
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork
import com.example.smartfeedandroid.data.remote.UploadRequest
import com.example.smartfeedandroid.data.remote.UploadResponse
import kotlinx.coroutines.suspendCancellableCoroutine
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

class UploadRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api,
    private val webSocketClient: OkHttpClient = SmartFeedNetwork.okHttpClient,
    private val json: Json = SmartFeedNetwork.json
) {
    suspend fun upload(url: String): Result<UploadResponse> {
        return runCatching {
            api.upload(UploadRequest(url = url))
        }
    }

    suspend fun uploadStreaming(
        url: String,
        onStatus: (UploadStreamStatus) -> Unit = {},
        onSummaryDelta: (String) -> Unit = {}
    ): Result<UploadResponse> {
        val token = AuthSession.accessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(IOException("请先登录。"))
        }

        return runCatching {
            suspendCancellableCoroutine { continuation ->
                var webSocket: WebSocket? = null
                val request = Request.Builder()
                    .url(SmartFeedNetwork.uploadWebSocketUrl(token))
                    .build()
                val payload = UploadRequest(url = url)

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: OkHttpResponse) {
                        onStatus(UploadStreamStatus.Connecting)
                        webSocket.send(json.encodeToString(payload))
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val event = runCatching {
                            json.parseToJsonElement(text).jsonObject
                        }.getOrNull() ?: return

                        when (event["type"]?.jsonPrimitive?.contentOrNull) {
                            "status" -> {
                                uploadStreamStatusFrom(event["stage"]?.jsonPrimitive?.contentOrNull)
                                    ?.let(onStatus)
                            }
                            "delta" -> {
                                val target = event["target"]?.jsonPrimitive?.contentOrNull
                                if (target == "summary") {
                                    event["text"]?.jsonPrimitive?.contentOrNull
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(onSummaryDelta)
                                }
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
                                val uploadResponse = json.decodeFromJsonElement(
                                    UploadResponse.serializer(),
                                    responseElement
                                )
                                if (continuation.isActive) {
                                    continuation.resume(uploadResponse)
                                }
                                webSocket.close(1000, null)
                            }
                            "error" -> {
                                val message = event["message"]?.jsonPrimitive?.contentOrNull
                                    ?: "WebSocket upload failed."
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

enum class UploadStreamStatus {
    Connecting,
    Authenticated,
    Parsing,
    Summarizing,
    Classifying,
    Storing,
    Fallback
}

private fun uploadStreamStatusFrom(stage: String?): UploadStreamStatus? {
    return when (stage) {
        "connected" -> UploadStreamStatus.Connecting
        "authenticated" -> UploadStreamStatus.Authenticated
        "parsing" -> UploadStreamStatus.Parsing
        "summarizing" -> UploadStreamStatus.Summarizing
        "classifying" -> UploadStreamStatus.Classifying
        "storing" -> UploadStreamStatus.Storing
        else -> null
    }
}
