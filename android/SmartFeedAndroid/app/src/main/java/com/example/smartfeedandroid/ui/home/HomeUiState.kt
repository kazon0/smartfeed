package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.remote.UploadResponse

data class HomeUiState(
    val url: String = "",
    val activeUrl: String = "",
    val activeConversationId: String? = null,
    val selectedTab: AppTab = AppTab.Home,
    val isChatOpen: Boolean = false,
    val query: String = "",
    val isUploading: Boolean = false,
    val isAsking: Boolean = false,
    val uploadResponse: UploadResponse? = null,
    val errorMessage: String? = null,
    val lastSharedUrl: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ChatMessage> = emptyList()
)
