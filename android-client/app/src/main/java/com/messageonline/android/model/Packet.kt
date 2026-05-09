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
}
