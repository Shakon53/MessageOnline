package com.messageonline.android.model

/**
 * Модель сообщения чата.
 * Используется как в глобальном чате, так и в личных переписках.
 */
data class ChatMessage(
    val senderId: Int,
    val senderUsername: String,
    val receiverId: Int? = null,       // null для глобального чата
    val receiverUsername: String? = null,
    val content: String,
    val timestamp: Long,
    val isGlobal: Boolean = true
) {
    /** Было ли сообщение отправлено мной? */
    fun isMine(myUsername: String): Boolean = senderUsername == myUsername
}
