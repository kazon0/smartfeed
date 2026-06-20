package com.example.smartfeedandroid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE ownerId = :ownerId ORDER BY updatedAtMillis DESC")
    suspend fun getAll(ownerId: String): List<ConversationEntity>

    @Query("UPDATE conversations SET ownerId = :ownerId WHERE ownerId = ''")
    suspend fun claimUnowned(ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE ownerId = :ownerId")
    suspend fun deleteByOwner(ownerId: String)
}
