package com.example.smartfeedandroid.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.ui.common.AppBackground
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.common.SoftRed
import com.example.smartfeedandroid.ui.common.SoftRedLight

@Composable
fun ArticleManagerScreen(
    articles: List<SavedArticle>,
    isLoadingArticles: Boolean,
    deletingArticleUrl: String?,
    articlesErrorMessage: String?,
    onBack: () -> Unit,
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
    var selectedTopic by remember(tabs) { mutableStateOf(tabs.first()) }
    val visibleArticles = if (selectedTopic == "全部") {
        articles
    } else {
        groupedArticles[selectedTopic].orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                text = "Saved articles",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "Tap an article to open it. Swipe left to reveal Delete.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        articlesErrorMessage?.let {
            ResultCard(title = "Articles Error") {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }

        if (isLoadingArticles) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
            }
        } else if (articles.isEmpty()) {
            ResultCard(title = "No saved articles") {
                Text(
                    text = "Upload or share articles first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            TopicTabs(
                topics = tabs,
                selectedTopic = selectedTopic,
                onSelectTopic = { selectedTopic = it }
            )

            visibleArticles.forEach { article ->
                SwipeDeleteArticleRow(
                    article = article,
                    isDeleting = deletingArticleUrl == article.url,
                    onDeleteArticle = onDeleteArticle
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteArticleRow(
    article: SavedArticle,
    isDeleting: Boolean,
    onDeleteArticle: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.EndToStart
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SoftRedLight, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = { onDeleteArticle(article.url) },
                    enabled = !isDeleting
                ) {
                    Text(
                        text = if (isDeleting) "Deleting..." else "Delete",
                        color = SoftRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        SavedArticleCard(article = article, isDeleting = isDeleting)
    }
}

@Composable
private fun SavedArticleCard(
    article: SavedArticle,
    isDeleting: Boolean
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = article.url.isNotBlank() && !isDeleting) {
                uriHandler.openUri(article.url)
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = article.title.ifBlank { article.url.ifBlank { "Untitled article" } },
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (article.domain.isNotBlank()) {
                Text(
                    text = article.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${article.chunkCount} chunks",
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
        onDeleteArticle = {}
    )
}
