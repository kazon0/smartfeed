package com.example.smartfeedandroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface SmartFeedApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): AuthUser

    @PATCH("auth/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): AuthUser

    @GET("articles")
    suspend fun articles(): ArticlesResponse

    @GET("conversations")
    suspend fun conversations(): ConversationListResponse

    @PUT("conversations/{id}")
    suspend fun putConversation(
        @Path("id") id: String,
        @Body request: ConversationSyncRequest
    ): ConversationSyncResponse

    @DELETE("conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String): DeleteConversationResponse

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
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name")
    val displayName: String = ""
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name")
    val displayName: String,
    val bio: String = ""
)

@Serializable
data class AuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "bearer",
    val user: AuthUser
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    @SerialName("display_name")
    val displayName: String = "",
    val bio: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = ""
)

@Serializable
data class ConversationListResponse(
    val conversations: List<ConversationSyncResponse> = emptyList(),
    val total: Int = 0
)

@Serializable
data class ConversationSyncResponse(
    val id: String,
    val url: String = "",
    val title: String = "",
    @SerialName("source_url")
    val sourceUrl: String = "",
    val summary: String = "",
    val status: String = "",
    val topic: String = "",
    @SerialName("stored_chunks")
    val storedChunks: Int = 0,
    @SerialName("created_at_millis")
    val createdAtMillis: Long = 0,
    @SerialName("updated_at_millis")
    val updatedAtMillis: Long = 0,
    val messages: List<ConversationMessageSync> = emptyList()
)

@Serializable
data class ConversationSyncRequest(
    val title: String = "",
    @SerialName("source_url")
    val sourceUrl: String = "",
    val summary: String = "",
    val status: String = "",
    val topic: String = "",
    @SerialName("stored_chunks")
    val storedChunks: Int = 0,
    @SerialName("created_at_millis")
    val createdAtMillis: Long = 0,
    @SerialName("updated_at_millis")
    val updatedAtMillis: Long = 0,
    val messages: List<ConversationMessageSync> = emptyList()
)

@Serializable
data class ConversationMessageSync(
    val type: String,
    val text: String = "",
    val response: ChatResponse? = null
)

@Serializable
data class DeleteConversationResponse(
    val status: String = "",
    val id: String = ""
)

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
