package com.example.smartfeedandroid.ui.home

import com.example.smartfeedandroid.ui.model.ChatMessage
import com.example.smartfeedandroid.ui.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationManagerTest {
    private val manager = ConversationManager()

    @Test
    fun createArticleConversationPersistsNormalizedMetadata() {
        val conversation = manager.createArticleConversation(
            url = "  https://example.com/article  ",
            title = "",
            topic = "",
            storedChunks = 6
        )

        assertEquals("https://example.com/article", conversation.url)
        assertEquals(conversation.url, conversation.sourceUrl)
        assertEquals(conversation.url, conversation.title)
        assertEquals("其他", conversation.topic)
        assertEquals("saved", conversation.status)
        assertEquals(6, conversation.storedChunks)
        assertEquals(conversation.createdAtMillis, conversation.updatedAtMillis)
    }

    @Test
    fun upsertUploadedConversationMatchesNormalizedArticleUrl() {
        val existing = conversation(
            id = "existing",
            url = "https://www.example.com/article/",
            messages = listOf(
                ChatMessage.Summary("旧摘要"),
                ChatMessage.User("继续解释")
            )
        )

        val conversations = manager.upsertUploadedConversation(
            conversations = listOf(existing),
            url = "https://example.com/article",
            title = "更新后的文章",
            summary = "新摘要",
            status = "saved",
            topic = "技术",
            storedChunks = 9
        )

        assertEquals(1, conversations.size)
        assertEquals("existing", conversations.single().id)
        assertEquals("更新后的文章", conversations.single().title)
        assertEquals(
            listOf(ChatMessage.Summary("新摘要"), ChatMessage.User("继续解释")),
            conversations.single().messages
        )
    }

    @Test
    fun upsertUploadedConversationCreatesNewConversationAtTop() {
        val existing = conversation(id = "existing", url = "https://example.com/old")

        val conversations = manager.upsertUploadedConversation(
            conversations = listOf(existing),
            url = "https://example.com/new",
            title = "新文章",
            summary = "文章摘要",
            status = "saved",
            topic = "产品",
            storedChunks = 4
        )

        assertEquals(2, conversations.size)
        assertNotEquals("existing", conversations.first().id)
        assertEquals("https://example.com/new", conversations.first().sourceUrl)
        assertEquals(listOf(ChatMessage.Summary("文章摘要")), conversations.first().messages)
        assertEquals("existing", conversations.last().id)
    }

    @Test
    fun appendMessageUpdatesOnlySelectedConversation() {
        val selected = conversation(id = "selected", url = "")
        val untouched = conversation(id = "untouched", url = "")
        val beforeUpdate = selected.updatedAtMillis

        val conversations = manager.appendMessage(
            conversations = listOf(selected, untouched),
            conversationId = "selected",
            message = ChatMessage.User("问题")
        )

        assertEquals(listOf(ChatMessage.User("问题")), conversations.first().messages)
        assertTrue(conversations.first().updatedAtMillis >= beforeUpdate)
        assertEquals(untouched, conversations.last())
    }

    private fun conversation(
        id: String,
        url: String,
        messages: List<ChatMessage> = emptyList()
    ): Conversation {
        return Conversation(
            id = id,
            title = id,
            url = url,
            sourceUrl = url,
            updatedAtMillis = 100,
            createdAtMillis = 50,
            messages = messages
        )
    }
}
