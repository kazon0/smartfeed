package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.theme.KalamFontFamily

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp)
            .drawBehind {
                val segment = 6.dp.toPx()
                val gap = 5.dp.toPx()
                var y = 2.dp.toPx()
                while (y < size.height - 2.dp.toPx()) {
                    drawLine(
                        color = JournalLine,
                        start = androidx.compose.ui.geometry.Offset(1.dp.toPx(), y),
                        end = androidx.compose.ui.geometry.Offset(1.dp.toPx(), (y + segment).coerceAtMost(size.height)),
                        strokeWidth = 2.dp.toPx()
                    )
                    y += segment + gap
                }
            }
            .padding(start = 13.dp)
            .background(Color.White.copy(alpha = 0.70f), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White, RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded }
            .padding(start = 13.dp, top = 11.dp, bottom = 11.dp, end = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = if (expanded) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (explanation.isNotBlank()) {
                    Text(
                        text = explanation,
                        color = JournalInkLight,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (expanded && source.sectionTitle.isNotBlank() && source.sourceSummary.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.source_section, source.sectionTitle),
                        color = JournalBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (expanded && source.url.isNotBlank()) {
                    Text(
                        text = source.url,
                        color = JournalInkLight,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = KalamFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (source.url.isNotBlank()) {
                IconButton(
                    onClick = { uriHandler.openUri(source.url) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.open_original_page),
                        tint = JournalBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.hide_source_detail)
                } else {
                    stringResource(R.string.view_source_detail)
                },
                tint = JournalInkLight,
                modifier = Modifier
                    .padding(top = 8.dp, end = 5.dp)
                    .size(18.dp)
            )
        }
    }
}
