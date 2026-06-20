package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.data.local.StoredChatMessage
import com.example.smartfeedandroid.data.local.StoredConversation
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.data.remote.ChatSource
import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMappersTest {
    @Test
    fun conversationRoundTripPreservesMetadataAndAllMessageTypes() {
        val response = ChatResponse(
            status = "ok",
            answer = "快速排序通过分区递归处理数据。",
            sources = listOf(
                ChatSource(
                    url = "https://example.com/algorithm",
                    displayTitle = "算法文章",
                    sectionTitle = "快速排序",
                    score = 0.92
                )
            ),
            sourceType = "page",
            intent = "page_question",
            retrievalScope = "page",
            fallbackPolicy = "knowledge_only"
        )
        val conversation = Conversation(
            id = "conversation-1",
            title = "算法文章",
            url = "https://example.com/algorithm",
            sourceUrl = "https://example.com/algorithm",
            summary = "算法摘要",
            status = "saved",
            topic = "技术",
            storedChunks = 8,
            updatedAtMillis = 200,
            createdAtMillis = 100,
            messages = listOf(
                ChatMessage.Summary("算法摘要"),
                ChatMessage.User("快速排序是什么？"),
                ChatMessage.Assistant(response),
                ChatMessage.Error("网络不可用")
            )
        )

        val restored = conversation.toStoredConversation().toConversation()

        assertEquals(conversation, restored)
    }

    @Test
    fun storedConversationFallsBackToUrlWhenSourceUrlIsBlank() {
        val stored = StoredConversation(
            id = "legacy",
            title = "旧文章",
            url = "https://example.com/legacy",
            sourceUrl = "",
            updatedAtMillis = 200,
            createdAtMillis = 100
        )

        val conversation = stored.toConversation()

        assertEquals(stored.url, conversation.sourceUrl)
        assertEquals(stored.createdAtMillis, conversation.createdAtMillis)
    }

    @Test
    fun storedConversationSkipsUnknownLegacyMessageType() {
        val stored = StoredConversation(
            id = "legacy",
            title = "旧对话",
            updatedAtMillis = 100,
            messages = listOf(
                StoredChatMessage(type = "unknown", text = "不可识别"),
                StoredChatMessage(type = "user", text = "保留的问题")
            )
        )

        val conversation = stored.toConversation()

        assertEquals(listOf(ChatMessage.User("保留的问题")), conversation.messages)
    }
}
