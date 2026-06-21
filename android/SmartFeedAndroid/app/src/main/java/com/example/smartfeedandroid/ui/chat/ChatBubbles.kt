package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPaperDeep
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.theme.KalamFontFamily
import com.example.smartfeedandroid.ui.theme.NunitoFontFamily
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
internal fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> UserBubble(text = message.text)
        is ChatMessage.Summary -> SummaryJournalCard(text = message.text)
        is ChatMessage.Assistant -> AssistantMessage(response = message.response)
        is ChatMessage.Error -> {
            ResultCard(title = stringResource(R.string.chat_error)) {
                Text(text = message.text, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun ThinkingBubble(text: String = "") {
    AssistantBubble(
        title = stringResource(R.string.app_name),
        text = text.ifBlank { stringResource(R.string.thinking) }
    )
}

@Composable
private fun SummaryJournalCard(text: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 9.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(0.3f)
                .shadow(5.dp, shape, clip = false)
                .clip(shape)
                .background(Color.White)
                .drawBehind {
                    val spacing = 20.dp.toPx()
                    var x = spacing
                    while (x < size.width) {
                        drawLine(JournalInkLight.copy(alpha = 0.055f), Offset(x, 0f), Offset(x, size.height), 1f)
                        x += spacing
                    }
                    var y = spacing
                    while (y < size.height) {
                        drawLine(JournalInkLight.copy(alpha = 0.055f), Offset(0f, y), Offset(size.width, y), 1f)
                        y += spacing
                    }
                }
                .border(1.dp, JournalLine.copy(alpha = 0.65f), shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(JournalBlue.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = JournalBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = "核心总结",
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Summary",
                    color = JournalInkLight,
                    fontFamily = KalamFontFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            DashedJournalDivider()
            JournalMarkdown(content = text, summaryStyle = true)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .rotate(2f)
                .size(width = 66.dp, height = 20.dp)
                .background(JournalYellow.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                .border(1.dp, JournalYellow, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun DashedJournalDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        val segment = 7.dp.toPx()
        val gap = 5.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = JournalLine,
                start = Offset(x, size.height / 2f),
                end = Offset((x + segment).coerceAtMost(size.width), size.height / 2f),
                strokeWidth = 1.dp.toPx()
            )
            x += segment + gap
        }
    }
}

@Composable
private fun AssistantMessage(response: ChatResponse) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AssistantBubble(
            title = stringResource(R.string.app_name),
            text = response.answer.ifBlank {
                response.message.ifBlank { stringResource(R.string.no_answer_returned) }
            },
            footer = response.sourceType.ifBlank { response.status.ifBlank { "N/A" } },
            renderMarkdown = true
        )

        if (response.sources.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = JournalInkLight,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(R.string.evidence),
                    style = MaterialTheme.typography.labelSmall,
                    color = JournalInkLight,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            SourceCard(source = response.sources.first())
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .rotate(-0.5f)
                .shadow(3.dp, RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp), clip = false)
                .background(JournalInk, RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp))
                .padding(horizontal = 17.dp, vertical = 12.dp)
        ) {
            Text(
                text = text,
                color = JournalPaper,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    title: String,
    text: String,
    footer: String = "",
    renderMarkdown: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 8.dp)
                .size(32.dp)
                .shadow(2.dp, CircleShape, clip = false)
                .background(Color.White, CircleShape)
                .border(1.dp, JournalLine, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Eco,
                contentDescription = null,
                tint = JournalGreen,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .shadow(3.dp, RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp), clip = false)
                .background(Color.White, RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp))
                .border(1.dp, JournalLine.copy(alpha = 0.6f), RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = title,
                color = JournalInk,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelLarge
            )
            if (renderMarkdown) {
                JournalMarkdown(content = text, summaryStyle = false)
            } else {
                Text(text = text, color = JournalInk, style = MaterialTheme.typography.bodyMedium)
            }
            if (footer.isNotBlank()) {
                Text(
                    text = footer,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = JournalInkLight,
                    fontFamily = KalamFontFamily
                )
            }
        }
    }
}

@Composable
private fun JournalMarkdown(content: String, summaryStyle: Boolean) {
    val base = MaterialTheme.typography.bodyMedium.copy(
        color = JournalInk,
        fontFamily = NunitoFontFamily,
        fontWeight = if (summaryStyle) FontWeight.SemiBold else FontWeight.Normal,
        lineHeight = if (summaryStyle) 25.sp else 23.sp
    )
    val heading = MaterialTheme.typography.titleMedium.copy(
        color = JournalInk,
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.ExtraBold
    )
    val codeStyle = MaterialTheme.typography.bodyMedium.copy(
        color = JournalTerra,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
    val typography = markdownTypography(
        h1 = heading.copy(fontSize = 21.sp),
        h2 = heading.copy(fontSize = 19.sp),
        h3 = heading.copy(fontSize = 17.sp),
        h4 = heading,
        h5 = heading,
        h6 = heading,
        text = base,
        code = codeStyle,
        inlineCode = codeStyle,
        quote = base.copy(fontFamily = KalamFontFamily, color = JournalInkLight),
        paragraph = base,
        ordered = base,
        bullet = base.copy(color = JournalTerra),
        list = base,
        link = base.copy(color = JournalBlue, fontWeight = FontWeight.Bold),
        table = base
    )
    val colors = markdownColor(
        text = JournalInk,
        codeBackground = JournalPaperDeep,
        inlineCodeBackground = JournalYellow.copy(alpha = 0.42f),
        dividerColor = JournalLine,
        tableBackground = JournalPaper
    )
    Markdown(
        content = content,
        colors = colors,
        typography = typography
    )
}
