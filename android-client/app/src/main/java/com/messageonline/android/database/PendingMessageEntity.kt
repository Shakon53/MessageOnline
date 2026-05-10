package com.messageonline.android.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiverUsername: String = "",
    val content: String = "",
    val messageType: String = "text",
    val isGlobal: Boolean = false,
    val replyToSender: String = "",
    val replyToContent: String = ""
)
