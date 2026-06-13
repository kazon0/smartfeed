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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.ui.analysis.AnalysisScreen
import com.example.smartfeedandroid.ui.analysis.AnalysisUiState
import com.example.smartfeedandroid.ui.analysis.AnalysisViewModel
import com.example.smartfeedandroid.ui.articles.ArticleManagerScreen
import com.example.smartfeedandroid.ui.articles.ArticleManagerUiState
import com.example.smartfeedandroid.ui.articles.ArticleManagerViewModel
import com.example.smartfeedandroid.ui.chat.ChatDetailScreen
import com.example.smartfeedandroid.ui.chat.ChatUiState
import com.example.smartfeedandroid.ui.chat.ChatViewModel
import com.example.smartfeedandroid.ui.navigation.AppBottomBar
import com.example.smartfeedandroid.ui.profile.PlaceholderScreen

@Composable
fun SmartFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    analysisViewModel: AnalysisViewModel = viewModel(),
    articleManagerViewModel: ArticleManagerViewModel = viewModel()
) {
    SmartFeedContent(
        uiState = viewModel.uiState,
        chatUiState = chatViewModel.uiState,
        analysisUiState = analysisViewModel.uiState,
        articleManagerUiState = articleManagerViewModel.uiState,
        onUrlChange = viewModel::onUrlChange,
        onUpload = viewModel::upload,
        onQueryChange = chatViewModel::onQueryChange,
        onAsk = {
            chatViewModel.ask(
                prepareUserMessage = viewModel::prepareUserMessage,
                appendResultMessage = viewModel::appendChatMessage,
                showError = viewModel::showError
            )
        },
        onSelectTab = { tab ->
            viewModel.selectTab(tab)
            if (tab == AppTab.Analysis) {
                analysisViewModel.refresh()
            }
        },
        onSelectConversation = viewModel::selectConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onStartGlobalConversation = viewModel::startGlobalConversation,
        onBackToConversations = viewModel::showConversationList,
        onOpenArticleManager = {
            viewModel.openArticleManager()
            articleManagerViewModel.refreshArticles()
        },
        onCloseArticleManager = {
            viewModel.closeArticleManager()
            analysisViewModel.refresh()
        },
        onOpenArticleChat = viewModel::startArticleConversation,
        onDeleteArticle = articleManagerViewModel::deleteArticle,
        onDismissError = viewModel::clearError,
        modifier = modifier
    )
}

@Composable
private fun SmartFeedContent(
    uiState: HomeUiState,
    chatUiState: ChatUiState,
    analysisUiState: AnalysisUiState,
    articleManagerUiState: ArticleManagerUiState,
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
    onOpenArticleChat: (SavedArticle) -> Unit,
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
                    title = { Text(stringResource(R.string.generic_error_title)) },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }

            when (uiState.selectedTab) {
                AppTab.Home -> {
                    if (uiState.isChatOpen) {
                        ChatDetailScreen(
                            query = chatUiState.query,
                            onQueryChange = onQueryChange,
                            messages = uiState.messages,
                            activeUrl = uiState.activeUrl,
                            activeTitle = activeConversation?.title.orEmpty(),
                            isAsking = chatUiState.isAsking,
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
                            articles = articleManagerUiState.articlesResponse?.articles.orEmpty(),
                            isLoadingArticles = articleManagerUiState.isLoadingArticles,
                            deletingArticleUrl = articleManagerUiState.deletingArticleUrl,
                            articlesErrorMessage = articleManagerUiState.articlesErrorMessage,
                            onBack = onCloseArticleManager,
                            onOpenArticleChat = onOpenArticleChat,
                            onDeleteArticle = onDeleteArticle,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        AnalysisScreen(
                            conversations = uiState.conversations,
                            stats = analysisUiState.statsResponse,
                            insights = analysisUiState.insightsResponse,
                            articles = analysisUiState.articlesResponse?.articles.orEmpty(),
                            isLoading = analysisUiState.isLoadingStats,
                            errorMessage = analysisUiState.statsErrorMessage
                                ?: analysisUiState.insightsErrorMessage
                                ?: analysisUiState.articlesErrorMessage,
                            onOpenArticleManager = onOpenArticleManager,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                AppTab.Profile -> {
                    PlaceholderScreen(
                        title = stringResource(R.string.profile_title),
                        description = stringResource(R.string.profile_placeholder),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
