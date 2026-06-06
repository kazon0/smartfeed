package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.remote.ChatRequest
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork

class ChatRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun ask(query: String, url: String?): Result<ChatResponse> {
        return runCatching {
            api.chat(
                ChatRequest(
                    query = query,
                    url = url?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
}
