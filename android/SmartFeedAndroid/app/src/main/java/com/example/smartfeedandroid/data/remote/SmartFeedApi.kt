package com.example.smartfeedandroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface SmartFeedApi {
    @POST("upload")
    suspend fun upload(@Body request: UploadRequest): UploadResponse
}

@Serializable
data class UploadRequest(
    val url: String
)

@Serializable
data class UploadResponse(
    val status: String,
    val data: ParsedPage? = null,
    @SerialName("stored_chunks")
    val storedChunks: Int = 0,
    val summary: String = "",
    val error: String? = null
)

@Serializable
data class ParsedPage(
    val url: String = "",
    val title: String = "",
    val metadata: PageMetadata? = null
)

@Serializable
data class PageMetadata(
    val parser: String? = null,
    val length: Int = 0
)
