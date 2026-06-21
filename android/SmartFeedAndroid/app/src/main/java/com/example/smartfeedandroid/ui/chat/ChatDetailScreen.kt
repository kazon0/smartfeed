package com.example.smartfeedandroid.ui.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.theme.KalamFontFamily

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
            .background(JournalPaper)
            .clickable(interactionSource = interactionSource, indication = null) {
                focusManager.clearFocus()
            }
    ) {
        ChatHeader(activeUrl = activeUrl, activeTitle = activeTitle, onBack = onBack)
        MessageList(
            messages = messages,
            isAsking = showStreamingResponse,
            streamStatusText = streamStatusText,
            streamAnswerText = streamAnswerText,
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
private fun ChatHeader(activeUrl: String, activeTitle: String, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var menuExpanded by remember { mutableStateOf(false) }
    val sourceLabel = remember(activeUrl) {
        runCatching { Uri.parse(activeUrl).host.orEmpty() }.getOrDefault("")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(JournalPaper.copy(alpha = 0.96f))
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                val segment = 8.dp.toPx()
                val gap = 6.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = JournalLine,
                        start = androidx.compose.ui.geometry.Offset(x, y),
                        end = androidx.compose.ui.geometry.Offset((x + segment).coerceAtMost(size.width), y),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += segment + gap
                }
            }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.back),
                tint = JournalInk,
                modifier = Modifier.size(27.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activeTitle.ifBlank { stringResource(R.string.new_chat) },
                style = MaterialTheme.typography.titleMedium,
                color = JournalInk,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sourceLabel.ifBlank { stringResource(R.string.app_name) },
                modifier = Modifier.padding(top = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = JournalInkLight,
                fontFamily = KalamFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = JournalInk
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = JournalPaper
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_original_page)) },
                    enabled = activeUrl.isNotBlank(),
                    onClick = {
                        menuExpanded = false
                        if (activeUrl.isNotBlank()) uriHandler.openUri(activeUrl)
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
    streamStatusText: String,
    streamAnswerText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isAsking) 1 else 0

    LaunchedEffect(messages.size, isAsking, streamAnswerText.length / 8) {
        if (itemCount > 0) listState.scrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ask_question_to_start),
                        color = JournalInkLight,
                        fontFamily = KalamFontFamily
                    )
                }
            }
        } else {
            items(messages) { ChatBubble(message = it) }
            if (isAsking) {
                item { ThinkingBubble(text = streamAnswerText.ifBlank { streamStatusText }) }
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
    val isSendEnabled = !isAsking && query.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), clip = false)
            .background(JournalPaper.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .border(1.dp, JournalLine, RoundedCornerShape(24.dp)),
            placeholder = {
                Text(stringResource(R.string.ask_anything), color = JournalInkLight.copy(alpha = 0.72f))
            },
            shape = RoundedCornerShape(24.dp),
            minLines = 1,
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = JournalInk,
                unfocusedTextColor = JournalInk,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
        IconButton(
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onAsk()
            },
            enabled = isSendEnabled,
            modifier = Modifier
                .size(48.dp)
                .rotate(if (isSendEnabled) -7f else 0f)
                .shadow(if (isSendEnabled) 4.dp else 0.dp, CircleShape, clip = false)
                .background(
                    if (isSendEnabled) JournalTerra else JournalLine,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.send),
                tint = if (isSendEnabled) JournalPaper else JournalInkLight,
                modifier = Modifier.size(21.dp)
            )
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
            ChatMessage.Summary("## 核心思路\n使用 **滑动窗口** 解决问题，时间复杂度为 `O(m+n)`。"),
            ChatMessage.User("这篇文章讲了什么")
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
