package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.data.remote.UploadResponse

@Composable
fun SmartFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    SmartFeedContent(
        uiState = viewModel.uiState,
        onUrlChange = viewModel::onUrlChange,
        onUpload = viewModel::upload,
        onQueryChange = viewModel::onQueryChange,
        onAsk = viewModel::ask,
        modifier = modifier
    )
}

@Composable
private fun SmartFeedContent(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SmartFeed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Save a web article and generate a summary.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Article URL") },
                placeholder = { Text("https://example.com/article") },
                singleLine = true
            )

            Button(
                onClick = onUpload,
                enabled = !uiState.isUploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isUploading) "Uploading..." else "Upload")
            }

            if (uiState.isUploading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage?.let {
                ResultCard(title = "Error") {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            uiState.uploadResponse?.let {
                UploadResult(response = it)
            }

            ChatSection(
                query = uiState.query,
                onQueryChange = onQueryChange,
                messages = uiState.messages,
                activeUrl = uiState.activeUrl,
                isAsking = uiState.isAsking,
                onAsk = onAsk
            )
        }
    }
}

@Composable
private fun UploadResult(response: UploadResponse) {
    ResultCard(title = "Upload Result") {
        ResultRow(label = "Status", value = response.status)
        ResultRow(label = "Stored chunks", value = response.storedChunks.toString())
        ResultRow(label = "Title", value = response.data?.title.orEmpty().ifBlank { "N/A" })
        ResultRow(label = "Parser", value = response.data?.metadata?.parser ?: "N/A")

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Summary", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = response.summary.ifBlank {
                response.error ?: "No summary returned."
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ChatSection(
    query: String,
    onQueryChange: (String) -> Unit,
    messages: List<ChatMessage>,
    activeUrl: String,
    isAsking: Boolean,
    onAsk: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Chat",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (activeUrl.isBlank()) {
                "Ask the global knowledge base."
            } else {
                "Asking with current page context."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (messages.isEmpty()) {
            ResultCard(title = "No messages yet") {
                Text("Ask a question after uploading a page, or ask the global knowledge base.")
            }
        } else {
            messages.forEach { message ->
                ChatBubble(message = message)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Question") },
            placeholder = { Text("这篇文章讲了什么？") },
            minLines = 2,
            maxLines = 4
        )

        Button(
            onClick = onAsk,
            enabled = !isAsking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAsking) "Asking..." else "Ask")
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        is ChatMessage.Assistant -> {
            AssistantMessage(response = message.response)
        }

        is ChatMessage.Error -> {
            ResultCard(title = "Chat Error") {
                Text(text = message.text, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AssistantMessage(response: ChatResponse) {
    ResultCard(title = "Assistant") {
        ResultRow(label = "Status", value = response.status.ifBlank { "N/A" })
        ResultRow(label = "Source type", value = response.sourceType.ifBlank { "N/A" })

        if (response.errorCode != null) {
            ResultRow(label = "Error code", value = response.errorCode)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = response.answer.ifBlank { response.message.ifBlank { "No answer returned." } })

        if (response.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Sources", fontWeight = FontWeight.SemiBold)
            response.sources.forEach { source ->
                SourceCard(source = source)
            }
        }
    }
}

@Composable
private fun SourceCard(source: ChatSource) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = source.displayTitle.ifBlank { source.title.ifBlank { "Untitled source" } },
                fontWeight = FontWeight.SemiBold
            )
            if (source.sectionTitle.isNotBlank()) {
                Text(
                    text = source.sectionTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (source.sourceSummary.isNotBlank()) {
                Text(text = source.sourceSummary)
            }
            if (source.url.isNotBlank()) {
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}
