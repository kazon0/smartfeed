package com.example.smartfeedandroid.ui.home

import java.net.URI

class ConversationManager {
    fun createGlobalConversation(): Conversation {
        return Conversation(
            id = createConversationId(),
            title = "新聊天",
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun updateMessages(
        conversations: List<Conversation>,
        conversationId: String,
        messages: List<ChatMessage>
    ): List<Conversation> {
        return conversations.map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    messages = messages,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                conversation
            }
        }
    }

    fun createArticleConversation(
        url: String,
        title: String,
        storedChunks: Int
    ): Conversation {
        val cleanUrl = url.trim()
        return Conversation(
            id = createConversationId(),
            title = title.ifBlank { cleanUrl.ifBlank { "新聊天" } },
            url = cleanUrl,
            status = "saved",
            storedChunks = storedChunks,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun appendMessage(
        conversations: List<Conversation>,
        conversationId: String,
        message: ChatMessage
    ): List<Conversation> {
        return conversations.map { conversation ->
            if (conversation.id == conversationId) {
                conversation.copy(
                    messages = conversation.messages + message,
                    updatedAtMillis = System.currentTimeMillis()
                )
            } else {
                conversation
            }
        }
    }

    fun upsertUploadedConversation(
        conversations: List<Conversation>,
        url: String,
        title: String,
        summary: String,
        status: String,
        storedChunks: Int
    ): List<Conversation> {
        val now = System.currentTimeMillis()
        val existingConversation = conversations.firstOrNull {
            sameArticleUrl(it.url, url)
        }
        val updatedConversation = if (existingConversation == null) {
            Conversation(
                id = createConversationId(),
                title = title,
                url = url,
                summary = summary,
                status = status,
                storedChunks = storedChunks,
                updatedAtMillis = now,
                messages = summaryMessages(summary)
            )
        } else {
            val messagesWithoutOldSummary = existingConversation.messages.filterNot {
                it is ChatMessage.Summary
            }
            existingConversation.copy(
                title = title,
                url = url,
                summary = summary,
                status = status,
                storedChunks = storedChunks,
                updatedAtMillis = now,
                messages = summaryMessages(summary) + messagesWithoutOldSummary
            )
        }

        return listOf(updatedConversation) + conversations.filterNot {
            it.id == updatedConversation.id
        }
    }

    private fun summaryMessages(summary: String): List<ChatMessage> {
        return summary
            .takeIf { it.isNotBlank() }
            ?.let { listOf(ChatMessage.Summary(it)) }
            ?: emptyList()
    }

    private fun sameArticleUrl(left: String, right: String): Boolean {
        return normalizeArticleUrl(left) == normalizeArticleUrl(right)
    }

    private fun normalizeArticleUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return ""
        }

        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
            val path = uri.path.orEmpty().trimEnd('/')
            val query = uri.query?.let { "?$it" }.orEmpty()
            if (scheme.isBlank() || host.isBlank()) {
                trimmed.trimEnd('/')
            } else {
                "$scheme://$host$path$query"
            }
        }.getOrElse {
            trimmed.trimEnd('/')
        }
    }

    private fun createConversationId(): String {
        return "${System.currentTimeMillis()}-${System.nanoTime()}"
    }
}
