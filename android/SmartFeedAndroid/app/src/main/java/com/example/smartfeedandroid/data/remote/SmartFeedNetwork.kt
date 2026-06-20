package com.example.smartfeedandroid.data.remote

import android.content.Context
import com.example.smartfeedandroid.BuildConfig
import com.example.smartfeedandroid.data.auth.AuthSession
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object SmartFeedNetwork {
    fun initialize(context: Context) {
        AuthSession.initialize(context.applicationContext)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val token = AuthSession.accessToken()
            val request = if (token.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            val response = chain.proceed(request)
            val authRequest = original.url.encodedPath.startsWith("/auth/")
            if (response.code == 401 && !authRequest) {
                AuthSession.clear()
            }
            response
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    val api: SmartFeedApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.SMARTFEED_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SmartFeedApi::class.java)
    }
}
