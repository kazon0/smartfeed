package com.example.smartfeedandroid.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.UploadResponse
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.ResultRow
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
import com.example.smartfeedandroid.ui.state.HomeUiState
import com.example.smartfeedandroid.ui.state.UploadProgress
import com.example.smartfeedandroid.ui.theme.KalamFontFamily

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var conversationSearchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JournalPaper)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .padding(bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            JournalHeader()
            MemorySearchBar(
                query = conversationSearchQuery,
                onQueryChange = { conversationSearchQuery = it }
            )
            UploadLinkCard(
                url = uiState.url,
                isUploading = uiState.isUploading,
                uploadProgress = uiState.uploadProgress,
                uploadStatusText = uiState.uploadStatusText,
                onUrlChange = onUrlChange,
                onUpload = onUpload
            )
            if (uiState.uploadSummaryText.isNotBlank()) {
                UploadStreamingSummary(summary = uiState.uploadSummaryText)
            }
            uiState.uploadResponse?.let {
                UploadResult(response = it)
            }

            ConversationList(
                conversations = uiState.conversations,
                activeConversationId = uiState.activeConversationId,
                searchQuery = conversationSearchQuery,
                onSelectConversation = onSelectConversation,
                onDeleteConversation = onDeleteConversation
            )
        }
    }
}

@Composable
private fun JournalHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WbSunny,
                    contentDescription = null,
                    tint = JournalTerra,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Morning!",
                    color = JournalTerra,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp)
                        .size(width = 82.dp, height = 10.dp)
                ) {
                    val path = Path()
                    val centerY = size.height * 0.55f
                    path.moveTo(0f, centerY)
                    val step = size.width / 4f
                    repeat(4) { index ->
                        val startX = step * index
                        path.quadraticTo(
                            startX + step / 2f,
                            if (index % 2 == 0) 0f else size.height,
                            startX + step,
                            centerY
                        )
                    }
                    drawPath(
                        path = path,
                        color = JournalTerra,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.52f), CircleShape)
                .drawBehind {
                    drawCircle(
                        color = JournalInkLight.copy(alpha = 0.48f),
                        radius = size.minDimension / 2f - 2.dp.toPx(),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Eco,
                contentDescription = null,
                tint = JournalInkLight,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MemorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("寻找记忆碎片...") },
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
}

@Composable
private fun UploadLinkCard(
    url: String,
    isUploading: Boolean,
    uploadProgress: UploadProgress?,
    uploadStatusText: String,
    onUrlChange: (String) -> Unit,
    onUpload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .shadow(5.dp, RoundedCornerShape(28.dp), clip = false)
                .background(
                    color = Color(0xFFFFF8E6),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    width = 1.dp,
                    color = JournalYellow,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(JournalTerra.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = JournalTerra,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.paste_or_share_article_link),
                    color = JournalInk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("长按粘贴链接...") },
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = JournalBlue.copy(alpha = 0.55f),
                    unfocusedBorderColor = JournalLine,
                    focusedTextColor = JournalInk,
                    unfocusedTextColor = JournalInk,
                    focusedPlaceholderColor = JournalInkLight.copy(alpha = 0.72f),
                    unfocusedPlaceholderColor = JournalInkLight.copy(alpha = 0.72f)
                ),
                trailingIcon = {
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(24.dp),
                            color = JournalTerra,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        IconButton(
                            onClick = onUpload,
                            enabled = url.isNotBlank(),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(38.dp)
                                .background(
                                    color = if (url.isNotBlank()) JournalInk else JournalLine,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.save_result),
                                tint = if (url.isNotBlank()) JournalPaper else JournalInkLight
                            )
                        }
                    }
                }
            )
            UploadProgressText(
                uploadProgress = uploadProgress,
                uploadStatusText = uploadStatusText
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 24.dp, y = 1.dp)
                .size(width = 56.dp, height = 17.dp)
                .shadow(1.dp, RoundedCornerShape(2.dp), clip = false)
                .background(
                    Color(0xFFFDFCF7),
                    RoundedCornerShape(2.dp)
                )
                .border(1.dp, JournalLine.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun UploadResult(response: UploadResponse) {
    ResultCard(title = stringResource(R.string.save_result)) {
        ResultRow(label = stringResource(R.string.status), value = response.status)
        ResultRow(label = stringResource(R.string.chunk_count), value = response.storedChunks.toString())
        ResultRow(label = stringResource(R.string.title), value = response.data?.title.orEmpty().ifBlank { "N/A" })
        ResultRow(label = stringResource(R.string.parser), value = response.data?.metadata?.parser ?: "N/A")

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.summary), fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = response.summary.ifBlank {
                response.error ?: stringResource(R.string.no_summary)
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun UploadProgressText(
    uploadProgress: UploadProgress?,
    uploadStatusText: String
) {
    val fallbackText = when (uploadProgress) {
        UploadProgress.CheckingStatus -> stringResource(R.string.upload_progress_checking)
        UploadProgress.OpeningSavedArticle -> stringResource(R.string.upload_progress_opening_saved)
        UploadProgress.UploadingNewArticle -> stringResource(R.string.upload_progress_uploading_new)
        null -> ""
    }
    val text = uploadStatusText.ifBlank { fallbackText }
    if (text.isBlank()) {
        return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun UploadStreamingSummary(summary: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(-0.25f)
                .shadow(5.dp, shape, clip = false)
                .clip(shape)
                .background(Color.White)
                .drawBehind {
                    val spacing = 20.dp.toPx()
                    var x = spacing
                    while (x < size.width) {
                        drawLine(
                            JournalInkLight.copy(alpha = 0.06f),
                            androidx.compose.ui.geometry.Offset(x, 0f),
                            androidx.compose.ui.geometry.Offset(x, size.height),
                            1f
                        )
                        x += spacing
                    }
                    var y = spacing
                    while (y < size.height) {
                        drawLine(
                            JournalInkLight.copy(alpha = 0.06f),
                            androidx.compose.ui.geometry.Offset(0f, y),
                            androidx.compose.ui.geometry.Offset(size.width, y),
                            1f
                        )
                        y += spacing
                    }
                }
                .border(1.dp, JournalLine.copy(alpha = 0.7f), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(JournalTerra.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = JournalTerra,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = "文章速记",
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Generated by AI",
                    color = JournalInkLight,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = JournalInk,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-25).dp, y = (-7).dp)
                .rotate(2f)
                .size(width = 60.dp, height = 18.dp)
                .background(JournalYellow.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                .border(1.dp, JournalYellow, RoundedCornerShape(2.dp))
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(
        uiState = HomeUiState(
            url = "https://example.com/article",
            conversations = listOf(
                Conversation(
                    id = "1",
                    title = "程序员应该知道的十个基础算法",
                    url = "https://example.com/article",
                    status = "received",
                    storedChunks = 8,
                    updatedAtMillis = 0L,
                    messages = listOf(ChatMessage.Summary("这是一篇文章摘要。"))
                )
            )
        ),
        onUrlChange = {},
        onUpload = {},
        onSelectConversation = {},
        onDeleteConversation = {}
    )
}
