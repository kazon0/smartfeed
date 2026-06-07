package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.remote.UploadResponse

data class HomeUiState(
    val url: String = "",
    val activeUrl: String = "",
    val query: String = "",
    val isUploading: Boolean = false,
    val isAsking: Boolean = false,
    val uploadResponse: UploadResponse? = null,
    val errorMessage: String? = null,
    val messages: List<ChatMessage> = emptyList()
)
