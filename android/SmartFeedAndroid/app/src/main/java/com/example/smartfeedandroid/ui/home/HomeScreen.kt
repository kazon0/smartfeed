package com.example.smartfeedandroid.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.ResultRow
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.common.SoftBlueLight
import com.example.smartfeedandroid.ui.common.SoftRed
import com.example.smartfeedandroid.ui.common.topicColor
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.state.HomeUiState
import com.example.smartfeedandroid.ui.state.UploadProgress
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
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

//            Text(
//                text = "Save articles and continue reading through conversation.",
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )

            OutlinedTextField(
                value = uiState.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.paste_or_share_article_link)) },
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
                            Text(
                                text = "➔",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
            UploadProgressText(uploadProgress = uiState.uploadProgress)
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
private fun ConversationList(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<ConversationFilter>(ConversationFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    val visibleConversations = conversations.filter { conversation ->
        selectedFilter.matches(conversation) && conversation.matchesSearch(searchQuery)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConversationListHeader(
            selectedFilter = selectedFilter,
            conversations = conversations,
            onSelectFilter = { selectedFilter = it }
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_conversations)) },
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = SoftBlue,
                unfocusedBorderColor = Color.LightGray
            )
        )
        if (conversations.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    text = stringResource(R.string.first_conversation_hint),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visibleConversations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matching_conversations),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            visibleConversations.forEach { conversation ->
                SwipeDeleteConversationRow(
                    conversation = conversation,
                    isActive = conversation.id == activeConversationId,
                    onClick = { onSelectConversation(conversation.id) },
                    onDeleteConversation = onDeleteConversation
                )
            }
        }
    }
}

