package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.ChatSource

@Composable
internal fun SourceCard(source: ChatSource) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val title = source.displayTitle.ifBlank {
        source.title.ifBlank { stringResource(R.string.untitled_source) }
    }
    val explanation = source.sourceSummary.ifBlank {
        source.sectionTitle.ifBlank { source.url }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (explanation.isNotBlank()) {
                Text(
                    text = stringResource(R.string.source_relation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = explanation,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (expanded && source.sectionTitle.isNotBlank() && source.sourceSummary.isNotBlank()) {
                Text(
                    text = stringResource(R.string.source_section, source.sectionTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded && source.url.isNotBlank()) {
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.hide_source_detail)
                        } else {
                            stringResource(R.string.view_source_detail)
                        }
                    )
                }
                if (source.url.isNotBlank()) {
                    TextButton(onClick = { uriHandler.openUri(source.url) }) {
                        Text(text = stringResource(R.string.open_original_page))
                    }
                }
            }
        }
    }
}
