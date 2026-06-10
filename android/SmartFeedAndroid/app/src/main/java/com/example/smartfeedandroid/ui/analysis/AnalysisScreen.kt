package com.example.smartfeedandroid.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.data.remote.StatsResponse
import com.example.smartfeedandroid.data.remote.TopicDistribution
import com.example.smartfeedandroid.ui.common.ResultCard
import com.example.smartfeedandroid.ui.common.ResultRow
import com.example.smartfeedandroid.ui.common.topicColor
import com.example.smartfeedandroid.ui.home.Conversation
import kotlin.math.min
import kotlin.math.round

@Composable
fun AnalysisScreen(
    conversations: List<Conversation>,
    stats: StatsResponse?,
    articles: List<SavedArticle>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenArticleManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localArticles = conversations.count { it.url.isNotBlank() }
    val localMessages = conversations.sumOf { it.messages.size }
    val topics = articleTopicDistribution(articles)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Analysis",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onOpenArticleManager,
                modifier = Modifier
                    .size(48.dp) // 控制整个按钮的点击热区大小
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_summary),
                    contentDescription = "Open article manager",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = "Knowledge base content distribution.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let {
            ResultCard(title = "Stats Error") {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }

        ResultCard(title = "Topic share") {
            if (topics.isEmpty()) {
                Text(
                    text = "No article topic stats yet. Upload or share articles first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TopicPieChart(topics = topics)
                TopicLegend(topics = topics)
            }
        }

        ResultCard(title = "Overview") {
            ResultRow(label = "Backend articles", value = (stats?.totalArticles ?: 0).toString())
            ResultRow(label = "Backend chunks", value = (stats?.totalChunks ?: 0).toString())
            ResultRow(label = "Managed articles", value = articles.size.toString())
            ResultRow(label = "Local conversations", value = conversations.size.toString())
            ResultRow(label = "Local articles", value = localArticles.toString())
            ResultRow(label = "Local messages", value = localMessages.toString())
        }

        ResultCard(title = "Source domains") {
            val domains = stats?.domains.orEmpty()
            if (domains.isEmpty()) {
                Text(
                    text = "No source domain distribution yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                domains.take(6).forEach { domain ->
                    ResultRow(
                        label = domain.domain,
                        value = "${domain.percentage}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicPieChart(topics: List<TopicDistribution>) {
    val chartTopics = topics.filter { it.percentage > 0.0 }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val diameter = min(size.width, size.height) * 0.82f
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        var startAngle = -90f
        chartTopics.forEachIndexed { index, topic ->
            val sweepAngle = (topic.percentage / 100.0 * 360.0).toFloat()
            drawArc(
                color = topicColor(index),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = topLeft,
                size = Size(diameter, diameter)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun TopicLegend(topics: List<TopicDistribution>) {
    topics.take(8).forEachIndexed { index, topic ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(color = topicColor(index), radius = 6.dp.toPx())
                }
                Text(text = topic.topic)
            }
            Text(
                text = "${topic.percentage}%",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun articleTopicDistribution(articles: List<SavedArticle>): List<TopicDistribution> {
    if (articles.isEmpty()) {
        return emptyList()
    }

    val total = articles.size.toDouble()
    return articles
        .groupingBy { it.topic.ifBlank { "其他" } }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { (topic, count) ->
            TopicDistribution(
                topic = topic,
                chunkCount = count,
                percentage = round(count * 10000.0 / total) / 100.0
            )
        }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisScreenPreview() {
    AnalysisScreen(
        conversations = emptyList(),
        stats = null,
        articles = listOf(
            SavedArticle(
                url = "https://example.com/a",
                title = "AI Article",
                domain = "example.com",
                chunkCount = 5,
                topic = "科技"
            ),
            SavedArticle(
                url = "https://example.com/b",
                title = "Life Article",
                domain = "example.com",
                chunkCount = 3,
                topic = "生活"
            )
        ),
        isLoading = false,
        errorMessage = null,
        onOpenArticleManager = {}
    )
}
