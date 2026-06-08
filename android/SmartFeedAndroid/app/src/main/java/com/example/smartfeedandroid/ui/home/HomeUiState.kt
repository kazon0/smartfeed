package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.remote.ArticlesResponse
import com.example.smartfeedandroid.data.remote.StatsResponse
import com.example.smartfeedandroid.data.remote.UploadResponse

data class HomeUiState(
    val url: String = "",
    val activeUrl: String = "",
    val activeConversationId: String? = null,
    val selectedTab: AppTab = AppTab.Home,
    val isChatOpen: Boolean = false,
    val isArticleManagerOpen: Boolean = false,
    val query: String = "",
    val isUploading: Boolean = false,
    val isAsking: Boolean = false,
    val isLoadingStats: Boolean = false,
    val isLoadingArticles: Boolean = false,
    val deletingArticleUrl: String? = null,
    val uploadResponse: UploadResponse? = null,
    val statsResponse: StatsResponse? = null,
    val articlesResponse: ArticlesResponse? = null,
    val errorMessage: String? = null,
    val statsErrorMessage: String? = null,
    val articlesErrorMessage: String? = null,
    val lastSharedUrl: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList()
)
