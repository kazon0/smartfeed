package com.example.smartfeedandroid.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.data.remote.DomainDistribution
import com.example.smartfeedandroid.data.remote.InsightsResponse
import com.example.smartfeedandroid.data.remote.SavedArticle
import com.example.smartfeedandroid.data.remote.StatsResponse
import com.example.smartfeedandroid.data.remote.TopicDistribution
import com.example.smartfeedandroid.ui.common.JournalBlue
import com.example.smartfeedandroid.ui.common.JournalGreen
import com.example.smartfeedandroid.ui.common.JournalInk
import com.example.smartfeedandroid.ui.common.JournalInkLight
import com.example.smartfeedandroid.ui.common.JournalLine
import com.example.smartfeedandroid.ui.common.JournalPaper
import com.example.smartfeedandroid.ui.common.JournalPink
import com.example.smartfeedandroid.ui.common.JournalTerra
import com.example.smartfeedandroid.ui.common.JournalYellow
import com.example.smartfeedandroid.ui.common.topicColor
import com.example.smartfeedandroid.ui.model.Conversation
import com.example.smartfeedandroid.ui.theme.KalamFontFamily
import kotlin.math.min
import kotlin.math.round

private val AnalysisBlobShape = GenericShape { size, _ ->
    moveTo(size.width * 0.50f, size.height * 0.01f)
    quadraticTo(size.width * 0.77f, 0f, size.width * 0.96f, size.height * 0.23f)
    quadraticTo(size.width, size.height * 0.47f, size.width * 0.92f, size.height * 0.73f)
    quadraticTo(size.width * 0.75f, size.height, size.width * 0.48f, size.height * 0.97f)
    quadraticTo(size.width * 0.20f, size.height, size.width * 0.05f, size.height * 0.72f)
    quadraticTo(-size.width * 0.01f, size.height * 0.46f, size.width * 0.09f, size.height * 0.20f)
    quadraticTo(size.width * 0.26f, 0f, size.width * 0.50f, size.height * 0.01f)
    close()
}

@Composable
fun AnalysisScreen(
    conversations: List<Conversation>,
    stats: StatsResponse?,
    insights: InsightsResponse?,
    articles: List<SavedArticle>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenArticles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val localMessages = conversations.sumOf { it.messages.size }
    val topics = articleTopicDistribution(articles)
    val domains = stats?.domains.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JournalPaper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .padding(bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AnalysisHeader(onOpenArticles = onOpenArticles)

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = JournalTerra)
            }
        }

        errorMessage?.let { AnalysisError(message = it) }

        SmartInsightCard(insights = insights)
        TopicPreferenceCard(topics = topics)
        KnowledgeProfileCard(
            articles = articles,
            topics = topics,
            domains = domains,
            localMessages = localMessages
        )
        ContentDepthCard(articles = articles)
        SourceDomainsCard(domains = domains)
    }
}

