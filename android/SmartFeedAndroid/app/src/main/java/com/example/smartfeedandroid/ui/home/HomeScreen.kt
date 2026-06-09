package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.ResultRow
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.common.SoftBlueLight
import androidx.compose.ui.res.painterResource
import com.example.smartfeedandroid.R
import androidx.compose.material3.Icon

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onStartGlobalConversation: () -> Unit,
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
                    text = "SmartFeed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onStartGlobalConversation,
                    modifier = Modifier
                        .size(48.dp) // 控制整个按钮的点击热区大小
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_new_chat),
                        contentDescription = "Start New Chat",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "Save articles and continue reading through conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste article link...") },
                shape = RoundedCornerShape(24.dp), // 调大圆角，更有搜索框的胶囊感
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = SoftBlue,
                    unfocusedBorderColor = Color.LightGray
                ),
                trailingIcon = {
                    if (uiState.isUploading) {
                        // 1. 如果正在保存，右侧直接展示一个精致的小转圈
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(24.dp), // 限制大小，使其刚好嵌入框内
                            color = SoftBlue,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        // 2. 正常状态下，是一个圆形的小图标按钮
                        IconButton(
                            onClick = onUpload,
                            enabled = uiState.url.isNotBlank(), // URL 有内容时才允许点击
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(36.dp)
                                .background(
                                    color = if (uiState.url.isNotBlank()) SoftBlue else Color.LightGray,
                                    shape = CircleShape // 变成正圆背景
                                )
                        ) {
                            // 这里可以用你想要的图标，比如一个向右的箭头 ➔ 或者保存图标
                            Text(
                                text = "➔",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
            uiState.uploadResponse?.let {
                UploadResult(response = it)
            }

            ConversationList(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                onSelectConversation = onSelectConversation
            )
        }
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (conversations.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    text = "Upload a page to create your first conversation.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            conversations.forEach { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isActive = conversation.id == activeConversationId,
                    onClick = { onSelectConversation(conversation.id) }
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                SoftBlueLight
            } else {
                Color.White
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = conversation.title,
                fontWeight = FontWeight.SemiBold
            )
            if (conversation.url.isNotBlank()) {
                Text(
                    text = conversation.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${conversation.messages.size} messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (conversation.status.isNotBlank() || conversation.storedChunks > 0) {
                Text(
                    text = "status: ${conversation.status.ifBlank { "N/A" }} · chunks: ${conversation.storedChunks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        onStartGlobalConversation = {}
    )
}
