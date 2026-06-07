package com.example.smartfeedandroid.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

class HomeViewModel(
    private val uploadRepository: UploadRepository = UploadRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {
    var uiState by mutableStateOf(HomeUiState())
        private set

    fun onUrlChange(value: String) {
        uiState = uiState.copy(url = value)
    }

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
    }

    fun upload() {
        val cleanUrl = uiState.url.trim()
        if (cleanUrl.isEmpty()) {
            uiState = uiState.copy(
                errorMessage = "Please enter a URL.",
                uploadResponse = null
            )
            return
        }

        uiState = uiState.copy(
            isUploading = true,
            errorMessage = null,
            uploadResponse = null
        )

        viewModelScope.launch {
            uploadRepository.upload(cleanUrl)
                .onSuccess { response ->
                    uiState = uiState.copy(
                        uploadResponse = response,
                        activeUrl = response.data?.url?.takeIf { it.isNotBlank() } ?: cleanUrl,
                        messages = emptyList()
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        errorMessage = error.message ?: "Upload failed."
                    )
                }

            uiState = uiState.copy(isUploading = false)
        }
    }

    fun ask() {
        val cleanQuery = uiState.query.trim()
        if (cleanQuery.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Please enter a question.")
            return
        }

        val activeUrl = uiState.activeUrl
        uiState = uiState.copy(
            messages = uiState.messages + ChatMessage.User(cleanQuery),
            query = "",
            isAsking = true,
            errorMessage = null
        )

        viewModelScope.launch {
            chatRepository.ask(cleanQuery, activeUrl)
                .onSuccess { response ->
                    uiState = uiState.copy(
                        messages = uiState.messages + ChatMessage.Assistant(response)
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        messages = uiState.messages + ChatMessage.Error(
                            error.message ?: "Chat request failed."
                        )
                    )
                }

            uiState = uiState.copy(isAsking = false)
        }
    }
}
