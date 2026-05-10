package com.messageonline.android.model

object ChatSession {
    var userId: Int = -1
    var username: String = ""
    var phone: String = ""
    var statusText: String = "Привет, я использую MessageOnline"
    var createdAt: Long = 0L

    fun login(userId: Int, username: String, phone: String = "", statusText: String = "", createdAt: Long = 0L) {
        this.userId = userId
        this.username = username
        if (phone.isNotBlank()) this.phone = phone
        if (statusText.isNotBlank()) this.statusText = statusText
        if (createdAt > 0L) this.createdAt = createdAt
    }

    fun logout() {
        userId = -1
        username = ""
        phone = ""
        statusText = "Привет, я использую MessageOnline"
        createdAt = 0L
    }

    fun isLoggedIn(): Boolean = userId > 0 && username.isNotBlank()
}
