package com.messageonline.android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingMessageDao {
    @Insert
    suspend fun insert(msg: PendingMessageEntity)

    @Query("SELECT * FROM pending_messages ORDER BY id ASC")
    suspend fun getAll(): List<PendingMessageEntity>

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_messages")
    suspend fun clearAll()
}
