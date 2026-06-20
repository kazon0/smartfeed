package com.example.smartfeedandroid.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.auth.AuthSession
import com.example.smartfeedandroid.data.remote.AuthUser
import com.example.smartfeedandroid.data.repository.AuthRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class AuthUiState(
    val user: AuthUser? = null,
    val isCheckingSession: Boolean = AuthSession.state.value.hasToken,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    var uiState by mutableStateOf(AuthUiState())
        private set

    init {
        viewModelScope.launch {
            AuthSession.state.collect { session ->
                uiState = uiState.copy(
                    user = session.user,
                    isCheckingSession = session.hasToken && session.user == null,
                    isSubmitting = false
                )
            }
        }
        if (AuthSession.state.value.hasToken) {
            verifySession()
        }
    }

    fun login(email: String, password: String) {
        submit { repository.login(email, password) }
    }

    fun register(email: String, password: String, displayName: String) {
        submit { repository.register(email, password, displayName) }
    }

    fun logout() {
        repository.logout()
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun verifySession() {
        viewModelScope.launch {
            repository.currentUser().onFailure {
                repository.logout()
            }
        }
    }

    private fun submit(action: suspend () -> Result<AuthUser>) {
        uiState = uiState.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            action()
                .onSuccess { user ->
                    uiState = uiState.copy(user = user, isSubmitting = false)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isSubmitting = false,
                        errorMessage = authErrorMessage(error)
                    )
                }
        }
    }

    private fun authErrorMessage(error: Throwable): String {
        return when ((error as? HttpException)?.code()) {
            401 -> "邮箱或密码不正确。"
            409 -> "该邮箱已经注册。"
            422 -> "请检查邮箱格式和密码长度。"
            else -> error.message ?: "无法连接到服务器。"
        }
    }
}
