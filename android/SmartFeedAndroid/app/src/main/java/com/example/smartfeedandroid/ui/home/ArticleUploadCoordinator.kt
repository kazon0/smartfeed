package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.remote.ArticleStatusResponse
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.data.repository.ArticleRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import com.example.smartfeedandroid.data.repository.UploadStreamStatus
import com.example.smartfeedandroid.ui.state.UploadProgress

class ArticleUploadCoordinator(
    private val articleRepository: ArticleRepository,
    private val uploadRepository: UploadRepository
) {
    suspend fun openOrUpload(
        url: String,
        onProgress: (UploadProgress) -> Unit,
        onStreamStatus: (UploadStreamStatus) -> Unit = {},
        onSummaryDelta: (String) -> Unit = {}
    ): ArticleUploadResult {
        onProgress(UploadProgress.CheckingStatus)

        val existingArticle = articleRepository.getArticleStatus(url)
            .getOrNull()
            ?.takeIf { it.exists && it.chunkCount > 0 }

        if (existingArticle != null) {
            onProgress(UploadProgress.OpeningSavedArticle)
            return ArticleUploadResult.ExistingArticle(existingArticle)
        }

        onProgress(UploadProgress.UploadingNewArticle)
        val streamingResult = uploadRepository.uploadStreaming(
            url = url,
            onStatus = onStreamStatus,
            onSummaryDelta = onSummaryDelta
        )
        val result = if (streamingResult.isSuccess) {
            streamingResult
        } else {
            onStreamStatus(UploadStreamStatus.Fallback)
            uploadRepository.upload(url)
        }

        return result.fold(
            onSuccess = { ArticleUploadResult.Uploaded(it) },
            onFailure = {
                ArticleUploadResult.Failed(it.message ?: "保存失败。")
            }
        )
    }
}

sealed interface ArticleUploadResult {
    data class ExistingArticle(val article: ArticleStatusResponse) : ArticleUploadResult
    data class Uploaded(val response: UploadResponse) : ArticleUploadResult
    data class Failed(val message: String) : ArticleUploadResult
}
