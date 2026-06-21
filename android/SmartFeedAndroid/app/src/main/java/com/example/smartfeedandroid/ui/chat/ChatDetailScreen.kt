package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.res.painterResource
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.SoftBlue

import com.example.smartfeedandroid.ui.model.ChatMessage
@Composable
fun ChatDetailScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    messages: List<ChatMessage>,
    activeUrl: String,
    activeTitle: String,
    isAsking: Boolean,
    showStreamingResponse: Boolean = isAsking,
    streamStatusText: String,
    streamAnswerText: String,
    onAsk: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        ChatHeader(
            activeUrl = activeUrl,
            activeTitle = activeTitle,
            onBack = onBack
        )

        MessageList(
            messages = messages,
            isAsking = showStreamingResponse,
            streamStatusText = streamStatusText,
            streamAnswerText = streamAnswerText,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.LightGray.copy(0.1f))
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,


    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.back),
                tint = Color.Black,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activeTitle.ifBlank { stringResource(R.string.new_chat) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.Black
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_original_page)) },
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
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = Color.LightGray.copy(alpha = 0.6f)
    )
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    isAsking: Boolean,
    streamStatusText: String,
    streamAnswerText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isAsking) 1 else 0

    LaunchedEffect(messages.size, isAsking, streamAnswerText.length / 8) {
        if (itemCount > 0) {
            listState.scrollToItem(itemCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ask_question_to_start),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(messages) { message ->
                ChatBubble(message = message)
            }
            if (isAsking) {
                item {
                    ThinkingBubble(text = streamAnswerText.ifBlank { streamStatusText })
                }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        shape = RoundedCornerShape(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ask_anything)) },
                shape = RoundedCornerShape(18.dp),
                minLines = 1,
                maxLines = 3,
                singleLine = false,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,     // 聚焦纯白
                    unfocusedContainerColor = Color.White,   // 未聚焦纯白
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

// 动态判断当前按钮是否可用（既没在思考，输入框也有字）
            val isSendEnabled = !isAsking && query.isNotBlank()

            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onAsk()
                },
                enabled = isSendEnabled,
                modifier = Modifier
                    .padding(bottom = 4.dp, start = 8.dp, end = 4.dp)
                    .size(35.dp)
                    .background(
                        color = if (isSendEnabled) SoftBlue else Color(0xFFE0E0E0),
                        shape = CircleShape // 裁剪成正圆
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send_message),
                    contentDescription = stringResource(R.string.send),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
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
        showStreamingResponse = true,
        streamStatusText = "正在检索相关文章...",
        streamAnswerText = "",
        onAsk = {},
        onBack = {}
    )
}
