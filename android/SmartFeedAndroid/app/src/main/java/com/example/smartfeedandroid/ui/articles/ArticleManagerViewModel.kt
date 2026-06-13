package com.example.smartfeedandroid.ui.articles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.remote.ArticlesResponse
import com.example.smartfeedandroid.data.repository.ArticleRepository
import kotlinx.coroutines.launch

class ArticleManagerViewModel : ViewModel() {
    private val articleRepository = ArticleRepository()

    var uiState by mutableStateOf(ArticleManagerUiState())
        private set

    fun refreshArticles() {
        uiState = uiState.copy(
            isLoadingArticles = true,
            articlesErrorMessage = null
        )

        viewModelScope.launch {
            articleRepository.getArticles()
                .onSuccess { articles ->
                    uiState = uiState.copy(articlesResponse = articles)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        articlesErrorMessage = error.message ?: "加载文章列表失败。"
                    )
                }

            uiState = uiState.copy(isLoadingArticles = false)
        }
    }

    fun deleteArticle(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return
        }

        uiState = uiState.copy(
            deletingArticleUrl = cleanUrl,
            articlesErrorMessage = null
        )

        viewModelScope.launch {
            articleRepository.deleteArticle(cleanUrl)
                .onSuccess {
                    refreshArticles()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        articlesErrorMessage = error.message ?: "删除文章失败。"
                    )
                }

            uiState = uiState.copy(deletingArticleUrl = null)
        }
    }
}

data class ArticleManagerUiState(
    val articlesResponse: ArticlesResponse? = null,
    val isLoadingArticles: Boolean = false,
    val deletingArticleUrl: String? = null,
    val articlesErrorMessage: String? = null
)
