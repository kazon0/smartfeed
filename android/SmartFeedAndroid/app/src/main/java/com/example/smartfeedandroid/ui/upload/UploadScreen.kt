package com.example.smartfeedandroid.ui.upload

import androidx.compose.foundation.layout.Arrangement
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
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.data.repository.UploadRepository
import kotlinx.coroutines.launch

@Composable
fun UploadScreen(
    modifier: Modifier = Modifier,
    repository: UploadRepository = UploadRepository()
) {
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<UploadResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
                        repository.upload(cleanUrl)
                            .onSuccess { response = it }
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
