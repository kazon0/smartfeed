package com.example.smartfeedandroid.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPink
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.common.SoftRed
import com.example.smartfeedandroid.ui.common.topicColor
import com.example.smartfeedandroid.ui.model.Conversation
import com.example.smartfeedandroid.ui.theme.KalamFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ConversationList(
    conversations: List<Conversation>,
    activeConversationId: String?,
    searchQuery: String,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<ConversationFilter>(ConversationFilter.All) }
    val visibleConversations = conversations.filter { conversation ->
        selectedFilter.matches(conversation) && conversation.matchesSearch(searchQuery)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConversationListHeader(
            selectedFilter = selectedFilter,
            conversations = conversations,
            onSelectFilter = { selectedFilter = it }
        )
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.first_conversation_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                        .border(1.dp, JournalLine, RoundedCornerShape(24.dp))
                        .padding(18.dp),
                    color = JournalInkLight
                )
            }
        } else if (visibleConversations.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matching_conversations),
                color = JournalInkLight,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            ConversationCardList(
                conversations = visibleConversations,
                activeConversationId = activeConversationId,
                onSelectConversation = onSelectConversation,
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}

@Composable
private fun ConversationListHeader(
    selectedFilter: ConversationFilter,
    conversations: List<Conversation>,
    onSelectFilter: (ConversationFilter) -> Unit
) {
    val filters = conversationFilters(conversations)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = JournalBlue,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "近期收录",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                color = JournalInk,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${selectedFilter.label()} >",
                style = MaterialTheme.typography.titleSmall,
                color = JournalInkLight,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filters.forEach { filter ->
                val selected = selectedFilter == filter
                TextButton(
                    onClick = { onSelectFilter(filter) },
                    modifier = Modifier
                        .height(48.dp)
                        .background(
                            color = if (selected) JournalInk else Color.White.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) JournalInk else JournalLine,
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterIcon(
                            filter = filter,
                            selected = selected
                        )
                        Text(
                            text = filter.label(),
                            color = if (selected) JournalPaper else JournalInkLight,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCardList(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        conversations.take(8).forEachIndexed { index, conversation ->
            SwipeDeleteConversationRow(
                conversation = conversation,
                isActive = conversation.id == activeConversationId,
                cardIndex = index,
                onClick = { onSelectConversation(conversation.id) },
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}

@Composable
private fun SwipeDeleteConversationRow(
    conversation: Conversation,
    isActive: Boolean,
    cardIndex: Int,
    onClick: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidth = 96.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(conversation.id) { Animatable(0f) }
    val rowHeight = 150.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(rowHeight)
                .background(
                    SoftRed,
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(
                onClick = { onDeleteConversation(conversation.id) }
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = JournalInk,
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
                cardIndex = cardIndex,
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
    cardIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topic = conversationTopic(conversation)
    val topicIndex = topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.lastIndex
    val cardColors = listOf(
        Color(0xFFE5EFE9),
        Color(0xFFE4EDF4),
        Color(0xFFF1E3E8),
        Color(0xFFF7EED3),
        Color(0xFFEDE4DE)
    )
    val blobColors = listOf(
        JournalGreen,
        JournalBlue,
        JournalPink,
        JournalYellow,
        JournalTerra
    )
    val containerColor = if (isActive) {
        JournalYellow
    } else {
        cardColors[cardIndex % cardColors.size]
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(7.dp, RoundedCornerShape(26.dp), clip = false)
                .clip(RoundedCornerShape(24.dp))
                .background(containerColor)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(24.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(112.dp)
                    .background(
                        blobColors[cardIndex % blobColors.size].copy(alpha = 0.28f),
                        RoundedCornerShape(44.dp)
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopicBookmark(
                        topic = topic,
                        color = topicColor(topicIndex),
                        compact = false
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = relativeTime(conversation.updatedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = JournalInkLight,
                        fontFamily = KalamFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.new_chat) },
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversationSummaryText(conversation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JournalInkLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConversationMetric(
                        text = "${conversation.messages.size} 条消息",
                        tint = JournalTerra,
                        icon = Icons.Filled.ChatBubbleOutline
                    )
                    ConversationMetric(
                        text = "${conversation.storedChunks} 片段",
                        tint = JournalBlue,
                        icon = Icons.Filled.Layers
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-24).dp, y = (-7).dp)
                .rotate(if (cardIndex % 2 == 0) -1.5f else 1.5f)
                .size(width = 58.dp, height = 18.dp)
                .shadow(1.dp, RoundedCornerShape(2.dp), clip = false)
                .background(Color.White.copy(alpha = 0.68f), RoundedCornerShape(2.dp))
                .border(
                    width = 1.dp,
                    color = JournalLine.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

private fun conversationSummaryText(conversation: Conversation): String {
    return conversation.summary.ifBlank {
        if (conversationSourceUrl(conversation).isBlank()) {
            "全局知识库对话"
        } else {
            "当前网页对话"
        }
    }
}

@Composable
private fun ConversationMetric(
    text: String,
    tint: Color,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = JournalInk,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilterIcon(
    filter: ConversationFilter,
    selected: Boolean
) {
    val label = filter.label()
    val icon = when {
        label.contains("科技") -> Icons.Filled.Lightbulb
        label.contains("健康") -> Icons.Filled.LocalFlorist
        label.contains("财经") -> Icons.Filled.Paid
        label.contains("学习") || label.contains("算法") -> Icons.Filled.School
        label.contains("新闻") -> Icons.Filled.Science
        else -> Icons.Filled.FolderOpen
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) JournalPaper else topicColor(label.length),
        modifier = Modifier.size(19.dp)
    )
}

private fun relativeTime(updatedAtMillis: Long): String {
    if (updatedAtMillis <= 0L) {
        return "刚刚"
    }
    val diffMillis = (System.currentTimeMillis() - updatedAtMillis).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diffMillis < minute -> "刚刚"
        diffMillis < hour -> "${diffMillis / minute} 分钟前"
        diffMillis < day -> "${diffMillis / hour} 小时前"
        diffMillis < 2 * day -> "昨天"
        else -> "${diffMillis / day} 天前"
    }
}

@Composable
private fun TopicBookmark(
    topic: String,
    color: Color,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.60f),
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.42f),
                shape = RoundedCornerShape(50)
            )
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 5.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "# $topic",
            color = JournalInk,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
