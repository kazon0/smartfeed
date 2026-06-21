package com.example.smartfeedandroid.ui.articles

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPink
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.SoftRed
import com.example.smartfeedandroid.ui.theme.KalamFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ArticleManagerScreen(
    articles: List<SavedArticle>,
    isLoadingArticles: Boolean,
    deletingArticleUrl: String?,
    articlesErrorMessage: String?,
    onBack: (() -> Unit)? = null,
    onOpenArticleChat: (SavedArticle) -> Unit,
    onDeleteArticle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val topics = articleTopics(articles)
    var selectedTopic by remember(topics) { mutableStateOf(topics.first()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var articleSort by rememberSaveable { mutableStateOf(ArticleSort.Default) }
    val filteredArticles = visibleArticles(articles, selectedTopic, searchQuery, articleSort)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JournalPaper)
    ) {
        ArticleJournalHeader(articleCount = articles.size, onBack = onBack)

        if (!isLoadingArticles && articles.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArticleSearchAndSort(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    articleSort = articleSort,
                    onSelectSort = { articleSort = it }
                )
                TopicChips(
                    topics = topics,
                    selectedTopic = selectedTopic,
                    onSelectTopic = { selectedTopic = it }
                )
            }
        }

        articlesErrorMessage?.let { errorMessage ->
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                ResultCard(title = stringResource(R.string.articles_error)) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        when {
            isLoadingArticles -> LoadingArticles()
            articles.isEmpty() -> EmptyArticles()
            filteredArticles.isEmpty() -> NoMatchingArticles()
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 10.dp, bottom = 132.dp)
            ) {
                itemsIndexed(
                    items = filteredArticles,
                    key = { _, article -> article.url }
                ) { index, article ->
                    SwipeDeleteArticleRow(
                        article = article,
                        cardIndex = index,
                        isDeleting = deletingArticleUrl == article.url,
                        onOpenArticleChat = onOpenArticleChat,
                        onDeleteArticle = onDeleteArticle
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleJournalHeader(
    articleCount: Int,
    onBack: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = JournalInk
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = JournalBlue,
                    modifier = Modifier.size(29.dp)
                )
                Text(
                    text = "知识库",
                    style = MaterialTheme.typography.headlineMedium,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "精心收集的每一寸灵感与思考。",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = JournalInkLight,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = articleCount.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = JournalInk,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "ARTICLES",
                style = MaterialTheme.typography.labelSmall,
                color = JournalInkLight,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun TopicChips(
    topics: List<String>,
    selectedTopic: String,
    onSelectTopic: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        topics.forEach { topic ->
            val selected = selectedTopic == topic
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .shadow(if (selected) 3.dp else 1.dp, RoundedCornerShape(20.dp), clip = false)
                    .background(
                        if (selected) JournalInk else Color.White,
                        RoundedCornerShape(20.dp)
                    )
                    .border(
                        1.dp,
                        if (selected) JournalInk else JournalLine,
                        RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectTopic(topic) }
                    .padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = topicIcon(topic),
                    contentDescription = null,
                    tint = if (selected) JournalPaper else topicTint(topic),
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = topic,
                    color = if (selected) JournalPaper else JournalInkLight,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun topicIcon(topic: String): ImageVector = when (topic) {
    "科技" -> Icons.Filled.Memory
    "学习" -> Icons.AutoMirrored.Filled.MenuBook
    "健康" -> Icons.Filled.LocalFlorist
    "职业" -> Icons.Filled.HomeWork
    "财经" -> Icons.Filled.Paid
    "新闻" -> Icons.Filled.Newspaper
    else -> Icons.Filled.Category
}

private fun topicTint(topic: String): Color = when (topic) {
    "科技" -> JournalBlue
    "学习" -> JournalTerra
    "健康" -> JournalGreen
    "财经" -> JournalYellow
    else -> JournalPink
}

@Composable
private fun SwipeDeleteArticleRow(
    article: SavedArticle,
    cardIndex: Int,
    isDeleting: Boolean,
    onOpenArticleChat: (SavedArticle) -> Unit,
    onDeleteArticle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidth = 96.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(article.url) { Animatable(0f) }
    var cardHeightPx by remember(article.url) { mutableStateOf(0) }
    val cardHeightDp = with(density) { if (cardHeightPx > 0) cardHeightPx.toDp() else 144.dp }

    LaunchedEffect(isDeleting) {
        if (isDeleting) offsetX.animateTo(-actionWidthPx, tween(180))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .height(cardHeightDp)
                .background(SoftRed, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = { onDeleteArticle(article.url) },
                enabled = !isDeleting
            ) {
                Text(
                    text = if (isDeleting) stringResource(R.string.deleting) else stringResource(R.string.delete),
                    color = JournalInk,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .onGloballyPositioned { cardHeightPx = it.size.height }
                .pointerInput(article.url, isDeleting) {
                    if (!isDeleting) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f))
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    offsetX.animateTo(
                                        if (offsetX.value < -actionWidthPx / 2) -actionWidthPx else 0f,
                                        tween(180)
                                    )
                                }
                            },
                            onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(180)) } }
                        )
                    }
                }
        ) {
            SavedArticleJournalCard(
                article = article,
                cardIndex = cardIndex,
                isDeleting = isDeleting,
                onOpenArticleChat = onOpenArticleChat
            )
        }
    }
}

