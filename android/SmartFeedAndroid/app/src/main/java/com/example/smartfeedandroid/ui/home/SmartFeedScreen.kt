package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.data.remote.StatsResponse
import com.example.smartfeedandroid.data.remote.TopicDistribution
import com.example.smartfeedandroid.data.remote.UploadResponse
import kotlin.math.min

@Composable
fun SmartFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    SmartFeedContent(
        uiState = viewModel.uiState,
        onUrlChange = viewModel::onUrlChange,
        onUpload = viewModel::upload,
        onQueryChange = viewModel::onQueryChange,
        onAsk = viewModel::ask,
        onSelectTab = viewModel::selectTab,
        onSelectConversation = viewModel::selectConversation,
        onStartGlobalConversation = viewModel::startGlobalConversation,
        onBackToConversations = viewModel::showConversationList,
        onOpenArticleManager = viewModel::openArticleManager,
        onCloseArticleManager = viewModel::closeArticleManager,
        onDeleteArticle = viewModel::deleteArticle,
        onDismissError = viewModel::clearError,
        modifier = modifier
    )
}

@Composable
private fun SmartFeedContent(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onSelectConversation: (String) -> Unit,
    onStartGlobalConversation: () -> Unit,
    onBackToConversations: () -> Unit,
    onOpenArticleManager: () -> Unit,
    onCloseArticleManager: () -> Unit,
    onDeleteArticle: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeConversation = uiState.conversations
        .firstOrNull { it.id == uiState.activeConversationId }

    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (
                    !(uiState.selectedTab == AppTab.Home && uiState.isChatOpen) &&
                    !(uiState.selectedTab == AppTab.Analysis && uiState.isArticleManagerOpen)
                ) {
                    AppBottomBar(
                        selectedTab = uiState.selectedTab,
                        onSelectTab = onSelectTab
                    )
                }
            }
        ) { innerPadding ->
            uiState.errorMessage?.let { errorMessage ->
                AlertDialog(
                    onDismissRequest = onDismissError,
                    title = { Text("Something went wrong") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(onClick = onDismissError) {
                            Text("OK")
                        }
                    }
                )
            }

            when (uiState.selectedTab) {
                AppTab.Home -> {
                    if (uiState.isChatOpen) {
                        ChatDetailScreen(
                            query = uiState.query,
                            onQueryChange = onQueryChange,
                            messages = uiState.messages,
                            activeUrl = uiState.activeUrl,
                            activeTitle = activeConversation?.title.orEmpty(),
                            isAsking = uiState.isAsking,
                            onAsk = onAsk,
                            onBack = onBackToConversations,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        HomeScreen(
                            uiState = uiState,
                            onUrlChange = onUrlChange,
                            onUpload = onUpload,
                            onSelectConversation = onSelectConversation,
                            onStartGlobalConversation = onStartGlobalConversation,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                AppTab.Analysis -> {
                    if (uiState.isArticleManagerOpen) {
                        ArticleManagerScreen(
                            articles = uiState.articlesResponse?.articles.orEmpty(),
                            isLoadingArticles = uiState.isLoadingArticles,
                            deletingArticleUrl = uiState.deletingArticleUrl,
                            articlesErrorMessage = uiState.articlesErrorMessage,
                            onBack = onCloseArticleManager,
                            onDeleteArticle = onDeleteArticle,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        AnalysisScreen(
                            conversations = uiState.conversations,
                            stats = uiState.statsResponse,
                            isLoading = uiState.isLoadingStats,
                            errorMessage = uiState.statsErrorMessage,
                            onOpenArticleManager = onOpenArticleManager,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                AppTab.Profile -> {
                    PlaceholderScreen(
                        title = "Profile",
                        description = "Account and settings will appear here.",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
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
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "SmartFeed",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Save articles and continue reading through conversation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Paste article link") },
                        placeholder = { Text("https://example.com/article") },
                        shape = RoundedCornerShape(18.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = onUpload,
                        enabled = !uiState.isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.isUploading) "Saving..." else "Save article")
                    }

                    if (uiState.isUploading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            uiState.uploadResponse?.let {
                UploadResult(response = it)
            }

            ConversationList(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                onSelectConversation = onSelectConversation
            )
        }

        FloatingActionButton(
            onClick = onStartGlobalConversation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = SoftBlue,
            contentColor = Color.White
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ChatDetailScreen(
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
private fun AnalysisScreen(
    conversations: List<Conversation>,
    stats: StatsResponse?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenArticleManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localArticles = conversations.count { it.url.isNotBlank() }
    val localMessages = conversations.sumOf { it.messages.size }
    val topics = stats?.topics.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Analysis",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onOpenArticleManager) {
                Text("Articles")
            }
        }

        Text(
            text = "Knowledge base content distribution.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
            ResultCard(title = "Stats Error") {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }

        ResultCard(title = "Topic share") {
            if (topics.isEmpty()) {
                Text(
                    text = "No backend topic stats yet. Upload or share articles first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TopicPieChart(topics = topics)
                TopicLegend(topics = topics)
            }
        }

        ResultCard(title = "Overview") {
            ResultRow(label = "Backend articles", value = (stats?.totalArticles ?: 0).toString())
            ResultRow(label = "Backend chunks", value = (stats?.totalChunks ?: 0).toString())
            ResultRow(label = "Local conversations", value = conversations.size.toString())
            ResultRow(label = "Local articles", value = localArticles.toString())
            ResultRow(label = "Local messages", value = localMessages.toString())
        }

        ResultCard(title = "Article share") {
            val articles = stats?.articles.orEmpty()
            if (articles.isEmpty()) {
                Text(
                    text = "No article distribution yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                articles.take(6).forEach { article ->
                    ResultRow(
                        label = article.title.ifBlank { article.url.ifBlank { "Untitled" } },
                        value = "${article.percentage}%"
                    )
                }
            }
        }

        ResultCard(title = "Source domains") {
            val domains = stats?.domains.orEmpty()
            if (domains.isEmpty()) {
                Text(
                    text = "No source domain distribution yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                domains.take(6).forEach { domain ->
                    ResultRow(
                        label = domain.domain,
                        value = "${domain.percentage}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleManagerScreen(
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
            text = "Grouped by topic. Tap an article to open it, or swipe right to delete it.",
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
            orderedTopics.forEach { topic ->
                Text(
                    text = topic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                groupedArticles.getValue(topic).forEach { article ->
                    SwipeDeleteArticleRow(
                        article = article,
                        isDeleting = deletingArticleUrl == article.url,
                        onDeleteArticle = onDeleteArticle
                    )
                }
            }
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
            if (value == SwipeToDismissBoxValue.StartToEnd && !isDeleting) {
                onDeleteArticle(article.url)
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SoftRedLight, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (isDeleting) "Deleting..." else "Delete",
                    color = SoftRed,
                    fontWeight = FontWeight.Bold
                )
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

@Composable
private fun TopicPieChart(topics: List<TopicDistribution>) {
    val chartTopics = topics.filter { it.percentage > 0.0 }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val diameter = min(size.width, size.height) * 0.82f
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        var startAngle = -90f
        chartTopics.forEachIndexed { index, topic ->
            val sweepAngle = (topic.percentage / 100.0 * 360.0).toFloat()
            drawArc(
                color = topicColor(index),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = topLeft,
                size = Size(diameter, diameter)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun TopicLegend(topics: List<TopicDistribution>) {
    topics.take(8).forEachIndexed { index, topic ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = topicColor(index), radius = 6.dp.toPx())
                }
                Text(text = topic.topic)
            }
            Text(
                text = "${topic.percentage}%",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = { Text(tab.label.take(1)) },
                label = { Text(tab.label) }
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
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        )
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
        is ChatMessage.User -> {
            UserBubble(text = message.text)
        }

        is ChatMessage.Summary -> {
            AssistantBubble(title = "Summary", text = message.text)
        }

        is ChatMessage.Assistant -> {
            AssistantMessage(response = message.response)
        }

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

private fun topicColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF8FAADC),
        Color(0xFFA8C7A3),
        Color(0xFFE6B89C),
        Color(0xFFC4B5D9),
        Color(0xFFE3A6A1),
        Color(0xFF9BC7C5),
        Color(0xFFD7C49E),
        Color(0xFFAEB7C2)
    )
    return colors[index % colors.size]
}

private val AppBackground = Color(0xFFF6F3EE)
private val SoftBlue = Color(0xFF8FAADC)
private val SoftBlueLight = Color(0xFFE7EEF8)
private val SoftGreen = Color(0xFFA8C7A3)
private val SoftRed = Color(0xFFC77878)
private val SoftRedLight = Color(0xFFF4E4E2)
