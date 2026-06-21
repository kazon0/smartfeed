package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.ResultRow
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
import com.example.smartfeedandroid.ui.state.HomeUiState
import com.example.smartfeedandroid.ui.state.UploadProgress

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.paste_or_share_article_link)) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = SoftBlue,
                    unfocusedBorderColor = Color.LightGray
                ),
                trailingIcon = {
                    if (uiState.isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(24.dp),
                            color = SoftBlue,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(
                            onClick = onUpload,
                            enabled = uiState.url.isNotBlank(),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(36.dp)
                                .background(
                                    color = if (uiState.url.isNotBlank()) SoftBlue else Color.LightGray,
                                    shape = CircleShape
                                )
                        ) {
                            Text(
                                text = "➔",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
            UploadProgressText(
                uploadProgress = uiState.uploadProgress,
                uploadStatusText = uiState.uploadStatusText
            )
            if (uiState.uploadSummaryText.isNotBlank()) {
                UploadStreamingSummary(summary = uiState.uploadSummaryText)
            }
            uiState.uploadResponse?.let {
                UploadResult(response = it)
            }

            ConversationList(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                onSelectConversation = onSelectConversation,
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}

@Composable
private fun UploadResult(response: UploadResponse) {
    ResultCard(title = stringResource(R.string.save_result)) {
        ResultRow(label = stringResource(R.string.status), value = response.status)
        ResultRow(label = stringResource(R.string.chunk_count), value = response.storedChunks.toString())
        ResultRow(label = stringResource(R.string.title), value = response.data?.title.orEmpty().ifBlank { "N/A" })
        ResultRow(label = stringResource(R.string.parser), value = response.data?.metadata?.parser ?: "N/A")

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.summary), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = response.summary.ifBlank {
                response.error ?: stringResource(R.string.no_summary)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UploadProgressText(
    uploadProgress: UploadProgress?,
    uploadStatusText: String
) {
    val fallbackText = when (uploadProgress) {
        UploadProgress.CheckingStatus -> stringResource(R.string.upload_progress_checking)
        UploadProgress.OpeningSavedArticle -> stringResource(R.string.upload_progress_opening_saved)
        UploadProgress.UploadingNewArticle -> stringResource(R.string.upload_progress_uploading_new)
        null -> ""
    }
    val text = uploadStatusText.ifBlank { fallbackText }
    if (text.isBlank()) {
        return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun UploadStreamingSummary(summary: String) {
    ResultCard(title = stringResource(R.string.summary)) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        uiState = HomeUiState(
            url = "https://example.com/article",
            conversations = listOf(
                Conversation(
                    id = "1",
                    title = "程序员应该知道的十个基础算法",
                    url = "https://example.com/article",
                    status = "received",
                    storedChunks = 8,
                    updatedAtMillis = 0L,
                    messages = listOf(ChatMessage.Summary("这是一篇文章摘要。"))
                )
            )
        ),
        onUrlChange = {},
        onUpload = {},
        onSelectConversation = {},
        onDeleteConversation = {}
    )
}
