package com.example.smartfeedandroid.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.SoftBlue
import com.example.smartfeedandroid.ui.home.AppTab

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onNewChat: () -> Unit
) {
    NavigationBar {
        listOf(AppTab.Home, AppTab.Articles).forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = { Text(label.take(1)) },
                label = { Text(label) }
            )
        }

        NavigationBarItem(
            selected = false,
            onClick = onNewChat,
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.new_chat),
                    tint = Color.White,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SoftBlue)
                )
            },
            label = { Text(stringResource(R.string.new_chat)) }
        )

        listOf(AppTab.Analysis, AppTab.Profile).forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = { Text(label.take(1)) },
                label = { Text(label) }
            )
        }
    }
}
