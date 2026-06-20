package com.example.smartfeedandroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    suspend fun getAll(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
