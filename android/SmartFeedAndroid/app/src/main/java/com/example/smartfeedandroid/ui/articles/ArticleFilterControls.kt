package com.example.smartfeedandroid.ui.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.smartfeedandroid.ui.common.SoftBlue

@Composable
internal fun ArticleSearchAndSort(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    articleSort: ArticleSort,
    onSelectSort: (ArticleSort) -> Unit,
    visibleArticleCount: Int
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_articles)) },
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = SoftBlue,
                unfocusedBorderColor = Color.LightGray
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.article_result_count, visibleArticleCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(articleSortLabel(articleSort))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
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
}

@Composable
private fun articleSortLabel(sort: ArticleSort): String {
    return when (sort) {
        ArticleSort.Default -> stringResource(R.string.article_sort_default)
        ArticleSort.Title -> stringResource(R.string.article_sort_title)
        ArticleSort.ChunkCount -> stringResource(R.string.article_sort_chunks)
    }
}
