package com.example.smartfeedandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartfeedandroid.ui.home.HomeViewModel
import com.example.smartfeedandroid.data.remote.SmartFeedNetwork
import com.example.smartfeedandroid.data.auth.AuthSession
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.home.SmartFeedScreen
import com.example.smartfeedandroid.ui.theme.SmartFeedAndroidTheme

class MainActivity : ComponentActivity() {
    private var pendingSharedUrl: String? = null
    private var homeViewModel: HomeViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SmartFeedNetwork.initialize(applicationContext)
        val paperColor = JournalPaper.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(paperColor, paperColor),
            navigationBarStyle = SystemBarStyle.light(paperColor, paperColor)
        )
        pendingSharedUrl = extractSharedUrl(intent)
        setContent {
            SmartFeedAndroidTheme {
                val viewModel: HomeViewModel = viewModel()
                homeViewModel = viewModel
                val sharedUrl = pendingSharedUrl
                val authSession by AuthSession.state.collectAsState()
                LaunchedEffect(sharedUrl, authSession.user) {
                    if (authSession.user != null) sharedUrl?.let {
                        viewModel.handleSharedUrl(it)
                        pendingSharedUrl = null
                    }
                }
                SmartFeedScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sharedUrl = extractSharedUrl(intent) ?: return
        if (AuthSession.state.value.user != null) {
            homeViewModel?.handleSharedUrl(sharedUrl) ?: run {
                pendingSharedUrl = sharedUrl
            }
        } else {
            pendingSharedUrl = sharedUrl
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) {
            return null
        }
        val text = listOf(
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        ).joinToString(separator = " ")

        return URL_PATTERN.find(text)
            ?.value
            ?.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
    }

    private companion object {
        val URL_PATTERN = Regex("""https?://\S+""")
    }
}
