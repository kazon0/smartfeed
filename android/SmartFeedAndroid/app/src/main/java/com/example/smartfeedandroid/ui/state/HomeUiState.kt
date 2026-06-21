package com.example.smartfeedandroid.ui.state

import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.ui.home.AppTab
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation

data class HomeUiState(
    val url: String = "",
    val activeUrl: String = "",
    val activeConversationId: String? = null,
    val selectedTab: AppTab = AppTab.Home,
    val isChatOpen: Boolean = false,
    val isUploading: Boolean = false,
    val uploadProgress: UploadProgress? = null,
    val uploadStatusText: String = "",
    val uploadSummaryText: String = "",
    val uploadResponse: UploadResponse? = null,
    val errorMessage: String? = null,
    val lastSharedUrl: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList()
)

enum class UploadProgress {
    CheckingStatus,
    OpeningSavedArticle,
    UploadingNewArticle
}
