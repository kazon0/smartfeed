package com.example.smartfeedandroid.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.SoftBlueLight
import com.example.smartfeedandroid.ui.model.ChatMessage

@Composable
internal fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> UserBubble(text = message.text)
        is ChatMessage.Summary -> AssistantBubble(title = stringResource(R.string.summary), text = message.text)
        is ChatMessage.Assistant -> AssistantMessage(response = message.response)
        is ChatMessage.Error -> {
            ResultCard(title = stringResource(R.string.chat_error)) {
                Text(text = message.text, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun ThinkingBubble() {
    AssistantBubble(
        title = stringResource(R.string.app_name),
        text = stringResource(R.string.thinking)
    )
}

@Composable
private fun AssistantMessage(response: ChatResponse) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistantBubble(
            title = stringResource(R.string.app_name),
            text = response.answer.ifBlank {
                response.message.ifBlank { stringResource(R.string.no_answer_returned) }
            },
            footer = response.sourceType.ifBlank { response.status.ifBlank { "N/A" } }
        )

        if (response.sources.isNotEmpty()) {
            Text(
                text = stringResource(R.string.evidence),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            response.sources.take(3).forEach { source ->
                SourceCard(source = source)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SoftBlueLight)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp)
                )
            }
            BubbleTriangle(color = SoftBlueLight, pointsRight = true)
        }
    }
}

@Composable
private fun AssistantBubble(
    title: String,
    text: String,
    footer: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        BubbleTriangle(color = Color.White, pointsRight = false)
        Card(
            modifier = Modifier.widthIn(max = 330.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(text = text)
                if (footer.isNotBlank()) {
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleTriangle(color: Color, pointsRight: Boolean) {
    val path = remember(pointsRight) { Path() }
    Canvas(
        modifier = Modifier
            .padding(top = 12.dp)
            .size(width = 8.dp, height = 12.dp)
    ) {
        path.reset()
        if (pointsRight) {
            path.moveTo(0f, 0f)
            path.lineTo(size.width, size.height / 2f)
            path.lineTo(0f, size.height)
        } else {
            path.moveTo(size.width, 0f)
            path.lineTo(0f, size.height / 2f)
            path.lineTo(size.width, size.height)
        }
        path.close()
        drawPath(path = path, color = color)
    }
}