@Composable
private fun SavedArticleJournalCard(
    article: SavedArticle,
    cardIndex: Int,
    isDeleting: Boolean,
    onOpenArticleChat: (SavedArticle) -> Unit
) {
    val cardColors = listOf(
        Color.White,
        Color(0xFFE7EFF5),
        Color(0xFFF3E8EC),
        Color(0xFFF8F0D8),
        Color(0xFFE8F0EB)
    )
    val cardColor = if (isDeleting) Color(0xFFF3F0EC) else cardColors[cardIndex % cardColors.size]
    val shape = RoundedCornerShape(22.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 144.dp)
                .shadow(5.dp, shape, clip = false)
                .clip(shape)
                .background(cardColor)
                .drawBehind {
                    if (cardIndex % cardColors.size == 0) {
                        val spacing = 18.dp.toPx()
                        var x = spacing
                        while (x < size.width) {
                            drawLine(JournalInkLight.copy(alpha = 0.08f), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                            x += spacing
                        }
                        var y = spacing
                        while (y < size.height) {
                            drawLine(JournalInkLight.copy(alpha = 0.08f), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                            y += spacing
                        }
                    }
                }
                .border(1.dp, Color.White.copy(alpha = 0.72f), shape)
                .clickable(enabled = article.url.isNotBlank() && !isDeleting) {
                    onOpenArticleChat(article)
                }
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (cardIndex % cardColors.size != 0) {
                    Text(
                        text = "# ${articleTopic(article)}",
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                            .border(1.dp, JournalLine.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = JournalInk,
                        fontFamily = KalamFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = article.title.ifBlank { article.url.ifBlank { stringResource(R.string.untitled_article) } },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDeleting) JournalInkLight else JournalInk,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArticleMetaPill(
                        icon = Icons.Filled.Link,
                        text = article.domain.ifBlank { article.url },
                        tint = JournalTerra,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ArticleMetaPill(
                        icon = Icons.Filled.Edit,
                        text = "${article.chunkCount} 碎片",
                        tint = topicTint(articleTopic(article))
                    )
                }
            }
        }

        when (cardIndex % 3) {
            0 -> Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-7).dp)
                    .rotate(-1.5f)
                    .size(width = 64.dp, height = 18.dp)
                    .background(JournalYellow.copy(alpha = 0.76f), RoundedCornerShape(2.dp))
                    .border(1.dp, JournalYellow, RoundedCornerShape(2.dp))
            )
            1 -> Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = null,
                tint = JournalInkLight.copy(alpha = 0.42f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = (-9).dp)
                    .rotate(14f)
                    .size(31.dp)
            )
            else -> Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = JournalTerra,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = 8.dp)
                    .size(15.dp)
            )
        }
    }
}

@Composable
private fun ArticleMetaPill(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.68f), RoundedCornerShape(9.dp))
            .border(1.dp, JournalLine.copy(alpha = 0.58f), RoundedCornerShape(9.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = JournalInkLight,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingArticles() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = JournalTerra)
    }
}

@Composable
private fun EmptyArticles() {
    Box(modifier = Modifier.padding(20.dp)) {
        ResultCard(title = stringResource(R.string.no_saved_articles)) {
            Text(text = stringResource(R.string.upload_articles_first), color = JournalInkLight)
        }
    }
}

@Composable
private fun NoMatchingArticles() {
    Box(modifier = Modifier.padding(20.dp)) {
        ResultCard(title = stringResource(R.string.no_matching_articles)) {
            Text(text = stringResource(R.string.adjust_article_filters), color = JournalInkLight)
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
