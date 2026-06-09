package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.ui.common.AppBackground
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.common.SoftBlueLight
import com.example.smartfeedandroid.ui.common.SoftGreen
import com.example.smartfeedandroid.ui.home.ChatMessage

@Composable
fun ChatDetailScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    messages: List<ChatMessage>,
    activeUrl: String,
    activeTitle: String,
    isAsking: Boolean,
    onAsk: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ChatHeader(
            activeUrl = activeUrl,
            activeTitle = activeTitle,
            onBack = onBack
        )

        MessageList(
            messages = messages,
            isAsking = isAsking,
            modifier = Modifier.weight(1f)
        )

        ChatInputBar(
            query = query,
            onQueryChange = onQueryChange,
            isAsking = isAsking,
            onAsk = onAsk
        )
    }
}

@Composable
private fun ChatHeader(
    activeUrl: String,
    activeTitle: String,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activeTitle.ifBlank { "Global chat" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            TextButton(onClick = { menuExpanded = true }) {
                Text("⋯", style = MaterialTheme.typography.headlineSmall)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open original page") },
                    enabled = activeUrl.isNotBlank(),
                    onClick = {
                        menuExpanded = false
                        if (activeUrl.isNotBlank()) {
                            uriHandler.openUri(activeUrl)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isAsking: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ask a question to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            messages.forEach { message ->
                ChatBubble(message = message)
            }
            if (isAsking) {
                ThinkingBubble()
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isAsking: Boolean,
    onAsk: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything") },
                shape = RoundedCornerShape(18.dp),
                minLines = 1,
                maxLines = 3
            )

            Button(
                onClick = onAsk,
                enabled = !isAsking && query.isNotBlank(),
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> UserBubble(text = message.text)
        is ChatMessage.Summary -> AssistantBubble(title = "Summary", text = message.text)
        is ChatMessage.Assistant -> AssistantMessage(response = message.response)
        is ChatMessage.Error -> {
            ResultCard(title = "Chat Error") {
                Text(text = message.text, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AssistantMessage(response: ChatResponse) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistantBubble(
            title = "SmartFeed",
            text = response.answer.ifBlank {
                response.message.ifBlank { "No answer returned." }
            },
            footer = response.sourceType.ifBlank { response.status.ifBlank { "N/A" } }
        )

        if (response.sources.isNotEmpty()) {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            response.sources.forEach { source ->
                SourceCard(source = source)
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    AssistantBubble(
        title = "SmartFeed",
        text = "正在思考中..."
    )
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SoftBlueLight)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp)
                )
            }
            BubbleTriangle(color = SoftBlueLight, pointsRight = true)
            Avatar(label = "我", color = SoftBlue)
        }
    }
}

@Composable
private fun AssistantBubble(
    title: String,
    text: String,
    footer: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Avatar(label = "S", color = SoftGreen)
        BubbleTriangle(color = Color.White, pointsRight = false)
        Card(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(text = text)
                if (footer.isNotBlank()) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(color = color, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BubbleTriangle(color: Color, pointsRight: Boolean) {
    Canvas(
        modifier = Modifier
            .padding(top = 12.dp)
            .size(width = 8.dp, height = 12.dp)
    ) {
        val path = Path().apply {
            if (pointsRight) {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            } else {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            }
            close()
        }
        drawPath(path = path, color = color)
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

@Preview(showBackground = true)
@Composable
private fun ChatDetailScreenPreview() {
    ChatDetailScreen(
        query = "十种算法有哪些",
        onQueryChange = {},
        messages = listOf(
            ChatMessage.User("这篇文章讲了什么"),
            ChatMessage.Summary("这是一篇文章摘要。"),
        ),
        activeUrl = "https://example.com/article",
        activeTitle = "程序员应该知道的十个基础算法",
        isAsking = true,
        onAsk = {},
        onBack = {}
    )
}
