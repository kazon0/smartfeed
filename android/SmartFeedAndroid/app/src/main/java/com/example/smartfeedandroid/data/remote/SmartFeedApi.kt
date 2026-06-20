package com.example.smartfeedandroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query

interface SmartFeedApi {
    @GET("articles")
    suspend fun articles(): ArticlesResponse

    @GET("articles/status")
    suspend fun articleStatus(@Query("url") url: String): ArticleStatusResponse

    @HTTP(method = "DELETE", path = "articles", hasBody = true)
    suspend fun deleteArticle(@Body request: DeleteArticleRequest): DeleteArticleResponse

    @GET("stats")
    suspend fun stats(): StatsResponse

    @GET("insights")
    suspend fun insights(): InsightsResponse

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
data class ArticlesResponse(
    val articles: List<SavedArticle> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SavedArticle(
    val url: String = "",
    val title: String = "",
    val domain: String = "",
    @SerialName("chunk_count")
    val chunkCount: Int = 0,
    val topic: String = "其他"
)

@Serializable
data class ArticleStatusResponse(
    val exists: Boolean = false,
    val url: String = "",
    val title: String = "",
    val domain: String = "",
    val topic: String = "",
    @SerialName("chunk_count")
    val chunkCount: Int = 0
)

@Serializable
data class DeleteArticleRequest(
    val url: String
)

@Serializable
data class DeleteArticleResponse(
    val status: String = "",
    val url: String = "",
    @SerialName("deleted_chunks")
    val deletedChunks: Int = 0
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
    val length: Int = 0,
    val topic: String = ""
)

@Serializable
data class ChatRequest(
    val query: String,
    val url: String? = null,
    val history: List<ChatHistoryItem> = emptyList()
)

@Serializable
data class ChatHistoryItem(
    val role: String,
    val content: String
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
data class InsightsResponse(
    val status: String = "",
    val summary: String = "",
    val highlights: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val source: String = "",
    @SerialName("total_articles")
    val totalArticles: Int = 0
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
