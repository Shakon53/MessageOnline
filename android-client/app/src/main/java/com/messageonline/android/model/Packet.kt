package com.messageonline.android.model

object Packet {
    const val REGISTER              = "REGISTER"
    const val LOGIN                 = "LOGIN"
    const val LOGOUT                = "LOGOUT"

    const val REGISTER_SUCCESS      = "REGISTER_SUCCESS"
    const val REGISTER_FAIL         = "REGISTER_FAIL"
    const val LOGIN_SUCCESS         = "LOGIN_SUCCESS"
    const val LOGIN_FAIL            = "LOGIN_FAIL"

    const val GLOBAL_MESSAGE        = "GLOBAL_MESSAGE"
    const val PRIVATE_MESSAGE       = "PRIVATE_MESSAGE"

    const val GET_HISTORY           = "GET_HISTORY"
    const val GET_PRIVATE_HISTORY   = "GET_PRIVATE_HISTORY"
    const val HISTORY_RESPONSE      = "HISTORY_RESPONSE"

    const val GET_USERS             = "GET_USERS"
    const val USER_LIST             = "USER_LIST"
    const val USER_JOINED           = "USER_JOINED"
    const val USER_LEFT             = "USER_LEFT"

    const val ERROR                 = "ERROR"
    const val NOTIFICATION          = "NOTIFICATION"

    const val TYPING                = "TYPING"
    const val UPDATE_PROFILE        = "UPDATE_PROFILE"
    const val PROFILE_UPDATED       = "PROFILE_UPDATED"

    // FCM push-уведомления
    const val FCM_TOKEN             = "FCM_TOKEN"

    // Read receipts
    const val MARK_READ             = "MARK_READ"
    const val MESSAGE_READ          = "MESSAGE_READ"

    // Editing & avatars
    const val EDIT_MESSAGE          = "EDIT_MESSAGE"
    const val EDITED_MESSAGE        = "EDITED_MESSAGE"
    const val UPDATE_AVATAR         = "UPDATE_AVATAR"

    // Delete for all
    const val DELETE_FOR_ALL    = "DELETE_FOR_ALL"
    const val MESSAGE_DELETED   = "MESSAGE_DELETED"

    // Username change
    const val CHANGE_USERNAME         = "CHANGE_USERNAME"
    const val USERNAME_CHANGE_SUCCESS = "USERNAME_CHANGE_SUCCESS"
    const val USERNAME_CHANGE_FAIL    = "USERNAME_CHANGE_FAIL"
    const val USERNAME_CHANGED        = "USERNAME_CHANGED"

    // Privacy settings
    const val UPDATE_PRIVACY   = "UPDATE_PRIVACY"
    const val PRIVACY_REJECTED = "PRIVACY_REJECTED"

    // Admin broadcast
    const val SYSTEM_MESSAGE    = "SYSTEM_MESSAGE"

    // Friends
    const val FRIEND_ADD        = "FRIEND_ADD"
    const val FRIEND_REQUEST_IN = "FRIEND_REQUEST_IN"
    const val FRIEND_ACCEPT     = "FRIEND_ACCEPT"
    const val FRIEND_DECLINE    = "FRIEND_DECLINE"
    const val FRIEND_ACCEPTED   = "FRIEND_ACCEPTED"
    const val FRIEND_REMOVE     = "FRIEND_REMOVE"
    const val FRIEND_REMOVED    = "FRIEND_REMOVED"
    const val GET_FRIENDS       = "GET_FRIENDS"
    const val FRIENDS_LIST      = "FRIENDS_LIST"
}
