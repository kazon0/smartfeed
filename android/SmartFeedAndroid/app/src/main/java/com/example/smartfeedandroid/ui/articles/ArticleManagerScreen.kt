package com.example.smartfeedandroid.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.ui.common.AppBackground
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.common.SoftRed
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.smartfeedandroid.R

@Composable
fun ArticleManagerScreen(
    articles: List<SavedArticle>,
    isLoadingArticles: Boolean,
    deletingArticleUrl: String?,
    articlesErrorMessage: String?,
    onBack: () -> Unit,
    onOpenArticleChat: (SavedArticle) -> Unit,
    onDeleteArticle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedArticles = articles.groupBy { it.topic.ifBlank { "其他" } }
    val topicOrder = listOf("科技", "学习", "健康", "职业", "财经", "生活", "新闻", "其他")
    val orderedTopics = groupedArticles.keys.sortedWith(
        compareBy<String> { topic ->
            topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.size
        }.thenBy { it }
    )

    val tabs = listOf("全部") + orderedTopics

    var selectedTopic by remember(tabs) {
        mutableStateOf(tabs.first())
    }

    val visibleArticles = if (selectedTopic == "全部") {
        articles
    } else {
        groupedArticles[selectedTopic].orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.LightGray.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = stringResource(R.string.saved_articles),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 56.dp)
            )
        }

        articlesErrorMessage?.let { errorMessage ->
            ResultCard(title = stringResource(R.string.articles_error)) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (!isLoadingArticles && articles.isNotEmpty()) {
            TopicTabs(
                topics = tabs,
                selectedTopic = selectedTopic,
                onSelectTopic = { selectedTopic = it }
            )
        }

        when {
            isLoadingArticles -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
            }

            articles.isEmpty() -> {
                ResultCard(title = stringResource(R.string.no_saved_articles)) {
                    Text(
                        text = stringResource(R.string.upload_articles_first),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(
                        items = visibleArticles,
                        key = { article -> article.url }
                    ) { article ->
                        SwipeDeleteArticleRow(
                            article = article,
                            isDeleting = deletingArticleUrl == article.url,
                            onOpenArticleChat = onOpenArticleChat,
                            onDeleteArticle = onDeleteArticle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicTabs(
    topics: List<String>,
    selectedTopic: String,
    onSelectTopic: (String) -> Unit
) {
    val selectedIndex = topics.indexOf(selectedTopic).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = AppBackground,
        contentColor = SoftBlue,
        edgePadding = 0.dp
    ) {
        topics.forEach { topic ->
            Tab(
                selected = selectedTopic == topic,
                onClick = { onSelectTopic(topic) },
                text = {
                    Text(
                        text = topic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun SwipeDeleteArticleRow(
    article: SavedArticle,
    isDeleting: Boolean,
    onOpenArticleChat: (SavedArticle) -> Unit,
    onDeleteArticle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val actionWidth = 96.dp
    val actionWidthPx = with(density) {
        actionWidth.toPx()
    }

    val offsetX = remember(article.url) { Animatable(0f) }

    var cardHeightPx by remember(article.url) {
        mutableStateOf(0)
    }

    val cardHeightDp = with(density) {
        if (cardHeightPx > 0) cardHeightPx.toDp() else 88.dp
    }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            offsetX.animateTo(-actionWidthPx, tween(180))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // 右侧 Delete，跟卡片同高
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(cardHeightDp)
                .background(
                    color = SoftRed,
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        bottomStart = 0.dp,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = {
                    onDeleteArticle(article.url)
                },
                enabled = !isDeleting
            ) {
                Text(
                    text = if (isDeleting) stringResource(R.string.deleting) else stringResource(R.string.delete),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 前面的文章卡片
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(offsetX.value.roundToInt(), 0)
                }
                .onGloballyPositioned { coordinates ->
                    cardHeightPx = coordinates.size.height
                }
                .pointerInput(article.url, isDeleting) {
                    if (!isDeleting) {
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
                }
        ) {
            PureSavedArticleCard(
                article = article,
                isDeleting = isDeleting,
                onOpenArticleChat = onOpenArticleChat
            )
        }
    }
}

@Composable
private fun PureSavedArticleCard(
    article: SavedArticle,
    isDeleting: Boolean,
    onOpenArticleChat: (SavedArticle) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = article.url.isNotBlank() && !isDeleting) {
                onOpenArticleChat(article)
            },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeleting) Color(0xFFF5F5F5) else Color.White
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = article.title.ifBlank { article.url.ifBlank { stringResource(R.string.untitled_article) } },
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isDeleting) Color.Gray else Color.Unspecified
            )
            if (article.domain.isNotBlank()) {
                Text(
                    text = article.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${article.chunkCount} ${stringResource(R.string.chunk_count)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleManagerScreenPreview() {
    ArticleManagerScreen(
        articles = listOf(
            SavedArticle(
                url = "https://example.com/a",
                title = "程序员应该知道的十个基础算法",
                domain = "example.com",
                chunkCount = 8,
                topic = "科技"
            ),
            SavedArticle(
                url = "https://example.com/b",
                title = "住房公积金政策新闻",
                domain = "example.com",
                chunkCount = 5,
                topic = "新闻"
            )
        ),
        isLoadingArticles = false,
        deletingArticleUrl = null,
        articlesErrorMessage = null,
        onBack = {},
        onOpenArticleChat = {},
        onDeleteArticle = {}
    )
}
