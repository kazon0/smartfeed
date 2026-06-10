package com.example.smartfeedandroid.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.smartfeedandroid.ui.home.AppTab

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar {
        AppTab.entries.forEach { tab ->
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
