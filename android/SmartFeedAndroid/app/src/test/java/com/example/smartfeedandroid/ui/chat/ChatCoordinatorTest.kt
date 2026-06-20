package com.example.smartfeedandroid.ui.chat

import com.example.smartfeedandroid.data.remote.ChatHistoryItem
import com.example.smartfeedandroid.data.remote.ChatResponse
import com.example.smartfeedandroid.ui.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCoordinatorTest {
    @Test
    fun chatHistoryMapsSupportedMessagesAndSkipsErrors() {
        val messages = listOf(
            ChatMessage.Summary("文章摘要"),
            ChatMessage.User("用户问题"),
            ChatMessage.Assistant(ChatResponse(answer = "助手回答")),
            ChatMessage.Assistant(ChatResponse(message = "fallback message")),
            ChatMessage.Assistant(ChatResponse()),
            ChatMessage.Error("网络错误")
        )

        val history = chatHistoryFrom(messages)

        assertEquals(
            listOf(
                ChatHistoryItem("summary", "文章摘要"),
                ChatHistoryItem("user", "用户问题"),
                ChatHistoryItem("assistant", "助手回答"),
                ChatHistoryItem("assistant", "fallback message")
            ),
            history
        )
    }

    @Test
    fun chatHistoryKeepsOnlyEightMostRecentSupportedMessages() {
        val messages = (1..10).map { ChatMessage.User("问题 $it") }

        val history = chatHistoryFrom(messages)

        assertEquals(8, history.size)
        assertEquals(ChatHistoryItem("user", "问题 3"), history.first())
        assertEquals(ChatHistoryItem("user", "问题 10"), history.last())
    }
}
