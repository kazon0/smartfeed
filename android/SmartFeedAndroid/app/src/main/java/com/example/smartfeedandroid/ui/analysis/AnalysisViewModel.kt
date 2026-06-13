package com.example.smartfeedandroid.ui.analysis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartfeedandroid.data.remote.ArticlesResponse
import com.example.smartfeedandroid.data.remote.InsightsResponse
import com.example.smartfeedandroid.data.remote.StatsResponse
import com.example.smartfeedandroid.data.repository.ArticleRepository
import com.example.smartfeedandroid.data.repository.StatsRepository
import kotlinx.coroutines.launch

class AnalysisViewModel : ViewModel() {
    private val statsRepository = StatsRepository()
    private val articleRepository = ArticleRepository()

    var uiState by mutableStateOf(AnalysisUiState())
        private set

    fun refresh() {
        refreshStats()
        refreshArticles()
        refreshInsights()
    }

    private fun refreshStats() {
        uiState = uiState.copy(
            isLoadingStats = true,
            statsErrorMessage = null
        )

        viewModelScope.launch {
            statsRepository.getStats()
                .onSuccess { stats ->
                    uiState = uiState.copy(statsResponse = stats)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        statsErrorMessage = error.message ?: "加载分析数据失败。"
                    )
                }

            uiState = uiState.copy(isLoadingStats = false)
        }
    }

    private fun refreshInsights() {
        uiState = uiState.copy(insightsErrorMessage = null)

        viewModelScope.launch {
            statsRepository.getInsights()
                .onSuccess { insights ->
                    uiState = uiState.copy(insightsResponse = insights)
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        insightsErrorMessage = error.message ?: "加载智能总结失败。"
                    )
                }
        }
    }

    private fun refreshArticles() {
        uiState = uiState.copy(articlesErrorMessage = null)

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
        }
    }
}

data class AnalysisUiState(
    val statsResponse: StatsResponse? = null,
    val insightsResponse: InsightsResponse? = null,
    val articlesResponse: ArticlesResponse? = null,
    val isLoadingStats: Boolean = false,
    val statsErrorMessage: String? = null,
    val insightsErrorMessage: String? = null,
    val articlesErrorMessage: String? = null
)
