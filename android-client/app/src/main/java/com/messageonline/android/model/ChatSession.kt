package com.messageonline.android.model

object ChatSession {
    var userId: Int = -1
    var username: String = ""
    var phone: String = ""
    var statusText: String = "Привет, я использую MessageOnline"

    fun login(userId: Int, username: String, phone: String = "", statusText: String = "") {
        this.userId = userId
        this.username = username
        if (phone.isNotBlank()) this.phone = phone
        if (statusText.isNotBlank()) this.statusText = statusText
    }

    fun logout() {
        userId = -1
        username = ""
        phone = ""
        statusText = "Привет, я использую MessageOnline"
    }

    fun isLoggedIn(): Boolean = userId > 0 && username.isNotBlank()
}
