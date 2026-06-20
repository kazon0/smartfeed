package com.example.smartfeedandroid.data.auth

import android.content.Context
import com.example.smartfeedandroid.data.remote.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthSessionState(
    val hasToken: Boolean = false,
    val user: AuthUser? = null
)

object AuthSession {
    private lateinit var tokenStore: SecureTokenStore
    private var token: String? = null
    private val mutableState = MutableStateFlow(AuthSessionState())

    val state: StateFlow<AuthSessionState> = mutableState.asStateFlow()

    fun initialize(context: Context) {
        if (::tokenStore.isInitialized) return
        tokenStore = SecureTokenStore(context.applicationContext)
        token = tokenStore.read()
        mutableState.value = AuthSessionState(hasToken = token != null)
    }

    fun accessToken(): String? {
        return token
    }

    fun authenticate(token: String, user: AuthUser) {
        tokenStore.save(token)
        this.token = token
        mutableState.value = AuthSessionState(hasToken = true, user = user)
    }

    fun updateUser(user: AuthUser) {
        mutableState.value = AuthSessionState(hasToken = true, user = user)
    }

    fun clear() {
        if (::tokenStore.isInitialized) tokenStore.clear()
        token = null
        mutableState.value = AuthSessionState()
    }
}
