package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.auth.AuthSession
import com.example.smartfeedandroid.data.remote.AuthUser
import com.example.smartfeedandroid.data.remote.LoginRequest
import com.example.smartfeedandroid.data.remote.RegisterRequest
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork

class AuthRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val response = api.login(LoginRequest(email.trim(), password))
        AuthSession.authenticate(response.accessToken, response.user)
        response.user
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthUser> = runCatching {
        val response = api.register(
            RegisterRequest(email.trim(), password, displayName.trim())
        )
        AuthSession.authenticate(response.accessToken, response.user)
        response.user
    }

    suspend fun currentUser(): Result<AuthUser> = runCatching {
        api.me().also(AuthSession::updateUser)
    }

    fun logout() {
        AuthSession.clear()
    }
}
