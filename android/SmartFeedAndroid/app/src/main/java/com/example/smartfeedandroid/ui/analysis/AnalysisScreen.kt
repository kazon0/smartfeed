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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.DomainDistribution
import com.example.smartfeedandroid.data.remote.InsightsResponse
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
    insights: InsightsResponse?,
    articles: List<SavedArticle>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenArticleManager: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = stringResource(R.string.analysis),
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
                    contentDescription = stringResource(R.string.open_article_manager),
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.knowledge_distribution_subtitle),
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
            ResultCard(title = stringResource(R.string.stats_error)) {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }


        ResultCard(title = stringResource(R.string.topic_share)) {
            if (topics.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_topic_stats),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TopicPieChart(topics = topics)
                TopicLegend(topics = topics)
            }
        }
        SmartInsightCard(insights = insights)

        KnowledgeProfileCard(
            articles = articles,
            topics = topics,
            domains = stats?.domains.orEmpty(),
            localMessages = localMessages
        )

        ContentDepthCard(articles = articles)

        ResultCard(title = stringResource(R.string.source_domains)) {
            val domains = stats?.domains.orEmpty()
            if (domains.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_source_domains),
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
private fun SmartInsightCard(insights: InsightsResponse?) {
    ResultCard(title = stringResource(R.string.smart_insight)) {
        if (insights == null || insights.summary.isBlank()) {
            Text(
                text = stringResource(R.string.smart_insight_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ResultCard
        }

        Text(text = insights.summary)
        Text(
            text = if (insights.source == "llm") {
                stringResource(R.string.smart_insight_source_llm)
            } else {
                stringResource(R.string.smart_insight_source_fallback)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InsightBullets(
            title = stringResource(R.string.highlights),
            items = insights.highlights
        )
        InsightBullets(
            title = stringResource(R.string.suggestions),
            items = insights.suggestions
        )
    }
}

@Composable
private fun InsightBullets(title: String, items: List<String>) {
    val cleanItems = items.filter { it.isNotBlank() }
    if (cleanItems.isEmpty()) {
        return
    }

    Text(text = title, fontWeight = FontWeight.SemiBold)
    cleanItems.forEach { item ->
        Text(
            text = "· $item",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KnowledgeProfileCard(
    articles: List<SavedArticle>,
    topics: List<TopicDistribution>,
    domains: List<DomainDistribution>,
    localMessages: Int
) {
    ResultCard(title = stringResource(R.string.knowledge_profile)) {
        if (articles.isEmpty()) {
            Text(
                text = stringResource(R.string.knowledge_profile_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.knowledge_suggestion_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@ResultCard
        }

        val topTopic = topics.maxByOrNull { it.percentage }
        val topDomain = domains.maxByOrNull { it.percentage }
        Text(
            text = stringResource(
                R.string.knowledge_profile_saved_articles,
                articles.size,
                topics.size
            )
        )
        topTopic?.let { topic ->
            Text(
                text = stringResource(
                    R.string.knowledge_profile_top_topic,
                    topic.topic,
                    topic.percentage.toString()
                )
            )
        }
        topDomain?.takeIf { it.domain.isNotBlank() }?.let { domain ->
            Text(
                text = stringResource(
                    R.string.knowledge_profile_top_domain,
                    domain.domain
                )
            )
        }
        Text(
            text = stringResource(R.string.knowledge_profile_conversations, localMessages)
        )
        Text(
            text = stringResource(R.string.knowledge_suggestion),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (topics.size <= 1) {
                stringResource(R.string.knowledge_suggestion_single_topic)
            } else {
                stringResource(R.string.knowledge_suggestion_multi_topic)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContentDepthCard(articles: List<SavedArticle>) {
    ResultCard(title = stringResource(R.string.content_depth)) {
        val topArticles = articles
            .filter { it.chunkCount > 0 }
            .sortedByDescending { it.chunkCount }
            .take(3)

        if (topArticles.isEmpty()) {
            Text(
                text = stringResource(R.string.content_depth_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            topArticles.forEach { article ->
                ContentDepthItem(article = article)
            }
            Text(
                text = stringResource(R.string.content_depth_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContentDepthItem(article: SavedArticle) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = article.title.ifBlank { article.domain.ifBlank { article.url } },
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Text(
            text = "${article.chunkCount} ${stringResource(R.string.chunk_count)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        insights = InsightsResponse(
            status = "ok",
            summary = "当前知识库主要集中在科技和生活内容，适合继续围绕具体文章进行追问。",
            highlights = listOf("科技主题占比较高", "来源较集中"),
            suggestions = listOf("可以补充不同来源文章", "从文章管理页打开重点文章继续提问"),
            source = "llm",
            totalArticles = 2
        ),
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
