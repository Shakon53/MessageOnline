package com.messageonline.android.model

/**
 * Модель сообщения чата.
 * status: PENDING → SENT → READ
 * localId: временный ID для сопоставления с эхом сервера
 * replyToSender / replyToContent: цитируемое сообщение (reply)
 * isEdited: сообщение было отредактировано
 */
data class ChatMessage(
    val senderId: Int = 0,
    val senderUsername: String = "",
    val receiverId: Int? = null,
    val receiverUsername: String? = null,
    val content: String = "",
    val timestamp: Long = 0L,
    val isGlobal: Boolean = true,
    val status: Int = STATUS_SENT,
    val localId: String = "",
    val replyToSender: String = "",
    val replyToContent: String = "",
    val isEdited: Boolean = false,
    val messageType: String = "text"
) {
    fun isMine(myUsername: String): Boolean = senderUsername == myUsername
    val hasReply: Boolean get() = replyToContent.isNotEmpty()

    companion object {
        const val STATUS_PENDING = 0   // ⏱ отправляется
        const val STATUS_SENT    = 1   // ✓✓ серые — дошло до сервера
        const val STATUS_READ    = 2   // ✓✓ синие — прочитано
    }
}
