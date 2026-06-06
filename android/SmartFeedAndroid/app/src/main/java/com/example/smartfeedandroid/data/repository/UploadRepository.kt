package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork
import com.example.smartfeedandroid.data.remote.UploadRequest
import com.example.smartfeedandroid.data.remote.UploadResponse

class UploadRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun upload(url: String): Result<UploadResponse> {
        return runCatching {
            api.upload(UploadRequest(url = url))
        }
    }
}
