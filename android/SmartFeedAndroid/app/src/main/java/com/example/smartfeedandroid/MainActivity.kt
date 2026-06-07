package com.example.smartfeedandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.smartfeedandroid.ui.home.SmartFeedScreen
import com.example.smartfeedandroid.ui.theme.SmartFeedAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFeedAndroidTheme {
                SmartFeedScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
