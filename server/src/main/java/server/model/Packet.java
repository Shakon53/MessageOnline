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
        return loginSuccess(userId, username, null, null);
    }

    /** Успешный вход с профилем */
    public static String loginSuccess(int userId, String username, String phone, String statusText) {
        JSONObject obj = new JSONObject()
                .put("type", LOGIN_SUCCESS)
                .put("userId", userId)
                .put("username", username);
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
                .toString();
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
}
