package com.messageonline.android.model

data class Conversation(
    val peerUsername: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val isGlobal: Boolean = false,
    val isOnline: Boolean = false
)
