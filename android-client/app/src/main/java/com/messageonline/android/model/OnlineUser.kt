package com.messageonline.android.model

/**
 * Модель онлайн-пользователя.
 */
data class OnlineUser(
    val id: Int,
    val username: String,
    val online: Boolean = true
)
