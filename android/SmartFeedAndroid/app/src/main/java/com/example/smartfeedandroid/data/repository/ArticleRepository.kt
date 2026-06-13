package com.example.smartfeedandroid.data.repository

import com.example.smartfeedandroid.data.remote.ArticlesResponse
import com.example.smartfeedandroid.data.remote.ArticleStatusResponse
import com.example.smartfeedandroid.data.remote.DeleteArticleRequest
import com.example.smartfeedandroid.data.remote.DeleteArticleResponse
import com.example.smartfeedandroid.data.remote.SmartFeedApi
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork

class ArticleRepository(
    private val api: SmartFeedApi = SmartFeedNetwork.api
) {
    suspend fun getArticles(): Result<ArticlesResponse> {
        return runCatching {
            api.articles()
        }
    }

    suspend fun getArticleStatus(url: String): Result<ArticleStatusResponse> {
        return runCatching {
            api.articleStatus(url)
        }
    }

    suspend fun deleteArticle(url: String): Result<DeleteArticleResponse> {
        return runCatching {
            api.deleteArticle(DeleteArticleRequest(url = url))
        }
    }
}
