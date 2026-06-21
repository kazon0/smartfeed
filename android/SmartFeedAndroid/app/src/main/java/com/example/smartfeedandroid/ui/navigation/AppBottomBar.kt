package com.example.smartfeedandroid.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPaperDeep
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.home.AppTab

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 14.dp)
                .fillMaxWidth()
                .height(72.dp)
                .shadow(20.dp, RoundedCornerShape(38.dp), clip = false)
                .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(38.dp))
                .border(1.4.dp, Color.White.copy(alpha = 0.86f), RoundedCornerShape(38.dp))
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            JournalNavItem(
                icon = Icons.Filled.Home,
                selected = selectedTab == AppTab.Home,
                onClick = { onSelectTab(AppTab.Home) }
            )
            JournalNavItem(
                icon = Icons.AutoMirrored.Filled.Article,
                selected = selectedTab == AppTab.Articles,
                onClick = { onSelectTab(AppTab.Articles) }
            )
            Box(modifier = Modifier.size(54.dp))
            JournalNavItem(
                icon = Icons.Filled.PieChart,
                selected = selectedTab == AppTab.Analysis,
                onClick = { onSelectTab(AppTab.Analysis) }
            )
            JournalNavItem(
                icon = Icons.Filled.Person,
                selected = selectedTab == AppTab.Profile,
                onClick = { onSelectTab(AppTab.Profile) }
            )
        }
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .clickable(onClick = onNewChat),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(3.dp, CircleShape, clip = false)
                    .background(JournalPaper.copy(alpha = 0.96f), CircleShape)
                    .padding(7.dp)
                    .background(JournalTerra, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = JournalPaper,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

@Composable
private fun JournalNavItem(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = if (selected) JournalPaperDeep.copy(alpha = 0.88f) else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) JournalInk else JournalInkLight,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
