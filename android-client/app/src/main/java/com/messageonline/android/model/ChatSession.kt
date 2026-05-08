package com.messageonline.android.model

object ChatSession {
    var userId: Int = -1
    var username: String = ""

    fun login(userId: Int, username: String) {
        this.userId = userId
        this.username = username
    }

    fun logout() {
        userId = -1
        username = ""
    }

    fun isLoggedIn(): Boolean = userId > 0 && username.isNotBlank()
}
