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
import com.example.smartfeedandroid.data.remote.AuthUser
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
import com.example.smartfeedandroid.ui.profile.AuthScreen
import com.example.smartfeedandroid.ui.profile.AuthViewModel
import com.example.smartfeedandroid.ui.profile.ProfileScreen
import com.example.smartfeedandroid.ui.profile.SessionLoadingScreen
import com.example.smartfeedandroid.ui.state.HomeUiState

@Composable
fun SmartFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    analysisViewModel: AnalysisViewModel = viewModel(),
    articleManagerViewModel: ArticleManagerViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val authState = authViewModel.uiState
    if (authState.isCheckingSession) {
        SessionLoadingScreen(modifier)
        return
    }
    val user = authState.user
    if (user == null) {
        AuthScreen(
            uiState = authState,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onClearError = authViewModel::clearError,
            modifier = modifier
        )
        return
    }

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
            when (tab) {
                AppTab.Analysis -> analysisViewModel.refresh()
                AppTab.Articles -> articleManagerViewModel.refreshArticles()
                else -> Unit
            }
        },
        onSelectConversation = viewModel::selectConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onStartGlobalConversation = viewModel::startGlobalConversation,
        onBackToConversations = viewModel::showConversationList,
        onOpenArticleChat = viewModel::startArticleConversation,
        onDeleteArticle = articleManagerViewModel::deleteArticle,
        profileUser = user,
        onLogout = authViewModel::logout,
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
    onOpenArticleChat: (SavedArticle) -> Unit,
    onDeleteArticle: (String) -> Unit,
    profileUser: AuthUser,
    onLogout: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeConversation = uiState.conversations
        .firstOrNull { it.id == uiState.activeConversationId }

    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!(uiState.selectedTab == AppTab.Home && uiState.isChatOpen)) {
                    AppBottomBar(
                        selectedTab = uiState.selectedTab,
                        onSelectTab = onSelectTab,
                        onNewChat = onStartGlobalConversation
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                AppTab.Articles -> {
                    ArticleManagerScreen(
                        articles = articleManagerUiState.articlesResponse?.articles.orEmpty(),
                        isLoadingArticles = articleManagerUiState.isLoadingArticles,
                        deletingArticleUrl = articleManagerUiState.deletingArticleUrl,
                        articlesErrorMessage = articleManagerUiState.articlesErrorMessage,
                        onOpenArticleChat = onOpenArticleChat,
                        onDeleteArticle = onDeleteArticle,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                AppTab.Analysis -> {
                    AnalysisScreen(
                        conversations = uiState.conversations,
                        stats = analysisUiState.statsResponse,
                        insights = analysisUiState.insightsResponse,
                        articles = analysisUiState.articlesResponse?.articles.orEmpty(),
                        isLoading = analysisUiState.isLoadingStats,
                        errorMessage = analysisUiState.statsErrorMessage
                            ?: analysisUiState.insightsErrorMessage
                            ?: analysisUiState.articlesErrorMessage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                AppTab.Profile -> {
                    ProfileScreen(
                        user = profileUser,
                        onLogout = onLogout,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
