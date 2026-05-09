package com.messageonline.android.model

data class Friend(
    val userId: Int,
    val username: String,
    val statusText: String = "",
    val avatarUrl: String = "",
    val isOnline: Boolean = false,
    val isPendingIncoming: Boolean = false   // true = they sent me a request
)
