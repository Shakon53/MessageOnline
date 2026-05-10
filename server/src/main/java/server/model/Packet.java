package server.model;

import org.json.JSONObject;

/**
 * Пакет сетевого протокола.
 * Все сообщения между клиентом и сервером передаются в формате JSON.
 *
 * Формат: {"type":"ТИП_ПАКЕТА", ...остальные поля...}
 *
 * Типы пакетов:
 * CLIENT -> SERVER:
 *   REGISTER, LOGIN, LOGOUT,
 *   GLOBAL_MESSAGE, PRIVATE_MESSAGE,
 *   GET_HISTORY, GET_PRIVATE_HISTORY, GET_USERS
 *
 * SERVER -> CLIENT:
 *   REGISTER_SUCCESS, REGISTER_FAIL,
 *   LOGIN_SUCCESS, LOGIN_FAIL,
 *   GLOBAL_MESSAGE, PRIVATE_MESSAGE,
 *   USER_LIST, USER_JOINED, USER_LEFT,
 *   HISTORY_RESPONSE, ERROR, NOTIFICATION
 */
public class Packet {

    // ===================== ТИПЫ ПАКЕТОВ =====================
    public static final String REGISTER          = "REGISTER";
    public static final String LOGIN             = "LOGIN";
    public static final String LOGOUT            = "LOGOUT";

    public static final String REGISTER_SUCCESS  = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL     = "REGISTER_FAIL";
    public static final String LOGIN_SUCCESS     = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL        = "LOGIN_FAIL";

    public static final String GLOBAL_MESSAGE    = "GLOBAL_MESSAGE";
    public static final String PRIVATE_MESSAGE   = "PRIVATE_MESSAGE";

    public static final String GET_HISTORY       = "GET_HISTORY";
    public static final String GET_PRIVATE_HISTORY = "GET_PRIVATE_HISTORY";
    public static final String HISTORY_RESPONSE  = "HISTORY_RESPONSE";

    public static final String GET_USERS         = "GET_USERS";
    public static final String USER_LIST         = "USER_LIST";
    public static final String USER_JOINED       = "USER_JOINED";
    public static final String USER_LEFT         = "USER_LEFT";

    public static final String ERROR             = "ERROR";
    public static final String NOTIFICATION      = "NOTIFICATION";

    public static final String TYPING           = "TYPING";
    public static final String UPDATE_PROFILE   = "UPDATE_PROFILE";
    public static final String PROFILE_UPDATED  = "PROFILE_UPDATED";

    public static final String FCM_TOKEN        = "FCM_TOKEN";

    // Read receipts
    public static final String MARK_READ        = "MARK_READ";
    public static final String MESSAGE_READ     = "MESSAGE_READ";

    // Editing & avatars
    public static final String EDIT_MESSAGE     = "EDIT_MESSAGE";
    public static final String EDITED_MESSAGE   = "EDITED_MESSAGE";
    public static final String UPDATE_AVATAR    = "UPDATE_AVATAR";

    // Delete for all
    public static final String DELETE_FOR_ALL    = "DELETE_FOR_ALL";
    public static final String MESSAGE_DELETED   = "MESSAGE_DELETED";

    // Username change
    public static final String CHANGE_USERNAME         = "CHANGE_USERNAME";
    public static final String USERNAME_CHANGE_SUCCESS = "USERNAME_CHANGE_SUCCESS";
    public static final String USERNAME_CHANGE_FAIL    = "USERNAME_CHANGE_FAIL";
    public static final String USERNAME_CHANGED        = "USERNAME_CHANGED";

    // Privacy settings
    public static final String UPDATE_PRIVACY   = "UPDATE_PRIVACY";
    public static final String PRIVACY_REJECTED = "PRIVACY_REJECTED";

    // Friends
    public static final String FRIEND_ADD        = "FRIEND_ADD";
    public static final String FRIEND_REQUEST_IN = "FRIEND_REQUEST_IN";
    public static final String FRIEND_ACCEPT     = "FRIEND_ACCEPT";
    public static final String FRIEND_DECLINE    = "FRIEND_DECLINE";
    public static final String FRIEND_ACCEPTED   = "FRIEND_ACCEPTED";
    public static final String FRIEND_REMOVE     = "FRIEND_REMOVE";
    public static final String FRIEND_REMOVED    = "FRIEND_REMOVED";
    public static final String GET_FRIENDS       = "GET_FRIENDS";
    public static final String FRIENDS_LIST      = "FRIENDS_LIST";

    // Admin panel
    public static final String ADMIN_LOGIN         = "ADMIN_LOGIN";
    public static final String ADMIN_LOGIN_SUCCESS = "ADMIN_LOGIN_SUCCESS";
    public static final String ADMIN_LOGIN_FAIL    = "ADMIN_LOGIN_FAIL";
    public static final String ADMIN_EVENT         = "ADMIN_EVENT";
    public static final String ADMIN_STATS         = "ADMIN_STATS";

    // ===================== ФАБРИЧНЫЕ МЕТОДЫ =====================

    /** Успешная регистрация */
    public static String registerSuccess(int userId, String username) {
        return new JSONObject()
                .put("type", REGISTER_SUCCESS)
                .put("userId", userId)
                .put("username", username)
                .toString();
    }

