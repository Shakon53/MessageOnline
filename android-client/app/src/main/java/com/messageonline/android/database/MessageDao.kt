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

    @Query("DELETE FROM messages WHERE timestamp = :ts AND senderUsername = :sender")
    suspend fun deleteByTimestampAndSender(ts: Long, sender: String)

    @Query("SELECT * FROM messages WHERE isGlobal = 0 ORDER BY timestamp DESC LIMIT 1000")
    suspend fun getAllPrivateMessages(): List<MessageEntity>

    /** Count unread messages from a specific peer (messages sent to me by them, not yet read) */
    @Query("""
        SELECT COUNT(*) FROM messages
        WHERE isGlobal = 0
          AND senderUsername = :peerUsername
          AND receiverUsername = :myUsername
          AND isRead = 0
    """)
    suspend fun getUnreadCount(peerUsername: String, myUsername: String): Int

    /** Mark all messages from peer as read */
    @Query("""
        UPDATE messages SET isRead = 1
        WHERE isGlobal = 0
          AND senderUsername = :peerUsername
          AND receiverUsername = :myUsername
    """)
    suspend fun markConversationRead(peerUsername: String, myUsername: String)

    /** Update message content (for editing) */
    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE timestamp = :timestamp AND senderUsername = :sender")
    suspend fun updateMessageContent(timestamp: Long, sender: String, newContent: String)
}
