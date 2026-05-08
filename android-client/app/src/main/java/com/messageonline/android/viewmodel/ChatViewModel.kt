package com.messageonline.android.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageonline.android.model.ChatMessage
import com.messageonline.android.model.ChatSession
import com.messageonline.android.model.OnlineUser
import com.messageonline.android.model.Packet
import com.messageonline.android.network.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel для управления состоянием чата.
 *
 * Хранит:
 *  - Список сообщений глобального чата
 *  - Список личных сообщений (по собеседнику)
 *  - Список онлайн-пользователей
 *  - Состояние подключения
 *  - Данные текущего пользователя
 *
 * LiveData позволяет Activity автоматически обновлять UI при изменениях.
 */
class ChatViewModel : ViewModel() {

    // ==================== LIVE DATA ====================

    private val _globalMessages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val globalMessages: LiveData<MutableList<ChatMessage>> = _globalMessages

    private val _privateMessages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val privateMessages: LiveData<MutableList<ChatMessage>> = _privateMessages

    private val _onlineUsers = MutableLiveData<MutableList<OnlineUser>>(mutableListOf())
    val onlineUsers: LiveData<MutableList<OnlineUser>> = _onlineUsers

    private val _connectionStatus = MutableLiveData<ConnectionStatus>(ConnectionStatus.DISCONNECTED)
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

    private val _loginResult = MutableLiveData<AuthResult>()
    val loginResult: LiveData<AuthResult> = _loginResult

    private val _registerResult = MutableLiveData<AuthResult>()
    val registerResult: LiveData<AuthResult> = _registerResult

    private val _notification = MutableLiveData<String>()
    val notification: LiveData<String> = _notification

    // ==================== ТЕКУЩИЙ ПОЛЬЗОВАТЕЛЬ ====================

    var myUserId: Int = -1
        private set
    var myUsername: String = ""
        private set

    // Текущий собеседник для личного чата
    var currentPrivatePeer: String = ""

    // ==================== СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ ====================

    enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    data class AuthResult(val success: Boolean, val message: String = "")

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    init {
        myUserId = ChatSession.userId
        myUsername = ChatSession.username
        setupSocketCallbacks()
    }

    /**
     * Настраивает callback-функции SocketManager.
     * Все входящие пакеты обрабатываются здесь.
     */
    private fun setupSocketCallbacks() {
        SocketManager.onConnected = {
            _connectionStatus.postValue(ConnectionStatus.CONNECTED)
        }

        SocketManager.onDisconnected = {
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED)
        }

        SocketManager.onError = { msg ->
            _connectionStatus.postValue(ConnectionStatus.ERROR)
            _notification.postValue(msg)
        }