@Composable
private fun ConversationListHeader(
    selectedFilter: ConversationFilter,
    conversations: List<Conversation>,
    onSelectFilter: (ConversationFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filters = conversationFilters(conversations)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.recent_conversations),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(text = selectedFilter.label())
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label()) },
                        onClick = {
                            onSelectFilter(filter)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeDeleteConversationRow(
    conversation: Conversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidth = 96.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(conversation.id) { Animatable(0f) }
    val rowHeight = 96.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(rowHeight)
                .background(SoftRed,
                    shape = RoundedCornerShape(bottomEnd = 5.dp, topEnd = 5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { onDeleteConversation(conversation.id) }
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(conversation.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount)
                                .coerceIn(-actionWidthPx, 0f)
                            scope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -actionWidthPx / 2) {
                                    -actionWidthPx
                                } else {
                                    0f
                                }
                                offsetX.animateTo(target, tween(180))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(180))
                            }
                        }
                    )
                }
        ) {
            ConversationItem(
                conversation = conversation,
                isActive = isActive,
                onClick = onClick,
                modifier = Modifier.height(rowHeight)
            )
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topic = conversationTopic(conversation)
    val topicIndex = topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.lastIndex

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                SoftBlueLight
            } else {
                Color.White
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp,end = 74.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.new_chat) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.summary.ifBlank {
                        if (conversationSourceUrl(conversation).isBlank()) {
                            stringResource(R.string.ask_knowledge_base)
                        } else {
                            stringResource(R.string.page_conversation)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${conversation.messages.size} 条消息 · ${conversation.storedChunks} 个片段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TopicBookmark(
                topic = topic,
                color = topicColor(topicIndex),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun TopicBookmark(
    topic: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = color,
                shape = RoundedCornerShape(bottomStart = 12.dp, topEnd = 5.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = topic,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private val topicOrder = listOf("科技", "学习", "健康", "职业", "财经", "生活", "新闻", "新聊天", "其他")

private sealed interface ConversationFilter {
    data object All : ConversationFilter
    data object NewChat : ConversationFilter
    data object Page : ConversationFilter
    data class Topic(val topic: String) : ConversationFilter

    fun matches(conversation: Conversation): Boolean {
        return when (this) {
            All -> true
            NewChat -> conversationSourceUrl(conversation).isBlank()
            Page -> conversationSourceUrl(conversation).isNotBlank()
            is Topic -> conversationTopic(conversation) == topic
        }
    }
}

@Composable
private fun ConversationFilter.label(): String {
    return when (this) {
        ConversationFilter.All -> stringResource(R.string.conversation_filter_all)
        ConversationFilter.NewChat -> stringResource(R.string.new_chat)
        ConversationFilter.Page -> stringResource(R.string.conversation_filter_page)
        is ConversationFilter.Topic -> topic
    }
}

private fun conversationFilters(conversations: List<Conversation>): List<ConversationFilter> {
    val topicFilters = conversations
        .map { conversationTopic(it) }
        .distinct()
        .filterNot { it == "新聊天" }
        .sortedWith(
            compareBy<String> { topic ->
                topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.size
            }.thenBy { it }
        )
        .map { ConversationFilter.Topic(it) }

    return listOf(
        ConversationFilter.All,
        ConversationFilter.NewChat,
        ConversationFilter.Page,
    ) + topicFilters
}

private fun Conversation.matchesSearch(query: String): Boolean {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) {
        return true
    }

    val messageText = messages
        .takeLast(6)
        .joinToString(" ") { message ->
            when (message) {
                is ChatMessage.User -> message.text
                is ChatMessage.Summary -> message.text
                is ChatMessage.Assistant -> message.response.answer.ifBlank {
                    message.response.message
                }
                is ChatMessage.Error -> message.text
            }
        }

    val searchable = listOf(
        title,
        summary,
        conversationSourceUrl(this),
        status,
        conversationTopic(this),
        messageText,
    ).joinToString(" ").lowercase()

    return cleanQuery in searchable
}

private fun conversationTopic(conversation: Conversation): String {
    val sourceUrl = conversationSourceUrl(conversation)
    if (sourceUrl.isBlank()) {
        return "新聊天"
    }

    conversation.topic.takeIf { it.isNotBlank() }?.let { return it }

    val text = listOf(conversation.title, conversation.summary, sourceUrl)
        .joinToString(" ")
        .lowercase()

    val scores = mapOf(
        "科技" to listOf("ai", "人工智能", "算法", "编程", "代码", "软件", "数据", "模型", "rag", "android", "kotlin", "python", "开发", "技术"),
        "学习" to listOf("学习", "课程", "考试", "高考", "教育", "笔记", "教程", "方法", "总结", "复习"),
        "健康" to listOf("健康", "医生", "疾病", "中毒", "症状", "治疗", "医院", "睡眠", "心理", "饮食"),
        "职业" to listOf("职业", "实习", "面试", "招聘", "简历", "工作", "岗位", "职场", "薪资"),
        "财经" to listOf("财经", "股票", "基金", "投资", "价格", "汇率", "美元", "经济", "市场", "公司"),
        "生活" to listOf("生活", "旅行", "美食", "家庭", "情感", "娱乐", "消费", "家长", "孩子"),
        "新闻" to listOf("新闻", "央视新闻", "新华社", "人民日报", "中新网", "通报", "政策", "社会", "近日", "报道称")
    )

    return scores
        .mapValues { (_, keywords) -> keywords.count { keyword -> keyword.lowercase() in text } }
        .maxByOrNull { it.value }
        ?.takeIf { it.value > 0 }
        ?.key
        ?: "其他"
}

private fun conversationSourceUrl(conversation: Conversation): String {
    return conversation.sourceUrl.ifBlank { conversation.url }
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
private fun UploadProgressText(uploadProgress: UploadProgress?) {
    val text = when (uploadProgress) {
        UploadProgress.CheckingStatus -> stringResource(R.string.upload_progress_checking)
        UploadProgress.OpeningSavedArticle -> stringResource(R.string.upload_progress_opening_saved)
        UploadProgress.UploadingNewArticle -> stringResource(R.string.upload_progress_uploading_new)
        null -> return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
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