@Composable
private fun AnalysisHeader(onOpenArticles: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "分析手记",
                style = MaterialTheme.typography.headlineMedium,
                color = JournalInk,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = stringResource(R.string.knowledge_distribution_subtitle),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = JournalInkLight,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(2.dp, CircleShape, clip = false)
                .background(Color.White.copy(alpha = 0.78f), CircleShape)
                .border(1.dp, JournalLine, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onOpenArticles),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = stringResource(R.string.saved_articles),
                tint = JournalInkLight,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SmartInsightCard(insights: InsightsResponse?) {
    JournalPanel(
        title = stringResource(R.string.smart_insight),
        icon = Icons.Filled.AutoAwesome,
        iconTint = JournalTerra,
        background = Color.White,
        showGrid = true,
        showTape = true
    ) {
        if (insights == null || insights.summary.isBlank()) {
            JournalBodyText(stringResource(R.string.smart_insight_empty))
            return@JournalPanel
        }

        Text(
            text = insights.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = JournalInk,
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.12f
        )
        Text(
            text = if (insights.source == "llm") {
                stringResource(R.string.smart_insight_source_llm)
            } else {
                stringResource(R.string.smart_insight_source_fallback)
            },
            modifier = Modifier.align(Alignment.End).rotate(-2f),
            style = MaterialTheme.typography.labelMedium,
            color = JournalInkLight,
            fontFamily = KalamFontFamily,
            fontWeight = FontWeight.Bold
        )
        InsightBullets(
            title = stringResource(R.string.highlights),
            items = insights.highlights,
            tint = JournalBlue
        )
        InsightBullets(
            title = stringResource(R.string.suggestions),
            items = insights.suggestions,
            tint = JournalGreen
        )
    }
}

@Composable
private fun InsightBullets(title: String, items: List<String>, tint: Color) {
    val cleanItems = items.filter { it.isNotBlank() }
    if (cleanItems.isEmpty()) return

    Text(
        text = title,
        modifier = Modifier
            .background(tint.copy(alpha = 0.34f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = JournalInk,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.labelLarge
    )
    cleanItems.forEach { item ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(tint, CircleShape)
            )
            Text(
                text = item,
                modifier = Modifier.weight(1f),
                color = JournalInkLight,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun TopicPreferenceCard(topics: List<TopicDistribution>) {
    JournalPanel(
        title = stringResource(R.string.topic_share),
        icon = Icons.Filled.Palette,
        iconTint = JournalBlue,
        background = JournalPaper,
        dashedBorder = true,
        trailing = "Last 30 days"
    ) {
        if (topics.isEmpty()) {
            JournalBodyText(stringResource(R.string.no_topic_stats))
        } else {
            TopicDonutChart(topics = topics)
            TopicLegend(topics = topics)
        }
    }
}

@Composable
private fun TopicDonutChart(topics: List<TopicDistribution>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(202.dp)
                .offset(x = 3.dp, y = 7.dp)
                .graphicsLayer {
                    shape = AnalysisBlobShape
                    clip = true
                }
                .background(JournalInk.copy(alpha = 0.07f))
        )
        Canvas(
            modifier = Modifier
                .size(202.dp)
                .rotate(-3f)
                .graphicsLayer {
                    shape = AnalysisBlobShape
                    clip = true
                }
        ) {
            val strokeWidth = 49.dp.toPx()
            val inset = strokeWidth / 2f + 3.dp.toPx()
            val diameter = min(size.width, size.height) - inset * 2f
            var startAngle = -105f
            topics.filter { it.percentage > 0.0 }.forEachIndexed { index, topic ->
                val sweep = (topic.percentage / 100.0 * 360.0).toFloat()
                drawArc(
                    color = topicColor(index),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Column(
            modifier = Modifier
                .size(94.dp)
                .rotate(2f)
                .graphicsLayer {
                    shape = AnalysisBlobShape
                    clip = true
                }
                .background(JournalPaper)
                .border(2.dp, JournalLine.copy(alpha = 0.55f), CircleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = topics.size.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = JournalInk,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "TOPICS",
                style = MaterialTheme.typography.labelSmall,
                color = JournalInkLight,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = JournalYellow,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-25).dp, y = 12.dp)
                .rotate(12f)
                .size(25.dp)
        )
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = JournalPink,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 28.dp, y = (-14).dp)
                .rotate(-16f)
                .size(16.dp)
        )
    }
}

@Composable
private fun TopicLegend(topics: List<TopicDistribution>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        topics.take(8).chunked(2).forEach { rowTopics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                rowTopics.forEach { topic ->
                    val index = topics.indexOf(topic)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .border(1.dp, JournalLine.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(topicColor(index), CircleShape)
                        )
                        Text(
                            text = "${topic.topic} ${topic.percentage}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = JournalInk,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowTopics.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KnowledgeProfileCard(
    articles: List<SavedArticle>,
    topics: List<TopicDistribution>,
    domains: List<DomainDistribution>,
    localMessages: Int
) {
    JournalPanel(
        title = stringResource(R.string.knowledge_profile),
        icon = Icons.Filled.AccountCircle,
        iconTint = JournalGreen,
        background = Color(0xFFE8F0EB)
    ) {
        if (articles.isEmpty()) {
            JournalBodyText(stringResource(R.string.knowledge_profile_empty))
            JournalBodyText(stringResource(R.string.knowledge_suggestion_empty))
            return@JournalPanel
        }

        val topTopic = topics.maxByOrNull { it.percentage }
        val topDomain = domains.maxByOrNull { it.percentage }
        ProfileLine(stringResource(R.string.knowledge_profile_saved_articles, articles.size, topics.size))
        topTopic?.let {
            ProfileLine(stringResource(R.string.knowledge_profile_top_topic, it.topic, it.percentage.toString()))
        }
        topDomain?.takeIf { it.domain.isNotBlank() }?.let {
            ProfileLine(stringResource(R.string.knowledge_profile_top_domain, it.domain))
        }
        ProfileLine(stringResource(R.string.knowledge_profile_conversations, localMessages))
        Text(
            text = stringResource(R.string.knowledge_suggestion),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.68f), RoundedCornerShape(7.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            color = JournalInk,
            fontWeight = FontWeight.ExtraBold
        )
        JournalBodyText(
            if (topics.size <= 1) {
                stringResource(R.string.knowledge_suggestion_single_topic)
            } else {
                stringResource(R.string.knowledge_suggestion_multi_topic)
            }
        )
    }
}

@Composable
private fun ProfileLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .background(JournalGreen, CircleShape)
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = JournalInk,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ContentDepthCard(articles: List<SavedArticle>) {
    val topArticles = articles
        .filter { it.chunkCount > 0 }
        .sortedByDescending { it.chunkCount }
        .take(3)

    JournalPanel(
        title = stringResource(R.string.content_depth),
        icon = Icons.Filled.Layers,
        iconTint = JournalTerra,
        background = Color(0xFFF8F0D8)
    ) {
        if (topArticles.isEmpty()) {
            JournalBodyText(stringResource(R.string.content_depth_empty))
        } else {
            topArticles.forEachIndexed { index, article ->
                ContentDepthItem(article = article, rank = index + 1)
            }
            JournalBodyText(stringResource(R.string.content_depth_hint))
        }
    }
}

@Composable
private fun ContentDepthItem(article: SavedArticle, rank: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .border(1.dp, JournalYellow, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(JournalTerra.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                color = JournalInk,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title.ifBlank { article.domain.ifBlank { article.url } },
                color = JournalInk,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${article.chunkCount} ${stringResource(R.string.chunk_count)}",
                style = MaterialTheme.typography.labelMedium,
                color = JournalInkLight
            )
        }
    }
}

@Composable
private fun SourceDomainsCard(domains: List<DomainDistribution>) {
    JournalPanel(
        title = stringResource(R.string.source_domains),
        icon = Icons.Filled.Language,
        iconTint = JournalPink,
        background = Color(0xFFF3E8EC)
    ) {
        if (domains.isEmpty()) {
            JournalBodyText(stringResource(R.string.no_source_domains))
        } else {
            domains.take(6).forEach { domain -> DomainRow(domain) }
        }
    }
}

@Composable
private fun DomainRow(domain: DomainDistribution) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = domain.domain,
                modifier = Modifier.weight(1f),
                color = JournalInk,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${domain.percentage}%",
                color = JournalInkLight,
                fontFamily = KalamFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Color.White.copy(alpha = 0.68f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((domain.percentage / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(JournalPink, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun AnalysisError(message: String) {
    JournalPanel(
        title = stringResource(R.string.stats_error),
        icon = Icons.Filled.EditNote,
        iconTint = JournalTerra,
        background = Color(0xFFF6E5E2)
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun JournalPanel(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    dashedBorder: Boolean = false,
    showGrid: Boolean = false,
    showTape: Boolean = false,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (dashedBorder) 0.dp else 4.dp, shape, clip = false)
                .clip(shape)
                .background(background)
                .drawBehind {
                    if (showGrid) {
                        val spacing = 20.dp.toPx()
                        var x = spacing
                        while (x < size.width) {
                            drawLine(JournalInkLight.copy(alpha = 0.09f), Offset(x, 0f), Offset(x, size.height), 1f)
                            x += spacing
                        }
                        var y = spacing
                        while (y < size.height) {
                            drawLine(JournalInkLight.copy(alpha = 0.09f), Offset(0f, y), Offset(size.width, y), 1f)
                            y += spacing
                        }
                    }
                    if (dashedBorder) {
                        drawRoundRect(
                            color = JournalLine,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
                            )
                        )
                    }
                }
                .then(
                    if (!dashedBorder) Modifier.border(1.dp, JournalLine.copy(alpha = 0.62f), shape)
                    else Modifier
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(23.dp))
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 9.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = JournalInk,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.weight(1f))
                trailing?.let {
                    Text(
                        text = it,
                        color = JournalInkLight,
                        fontFamily = KalamFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            content()
        }
        if (showTape) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-9).dp)
                    .rotate(1.5f)
                    .size(width = 76.dp, height = 20.dp)
                    .background(JournalYellow.copy(alpha = 0.68f), RoundedCornerShape(2.dp))
                    .border(1.dp, JournalYellow.copy(alpha = 0.82f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun JournalBodyText(text: String) {
    Text(
        text = text,
        color = JournalInkLight,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun articleTopicDistribution(articles: List<SavedArticle>): List<TopicDistribution> {
    if (articles.isEmpty()) return emptyList()
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
        errorMessage = null
    )
}