    /** Ошибка регистрации */
    public static String registerFail(String reason) {
        return new JSONObject()
                .put("type", REGISTER_FAIL)
                .put("message", reason)
                .toString();
    }

    /** Успешный вход */
    public static String loginSuccess(int userId, String username) {
        return loginSuccess(userId, username, null, null, 0L);
    }

    /** Успешный вход с профилем */
    public static String loginSuccess(int userId, String username, String phone, String statusText, long createdAt) {
        JSONObject obj = new JSONObject()
                .put("type", LOGIN_SUCCESS)
                .put("userId", userId)
                .put("username", username)
                .put("createdAt", createdAt);
        if (phone != null) obj.put("phone", phone);
        if (statusText != null) obj.put("statusText", statusText);
        return obj.toString();
    }

    /** Ошибка входа */
    public static String loginFail(String reason) {
        return new JSONObject()
                .put("type", LOGIN_FAIL)
                .put("message", reason)
                .toString();
    }

    /** Глобальное сообщение */
    public static String globalMessage(Message msg) {
        return new JSONObject()
                .put("type", GLOBAL_MESSAGE)
                .put("senderId", msg.getSenderId())
                .put("senderUsername", msg.getSenderUsername())
                .put("content", msg.getContent())
                .put("timestamp", msg.getTimestamp())
                .put("messageType", msg.getMessageType())
                .toString();
    }

    /** Личное сообщение */
    public static String privateMessage(Message msg) {
        return new JSONObject()
                .put("type", PRIVATE_MESSAGE)
                .put("senderId", msg.getSenderId())
                .put("senderUsername", msg.getSenderUsername())
                .put("receiverId", msg.getReceiverId())
                .put("receiverUsername", msg.getReceiverUsername())
                .put("content", msg.getContent())
                .put("timestamp", msg.getTimestamp())
                .put("messageType", msg.getMessageType())
                .toString();
    }

    /** Сообщение удалено */
    public static String messageDeleted(String senderUsername, long timestamp, boolean isGlobal, String receiverUsername) {
        JSONObject obj = new JSONObject()
                .put("type", MESSAGE_DELETED)
                .put("senderUsername", senderUsername)
                .put("timestamp", timestamp)
                .put("isGlobal", isGlobal);
        if (receiverUsername != null && !receiverUsername.isEmpty()) obj.put("receiverUsername", receiverUsername);
        return obj.toString();
    }

    /** Уведомление о подключении пользователя */
    public static String userJoined(int userId, String username) {
        return new JSONObject()
                .put("type", USER_JOINED)
                .put("userId", userId)
                .put("username", username)
                .toString();
    }

    /** Уведомление об отключении пользователя */
    public static String userLeft(int userId, String username) {
        return new JSONObject()
                .put("type", USER_LEFT)
                .put("userId", userId)
                .put("username", username)
                .toString();
    }

    /** Сообщение об ошибке */
    public static String error(String message) {
        return new JSONObject()
                .put("type", ERROR)
                .put("message", message)
                .toString();
    }

    /** Push-уведомление */
    public static String notification(String content) {
        return new JSONObject()
                .put("type", NOTIFICATION)
                .put("content", content)
                .toString();
    }

    /** Индикатор печатает */
    public static String typing(String senderUsername, boolean isTyping) {
        return new JSONObject()
                .put("type", TYPING)
                .put("senderUsername", senderUsername)
                .put("isTyping", isTyping)
                .toString();
    }

    /** Профиль обновлён */
    public static String profileUpdated(int userId, String username, String statusText) {
        return new JSONObject()
                .put("type", PROFILE_UPDATED)
                .put("userId", userId)
                .put("username", username)
                .put("statusText", statusText)
                .toString();
    }

    /** Сообщение отредактировано */
    public static String editedMessage(String senderUsername, long timestamp, String newContent, boolean isGlobal, String receiverUsername) {
        JSONObject obj = new JSONObject()
                .put("type", EDITED_MESSAGE)
                .put("senderUsername", senderUsername)
                .put("timestamp", timestamp)
                .put("newContent", newContent)
                .put("isGlobal", isGlobal);
        if (receiverUsername != null && !receiverUsername.isEmpty()) obj.put("receiverUsername", receiverUsername);
        return obj.toString();
    }

    public static String friendRequestIn(int fromId, String fromUsername, String fromStatusText, String fromAvatarUrl) {
        return new JSONObject()
                .put("type", FRIEND_REQUEST_IN)
                .put("fromUserId", fromId)
                .put("fromUsername", fromUsername)
                .put("fromStatusText", fromStatusText)
                .put("fromAvatarUrl", fromAvatarUrl)
                .toString();
    }

    public static String friendAccepted(int userId, String username, String statusText, String avatarUrl) {
        return new JSONObject()
                .put("type", FRIEND_ACCEPTED)
                .put("userId", userId)
                .put("username", username)
                .put("statusText", statusText)
                .put("avatarUrl", avatarUrl)
                .toString();
    }

    public static String friendRemoved(int userId, String username) {
        return new JSONObject()
                .put("type", FRIEND_REMOVED)
                .put("userId", userId)
                .put("username", username)
                .toString();
    }
}
