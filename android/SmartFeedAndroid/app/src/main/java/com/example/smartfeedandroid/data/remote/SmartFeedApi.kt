package com.example.smartfeedandroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SmartFeedApi {
    @GET("stats")
    suspend fun stats(): StatsResponse

    @POST("upload")
    suspend fun upload(@Body request: UploadRequest): UploadResponse

    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
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

@Serializable
data class ChatRequest(
    val query: String,
    val url: String? = null
)

@Serializable
data class ChatResponse(
    val status: String = "",
    @SerialName("error_code")
    val errorCode: String? = null,
    val message: String = "",
    val answer: String = "",
    val sources: List<ChatSource> = emptyList(),
    @SerialName("source_type")
    val sourceType: String = "",
    val intent: String = "",
    @SerialName("retrieval_scope")
    val retrievalScope: String = "",
    @SerialName("fallback_policy")
    val fallbackPolicy: String = ""
)

@Serializable
data class ChatSource(
    val url: String = "",
    val title: String = "",
    @SerialName("display_title")
    val displayTitle: String = "",
    @SerialName("section_title")
    val sectionTitle: String = "",
    @SerialName("source_summary")
    val sourceSummary: String = "",
    val score: Double? = null
)

@Serializable
data class StatsResponse(
    @SerialName("total_chunks")
    val totalChunks: Int = 0,
    @SerialName("total_articles")
    val totalArticles: Int = 0,
    val topics: List<TopicDistribution> = emptyList(),
    val domains: List<DomainDistribution> = emptyList(),
    val articles: List<ArticleDistribution> = emptyList()
)

@Serializable
data class TopicDistribution(
    val topic: String = "",
    @SerialName("chunk_count")
    val chunkCount: Int = 0,
    val percentage: Double = 0.0
)

@Serializable
data class DomainDistribution(
    val domain: String = "",
    @SerialName("chunk_count")
    val chunkCount: Int = 0,
    val percentage: Double = 0.0
)

@Serializable
data class ArticleDistribution(
    val url: String = "",
    val title: String = "",
    val domain: String = "",
    @SerialName("chunk_count")
    val chunkCount: Int = 0,
    val percentage: Double = 0.0
)
