package com.messageonline.android.database

import androidx.room.*

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE isGlobal = 1 ORDER BY timestamp ASC LIMIT 200")
    suspend fun getGlobalMessages(): List<MessageEntity>

    @Query("""
        SELECT * FROM messages
        WHERE isGlobal = 0
          AND ((senderUsername = :user1 AND receiverUsername = :user2)
           OR  (senderUsername = :user2 AND receiverUsername = :user1))
        ORDER BY timestamp ASC LIMIT 200
    """)
    suspend fun getPrivateMessages(user1: String, user2: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(msg: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(msgs: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE isGlobal = 1")
    suspend fun clearGlobal()

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
