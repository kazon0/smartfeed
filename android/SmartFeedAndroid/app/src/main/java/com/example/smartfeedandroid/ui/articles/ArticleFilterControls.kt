package com.example.smartfeedandroid.ui.articles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper

@Composable
internal fun ArticleSearchAndSort(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    articleSort: ArticleSort,
    onSelectSort: (ArticleSort) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_articles)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = JournalInkLight
                )
            },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = JournalInk,
                unfocusedTextColor = JournalInk,
                focusedPlaceholderColor = JournalInkLight,
                unfocusedPlaceholderColor = JournalInkLight
            )
        )
        Box {
            IconButton(
                onClick = { sortMenuExpanded = true },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, JournalLine, RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = articleSortLabel(articleSort),
                    tint = JournalInk
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                containerColor = JournalPaper
            ) {
                ArticleSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(articleSortLabel(sort)) },
                        onClick = {
                            onSelectSort(sort)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun articleSortLabel(sort: ArticleSort): String {
    return when (sort) {
        ArticleSort.Default -> stringResource(R.string.article_sort_default)
        ArticleSort.Title -> stringResource(R.string.article_sort_title)
        ArticleSort.ChunkCount -> stringResource(R.string.article_sort_chunks)
    }
}
