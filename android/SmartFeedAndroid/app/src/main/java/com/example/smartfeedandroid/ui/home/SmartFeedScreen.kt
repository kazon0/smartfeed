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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.data.repository.ChatRepository
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

@Composable
fun SmartFeedScreen(
    modifier: Modifier = Modifier,
    uploadRepository: UploadRepository = UploadRepository(),
    chatRepository: ChatRepository = ChatRepository()
) {
    var url by remember { mutableStateOf("") }
    var activeUrl by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isAsking by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<UploadResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val scope = rememberCoroutineScope()

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
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Article URL") },
                placeholder = { Text("https://example.com/article") },
                singleLine = true
            )

            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    if (cleanUrl.isEmpty()) {
                        errorMessage = "Please enter a URL."
                        response = null
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    response = null

                    scope.launch {
                        uploadRepository.upload(cleanUrl)
                            .onSuccess {
                                response = it
                                activeUrl = it.data?.url?.takeIf { parsedUrl -> parsedUrl.isNotBlank() }
                                    ?: cleanUrl
                                messages = emptyList()
                            }
                            .onFailure { errorMessage = it.message ?: "Upload failed." }
                        isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Uploading..." else "Upload")
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage?.let {
                ResultCard(title = "Error") {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            response?.let {
                UploadResult(response = it)
            }

            ChatSection(
                query = query,
                onQueryChange = { query = it },
                messages = messages,
                activeUrl = activeUrl,
                isAsking = isAsking,
                onAsk = {
                    val cleanQuery = query.trim()
                    if (cleanQuery.isEmpty()) {
                        errorMessage = "Please enter a question."
                        return@ChatSection
                    }

                    messages = messages + ChatMessage.User(cleanQuery)
                    query = ""
                    isAsking = true
                    errorMessage = null

                    scope.launch {
                        chatRepository.ask(cleanQuery, activeUrl)
                            .onSuccess {
                                messages = messages + ChatMessage.Assistant(it)
                            }
                            .onFailure {
                                messages = messages + ChatMessage.Error(
                                    it.message ?: "Chat request failed."
                                )
                            }
                        isAsking = false
                    }
                }
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

private sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Assistant(val response: ChatResponse) : ChatMessage
    data class Error(val text: String) : ChatMessage
}
