package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartfeedandroid.ui.analysis.AnalysisScreen
import com.example.smartfeedandroid.ui.articles.ArticleManagerScreen
import com.example.smartfeedandroid.ui.chat.ChatDetailScreen
import com.example.smartfeedandroid.ui.navigation.AppBottomBar
import com.example.smartfeedandroid.ui.profile.PlaceholderScreen

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
        onDeleteConversation = viewModel::deleteConversation,
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
    onDeleteConversation: (String) -> Unit,
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
                            onDeleteConversation = onDeleteConversation,
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
                            articles = uiState.articlesResponse?.articles.orEmpty(),
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
