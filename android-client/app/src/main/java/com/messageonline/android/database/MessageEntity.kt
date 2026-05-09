package com.messageonline.android.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.messageonline.android.model.ChatMessage

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: Int = 0,
    val senderUsername: String = "",
    val receiverId: Int = 0,
    val receiverUsername: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val isGlobal: Boolean = true,
    val status: Int = ChatMessage.STATUS_SENT,
    val replyToSender: String = "",
    val replyToContent: String = "",
    val isRead: Boolean = false,
    val isEdited: Boolean = false
) {
    fun toChatMessage() = ChatMessage(
        senderId = senderId,
        senderUsername = senderUsername,
        receiverId = if (receiverId == 0) null else receiverId,
        receiverUsername = receiverUsername.ifEmpty { null },
        content = content,
        timestamp = timestamp,
        isGlobal = isGlobal,
        status = status,
        replyToSender = replyToSender,
        replyToContent = replyToContent,
        isEdited = isEdited
    )

    companion object {
        fun from(msg: ChatMessage) = MessageEntity(
            senderId = msg.senderId,
            senderUsername = msg.senderUsername,
            receiverId = msg.receiverId ?: 0,
            receiverUsername = msg.receiverUsername ?: "",
            content = msg.content,
            timestamp = msg.timestamp,
            isGlobal = msg.isGlobal,
            status = if (msg.status == ChatMessage.STATUS_PENDING) ChatMessage.STATUS_SENT
                     else msg.status,
            replyToSender = msg.replyToSender,
            replyToContent = msg.replyToContent,
            isEdited = msg.isEdited
        )
    }
}
