package com.example.smartfeedandroid.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "''")
    val ownerId: String = "",
    val title: String,
    val url: String,
    @ColumnInfo(defaultValue = "''")
    val sourceUrl: String,
    val summary: String,
    val status: String,
    @ColumnInfo(defaultValue = "''")
    val topic: String,
    val storedChunks: Int,
    @ColumnInfo(defaultValue = "0")
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val messagesJson: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageIndex: Int,
    val type: String,
    val text: String,
    val responseJson: String
)
