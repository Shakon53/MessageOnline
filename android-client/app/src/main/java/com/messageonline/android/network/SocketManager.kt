package com.messageonline.android.network

import android.util.Log
import com.messageonline.android.model.Packet
import okhttp3.*
import okhttp3.Protocol
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SocketManager {

    private const val TAG = "SocketManager"

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    private var webSocket: WebSocket? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onPacketReceived: ((JSONObject) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    var isConnected: Boolean = false
        private set

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    fun connect() {
        disconnect()
        val request = Request.Builder().url(ServerConfig.WS_URL).build()
        webSocket = client.newWebSocket(request, wsListener)
        Log.i(TAG, "Подключение к ${ServerConfig.WS_URL}...")
    }

    fun disconnect() {
        isConnected = false
        webSocket?.close(1000, "Disconnect")
        webSocket = null
    }

    // ==================== ОТПРАВКА ====================

    @Synchronized
    fun send(json: JSONObject) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "Попытка отправки без соединения")
            return
        }
        val text = json.toString()
        webSocket!!.send(text)
        Log.d(TAG, "Отправлен: ${json.optString("type")}")
    }

    // ==================== LISTENER ====================

    private val wsListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnected = true
            Log.i(TAG, "WebSocket подключён")
            onConnected?.invoke()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                Log.d(TAG, "Получен: ${json.optString("type")}")
                onPacketReceived?.invoke(json)
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка парсинга: $text")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnected = false
            Log.i(TAG, "WebSocket закрыт: $reason")
            onDisconnected?.invoke()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnected = false
            val errorMsg = "${t.javaClass.simpleName}: ${t.message}"
            Log.e(TAG, "WebSocket ошибка: $errorMsg")
            onError?.invoke(errorMsg)
        }
    }

    // ==================== ФАБРИЧНЫЕ МЕТОДЫ ====================

    fun sendRegister(username: String, phone: String, password: String) {
        send(JSONObject().apply {
            put("type", Packet.REGISTER)
            put("username", username)
            put("phone", phone)
            put("password", password)
        })
    }

    fun sendLogin(username: String, password: String) {
        send(JSONObject().apply {
            put("type", Packet.LOGIN)
            put("username", username)
            put("password", password)
        })
    }

    fun sendGlobalMessage(content: String, replyToSender: String = "", replyToContent: String = "") {
        send(JSONObject().apply {
            put("type", Packet.GLOBAL_MESSAGE)
            put("content", content)
            if (replyToSender.isNotEmpty()) put("replyToSender", replyToSender)
            if (replyToContent.isNotEmpty()) put("replyToContent", replyToContent)
        })
    }

    fun sendPrivateMessage(receiverUsername: String, content: String, replyToSender: String = "", replyToContent: String = "") {
        send(JSONObject().apply {
            put("type", Packet.PRIVATE_MESSAGE)
            put("receiverUsername", receiverUsername)
            put("content", content)
            if (replyToSender.isNotEmpty()) put("replyToSender", replyToSender)
            if (replyToContent.isNotEmpty()) put("replyToContent", replyToContent)
        })
    }

    fun requestGlobalHistory(limit: Int = 50) {
        send(JSONObject().apply {
            put("type", Packet.GET_HISTORY)
            put("limit", limit)
        })
    }

    fun requestPrivateHistory(otherUsername: String, limit: Int = 50) {
        send(JSONObject().apply {
            put("type", Packet.GET_PRIVATE_HISTORY)
            put("otherUsername", otherUsername)
            put("limit", limit)
        })
    }

    fun requestUserList() {
        send(JSONObject().apply { put("type", Packet.GET_USERS) })
    }

    fun sendLogout() {
        send(JSONObject().apply { put("type", Packet.LOGOUT) })
    }

    fun sendTyping(receiverUsername: String, isTyping: Boolean) {
        send(JSONObject().apply {
            put("type", Packet.TYPING)
            put("receiverUsername", receiverUsername)
            put("isTyping", isTyping)
        })
    }

    fun sendUpdateProfile(statusText: String) {
        send(JSONObject().apply {
            put("type", Packet.UPDATE_PROFILE)
            put("statusText", statusText)
        })
    }

    fun sendMarkRead(peerUsername: String) {
        send(JSONObject().apply {
            put("type", Packet.MARK_READ)
            put("peerUsername", peerUsername)
        })
    }

    fun sendFCMToken(token: String) {
        send(JSONObject().apply {
            put("type", Packet.FCM_TOKEN)
            put("token", token)
        })
    }

    fun sendEditMessage(timestamp: Long, newContent: String, isGlobal: Boolean, receiverUsername: String = "") {
        send(JSONObject().apply {
            put("type", Packet.EDIT_MESSAGE)
            put("timestamp", timestamp)
            put("newContent", newContent)
            put("isGlobal", isGlobal)
            if (receiverUsername.isNotEmpty()) put("receiverUsername", receiverUsername)
        })
    }

    fun sendUpdateAvatar(avatarUrl: String) {
        send(JSONObject().apply {
            put("type", Packet.UPDATE_AVATAR)
            put("avatarUrl", avatarUrl)
        })
    }
}
