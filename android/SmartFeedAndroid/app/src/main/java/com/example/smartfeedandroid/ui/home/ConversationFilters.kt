package com.example.smartfeedandroid.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.smartfeedandroid.R
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation

internal val topicOrder = listOf("科技", "学习", "健康", "职业", "财经", "生活", "新闻", "新聊天", "其他")

internal sealed interface ConversationFilter {
    data object All : ConversationFilter
    data object NewChat : ConversationFilter
    data object Page : ConversationFilter
    data class Topic(val topic: String) : ConversationFilter

    fun matches(conversation: Conversation): Boolean {
        return when (this) {
            All -> true
            NewChat -> conversationSourceUrl(conversation).isBlank()
            Page -> conversationSourceUrl(conversation).isNotBlank()
            is Topic -> conversationTopic(conversation) == topic
        }
    }
}

@Composable
internal fun ConversationFilter.label(): String {
    return when (this) {
        ConversationFilter.All -> stringResource(R.string.conversation_filter_all)
        ConversationFilter.NewChat -> stringResource(R.string.new_chat)
        ConversationFilter.Page -> stringResource(R.string.conversation_filter_page)
        is ConversationFilter.Topic -> topic
    }
}

internal fun conversationFilters(conversations: List<Conversation>): List<ConversationFilter> {
    val topicFilters = conversations
        .map { conversationTopic(it) }
        .distinct()
        .filterNot { it == "新聊天" }
        .sortedWith(
            compareBy<String> { topic ->
                topicOrder.indexOf(topic).takeIf { it >= 0 } ?: topicOrder.size
            }.thenBy { it }
        )
        .map { ConversationFilter.Topic(it) }

    return listOf(
        ConversationFilter.All,
        ConversationFilter.NewChat,
        ConversationFilter.Page,
    ) + topicFilters
}

internal fun Conversation.matchesSearch(query: String): Boolean {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isBlank()) {
        return true
    }

    val messageText = messages
        .takeLast(6)
        .joinToString(" ") { message ->
            when (message) {
                is ChatMessage.User -> message.text
                is ChatMessage.Summary -> message.text
                is ChatMessage.Assistant -> message.response.answer.ifBlank {
                    message.response.message
                }
                is ChatMessage.Error -> message.text
            }
        }

    val searchable = listOf(
        title,
        summary,
        conversationSourceUrl(this),
        status,
        conversationTopic(this),
        messageText,
    ).joinToString(" ").lowercase()

    return cleanQuery in searchable
}

internal fun conversationTopic(conversation: Conversation): String {
    val sourceUrl = conversationSourceUrl(conversation)
    if (sourceUrl.isBlank()) {
        return "新聊天"
    }

    conversation.topic.takeIf { it.isNotBlank() }?.let { return it }

    val text = listOf(conversation.title, conversation.summary, sourceUrl)
        .joinToString(" ")
        .lowercase()

    val scores = mapOf(
        "科技" to listOf("ai", "人工智能", "算法", "编程", "代码", "软件", "数据", "模型", "rag", "android", "kotlin", "python", "开发", "技术"),
        "学习" to listOf("学习", "课程", "考试", "高考", "教育", "笔记", "教程", "方法", "总结", "复习"),
        "健康" to listOf("健康", "医生", "疾病", "中毒", "症状", "治疗", "医院", "睡眠", "心理", "饮食"),
        "职业" to listOf("职业", "实习", "面试", "招聘", "简历", "工作", "岗位", "职场", "薪资"),
        "财经" to listOf("财经", "股票", "基金", "投资", "价格", "汇率", "美元", "经济", "市场", "公司"),
        "生活" to listOf("生活", "旅行", "美食", "家庭", "情感", "娱乐", "消费", "家长", "孩子"),
        "新闻" to listOf("新闻", "央视新闻", "新华社", "人民日报", "中新网", "通报", "政策", "社会", "近日", "报道称")
    )

    return scores
        .mapValues { (_, keywords) -> keywords.count { keyword -> keyword.lowercase() in text } }
        .maxByOrNull { it.value }
        ?.takeIf { it.value > 0 }
        ?.key
        ?: "其他"
}

internal fun conversationSourceUrl(conversation: Conversation): String {
    return conversation.sourceUrl.ifBlank { conversation.url }
}