        SocketManager.onPacketReceived = { json ->
            handleIncomingPacket(json)
        }
    }

    /**
     * Разбирает входящий пакет и обновляет LiveData.
     */
    private fun handleIncomingPacket(json: JSONObject) {
        when (json.optString("type")) {

            Packet.LOGIN_SUCCESS -> {
                myUserId = json.optInt("userId")
                myUsername = json.optString("username")
                ChatSession.login(myUserId, myUsername)
                _loginResult.postValue(AuthResult(true))
            }

            Packet.LOGIN_FAIL -> {
                _loginResult.postValue(AuthResult(false, json.optString("message")))
            }

            Packet.REGISTER_SUCCESS -> {
                _registerResult.postValue(AuthResult(true))
            }

            Packet.REGISTER_FAIL -> {
                _registerResult.postValue(AuthResult(false, json.optString("message")))
            }

            Packet.GLOBAL_MESSAGE -> {
                val msg = parseGlobalMessage(json)
                val list = _globalMessages.value ?: mutableListOf()
                list.add(msg)
                _globalMessages.postValue(list)
            }

            Packet.PRIVATE_MESSAGE -> {
                val msg = parsePrivateMessage(json)
                val list = _privateMessages.value ?: mutableListOf()
                list.add(msg)
                _privateMessages.postValue(list)
            }

            Packet.USER_LIST -> {
                val users = mutableListOf<OnlineUser>()
                val arr = json.optJSONArray("users") ?: return
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    users.add(OnlineUser(
                        id = u.optInt("id"),
                        username = u.optString("username"),
                        online = u.optBoolean("online", true)
                    ))
                }
                _onlineUsers.postValue(users)
            }

            Packet.USER_JOINED -> {
                val user = OnlineUser(
                    id = json.optInt("userId"),
                    username = json.optString("username")
                )
                val list = _onlineUsers.value ?: mutableListOf()
                if (list.none { it.username == user.username }) {
                    list.add(user)
                    _onlineUsers.postValue(list)
                }
                _notification.postValue("${user.username} присоединился к чату")
            }

            Packet.USER_LEFT -> {
                val username = json.optString("username")
                val list = _onlineUsers.value ?: mutableListOf()
                list.removeAll { it.username == username }
                _onlineUsers.postValue(list)
                _notification.postValue("$username покинул чат")
            }

            Packet.HISTORY_RESPONSE -> {
                val chatType = json.optString("chatType")
                val messagesArr = json.optJSONArray("messages") ?: return
                val messages = mutableListOf<ChatMessage>()

                for (i in 0 until messagesArr.length()) {
                    val m = messagesArr.getJSONObject(i)
                    val isGlobal = m.optBoolean("isGlobal", true)
                    messages.add(
                        if (isGlobal) parseGlobalMessage(m)
                        else parsePrivateMessage(m)
                    )
                }

                if (chatType == "global") {
                    _globalMessages.postValue(messages)
                } else {
                    _privateMessages.postValue(messages)
                }
            }

            Packet.NOTIFICATION -> {
                _notification.postValue(json.optString("content"))
            }

            Packet.ERROR -> {
                _notification.postValue(json.optString("message"))
            }
        }
    }

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    fun connect(host: String, port: Int) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        SocketManager.serverHost = host
        SocketManager.serverPort = port

        viewModelScope.launch(Dispatchers.IO) {
            SocketManager.connect()
        }
    }

    // ==================== АВТОРИЗАЦИЯ ====================

    fun login(username: String, password: String) {
        SocketManager.sendLogin(username, password)
    }

    fun register(username: String, email: String, password: String) {
        SocketManager.sendRegister(username, email, password)
    }

    fun logout() {
        SocketManager.sendLogout()
        SocketManager.disconnect()
        myUserId = -1
        myUsername = ""
        ChatSession.logout()
        _globalMessages.value = mutableListOf()
        _privateMessages.value = mutableListOf()
        _onlineUsers.value = mutableListOf()
    }

    // ==================== СООБЩЕНИЯ ====================

    fun sendGlobalMessage(content: String) {
        if (content.isNotBlank()) {
            SocketManager.sendGlobalMessage(content.trim())
        }
    }

    fun sendPrivateMessage(receiverUsername: String, content: String) {
        if (content.isNotBlank() && receiverUsername.isNotBlank()) {
            SocketManager.sendPrivateMessage(receiverUsername, content.trim())
        }
    }

    fun loadGlobalHistory() {
        SocketManager.requestGlobalHistory(50)
    }

    fun loadPrivateHistory(otherUsername: String) {
        _privateMessages.value = mutableListOf() // Очищаем перед загрузкой
        SocketManager.requestPrivateHistory(otherUsername, 50)
    }

    fun refreshUsers() {
        SocketManager.requestUserList()
    }

    // ==================== СИСТЕМНЫЕ УВЕДОМЛЕНИЯ ====================

    fun showNotification(context: Context, title: String, text: String) {
        val channelId = "chat_notifications"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        // Создаём канал уведомлений (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Сообщения чата",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private fun parseGlobalMessage(json: JSONObject) = ChatMessage(
        senderId = json.optInt("senderId"),
        senderUsername = json.optString("senderUsername"),
        content = json.optString("content"),
        timestamp = json.optLong("timestamp"),
        isGlobal = true
    )

    private fun parsePrivateMessage(json: JSONObject) = ChatMessage(
        senderId = json.optInt("senderId"),
        senderUsername = json.optString("senderUsername"),
        receiverId = json.optInt("receiverId"),
        receiverUsername = json.optString("receiverUsername"),
        content = json.optString("content"),
        timestamp = json.optLong("timestamp"),
        isGlobal = false
    )

}
