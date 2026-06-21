package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.ui.state.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUploadSummaryStateTest {
    private val state = HomeUiState(
        url = "https://example.com/article",
        uploadSummaryText = "文章总结",
        uploadSummaryUrl = "https://example.com/article"
    )

    @Test
    fun sameUrlKeepsSummary() {
        val updated = state.withUploadUrl(" https://example.com/article ")

        assertEquals("文章总结", updated.uploadSummaryText)
        assertEquals("https://example.com/article", updated.uploadSummaryUrl)
    }

    @Test
    fun emptyUrlClearsSummary() {
        val updated = state.withUploadUrl("")

        assertEquals("", updated.uploadSummaryText)
        assertEquals("", updated.uploadSummaryUrl)
    }

    @Test
    fun differentUrlClearsSummary() {
        val updated = state.withUploadUrl("https://example.com/other")

        assertEquals("", updated.uploadSummaryText)
        assertEquals("", updated.uploadSummaryUrl)
    }
}
