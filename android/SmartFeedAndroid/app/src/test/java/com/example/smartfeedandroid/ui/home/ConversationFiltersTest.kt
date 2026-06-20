package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationFiltersTest {
    @Test
    fun conversationTopicUsesSavedTopicBeforeKeywordFallback() {
        val conversation = conversation(
            sourceUrl = "https://example.com/android",
            topic = "职业",
            title = "Android Kotlin 开发教程"
        )

        assertEquals("职业", conversationTopic(conversation))
    }

    @Test
    fun conversationTopicFallsBackToKeywordsForLegacyConversation() {
        val conversation = conversation(
            sourceUrl = "https://example.com/health",
            title = "野生菌中毒症状和医院治疗建议"
        )

        assertEquals("健康", conversationTopic(conversation))
    }

    @Test
    fun conversationFiltersKeepFixedFiltersThenKnownAndUnknownTopics() {
        val conversations = listOf(
            conversation(sourceUrl = "https://example.com/custom", topic = "自定义"),
            conversation(sourceUrl = "https://example.com/health", topic = "健康"),
            conversation(sourceUrl = "", topic = "")
        )

        val filters = conversationFilters(conversations)

        assertEquals(
            listOf(
                ConversationFilter.All,
                ConversationFilter.NewChat,
                ConversationFilter.Page,
                ConversationFilter.Topic("健康"),
                ConversationFilter.Topic("自定义")
            ),
            filters
        )
        assertTrue(ConversationFilter.NewChat.matches(conversations.last()))
        assertTrue(ConversationFilter.Page.matches(conversations.first()))
    }

    @Test
    fun searchUsesMetadataAndOnlyTheSixMostRecentMessages() {
        val conversation = conversation(
            sourceUrl = "https://example.com/article",
            title = "文章标题",
            messages = listOf(ChatMessage.User("过期关键词")) +
                (1..6).map { ChatMessage.User("最近消息 $it") }
        )

        assertTrue(conversation.matchesSearch("文章标题"))
        assertTrue(conversation.matchesSearch("最近消息 6"))
        assertFalse(conversation.matchesSearch("过期关键词"))
        assertTrue(conversation.matchesSearch("  "))
    }

    private fun conversation(
        sourceUrl: String,
        topic: String = "",
        title: String = "对话",
        messages: List<ChatMessage> = emptyList()
    ): Conversation {
        return Conversation(
            id = "$title-$sourceUrl",
            title = title,
            sourceUrl = sourceUrl,
            topic = topic,
            updatedAtMillis = 100,
            messages = messages
        )
    }
}
