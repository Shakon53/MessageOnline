package com.messageonline.android.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.messageonline.android.database.AppDatabase
import com.messageonline.android.database.MessageEntity
import com.messageonline.android.database.PendingMessageEntity
import com.messageonline.android.model.ChatMessage
import com.messageonline.android.model.ChatSession
import com.messageonline.android.model.Conversation
import com.messageonline.android.model.Friend
import com.messageonline.android.model.OnlineUser
import com.messageonline.android.model.Packet
import com.messageonline.android.network.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    // ==================== ROOM ====================

    private val dao = AppDatabase.getInstance(app).messageDao()
    private val pendingDao = AppDatabase.getInstance(app).pendingMessageDao()

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

    private val _typing = MutableLiveData<Pair<String, Boolean>>()
    val typing: LiveData<Pair<String, Boolean>> = _typing

    private val _profileUpdated = MutableLiveData<Unit>()
    val profileUpdated: LiveData<Unit> = _profileUpdated

    // List of all conversations (global + private) for the main ChatsActivity
    private val _conversations = MutableLiveData<List<Conversation>>(emptyList())
    val conversations: LiveData<List<Conversation>> = _conversations

    private val _editedMessage = MutableLiveData<ChatMessage>()
    val editedMessage: LiveData<ChatMessage> = _editedMessage

    private val _friends = MutableLiveData<List<Friend>>(emptyList())
    val friends: LiveData<List<Friend>> = _friends

    private val _friendRequests = MutableLiveData<List<Friend>>(emptyList())
    val friendRequests: LiveData<List<Friend>> = _friendRequests

    private val _incomingFriendRequest = MutableLiveData<Friend?>()
    val incomingFriendRequest: LiveData<Friend?> = _incomingFriendRequest

    // ==================== ТЕКУЩИЙ ПОЛЬЗОВАТЕЛЬ ====================

    var myUserId: Int = -1
        private set
    var myUsername: String = ""
        private set
    var currentPrivatePeer: String = ""

    /** Currently replying to this message (set by UI, cleared after send). */
    var replyToMessage: ChatMessage? = null

    enum class ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, ERROR }
    data class AuthResult(val success: Boolean, val message: String = "")

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    init {
        myUserId = ChatSession.userId
        myUsername = ChatSession.username
        setupSocketCallbacks()
        loadCachedGlobalMessages()
    }

    private fun loadCachedGlobalMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = dao.getGlobalMessages().map { it.toChatMessage() }
            if (cached.isNotEmpty()) _globalMessages.postValue(cached.toMutableList())
            refreshConversations()
        }
    }

    fun refreshConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            val allPrivate = dao.getAllPrivateMessages()
            val onlineSet  = _onlineUsers.value?.associateBy { it.username } ?: emptyMap()
            val friendsMap = _friends.value?.associateBy { it.username } ?: emptyMap()
            val prefs = getApplication<Application>().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)

            val convMap = linkedMapOf<String, Conversation>()
            for (msg in allPrivate) {
                val peer = if (msg.senderUsername == myUsername) msg.receiverUsername else msg.senderUsername
                if (peer.isBlank() || convMap.containsKey(peer)) continue
                val unread    = dao.getUnreadCount(peer, myUsername)
                val avatarUrl = friendsMap[peer]?.avatarUrl ?: prefs.getString("avatar_$peer", "") ?: ""
                // Use onlineSet as authoritative source; fall back to friendsMap only before
                // the first USER_LIST arrives (onlineSet still empty right after app start)
                val isOnline = if (onlineSet.isNotEmpty()) onlineSet.containsKey(peer)
                               else friendsMap[peer]?.isOnline == true
                convMap[peer] = Conversation(
                    peerUsername  = peer,
                    lastMessage   = if (msg.senderUsername == myUsername) "Вы: ${msg.content}" else msg.content,
                    lastTimestamp = msg.timestamp,
                    unreadCount   = unread,
                    isOnline      = isOnline,
                    avatarUrl     = avatarUrl
                )
            }

            // Add accepted friends that have NO message history yet
            for (friend in _friends.value ?: emptyList()) {
                if (!convMap.containsKey(friend.username)) {
                    convMap[friend.username] = Conversation(
                        peerUsername  = friend.username,
                        lastMessage   = "Начать разговор",
                        lastTimestamp = 0L,
                        isOnline      = friend.isOnline,
                        avatarUrl     = friend.avatarUrl
                    )
                }
            }

            val globalLast = dao.getGlobalMessages().lastOrNull()
            val globalConv = Conversation(
                peerUsername  = "Общий чат",
                lastMessage   = globalLast?.let { "${it.senderUsername}: ${it.content}" }
                                ?: "Нажмите чтобы присоединиться",
                lastTimestamp = globalLast?.timestamp ?: 0L,
                isGlobal      = true
            )

            // Sort: global first, then by lastTimestamp desc
            val privList = convMap.values.sortedByDescending { it.lastTimestamp }
            val list = mutableListOf(globalConv)
            list.addAll(privList)
            _conversations.postValue(list)
        }
    }

    // ==================== SOCKET CALLBACKS ====================

    private fun setupSocketCallbacks() {
        SocketManager.onConnected = {
            _connectionStatus.postValue(ConnectionStatus.CONNECTED)
            sendSavedFCMToken()
            sendSavedAvatar()
            SocketManager.requestFriends()
            viewModelScope.launch(Dispatchers.IO) { flushPendingMessages() }
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

    private fun sendSavedFCMToken() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        val token = prefs.getString("fcm_token", "") ?: ""
        if (token.isNotEmpty()) SocketManager.sendFCMToken(token)
    }

    private fun sendSavedAvatar() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        val avatar = prefs.getString("last_avatar", "") ?: ""
        if (avatar.isNotEmpty()) SocketManager.sendUpdateAvatar(avatar)
    }

    // ==================== ОБРАБОТКА ПАКЕТОВ ====================

    private fun handleIncomingPacket(json: JSONObject) {
        when (json.optString("type")) {

            Packet.LOGIN_SUCCESS -> {
                myUserId = json.optInt("userId")
                myUsername = json.optString("username")
                ChatSession.login(
                    myUserId, myUsername,
                    json.optString("phone"),
                    json.optString("statusText")
                )
                _loginResult.postValue(AuthResult(true))
            }

            Packet.LOGIN_FAIL ->
                _loginResult.postValue(AuthResult(false, json.optString("message")))

            Packet.REGISTER_SUCCESS ->
                _registerResult.postValue(AuthResult(true))

            Packet.REGISTER_FAIL ->
                _registerResult.postValue(AuthResult(false, json.optString("message")))

            Packet.GLOBAL_MESSAGE -> {
                val msg = parseGlobalMessage(json)
                val list = _globalMessages.value ?: mutableListOf()
                if (msg.senderUsername == myUsername) {
                    val idx = list.indexOfLast {
                        it.status == ChatMessage.STATUS_PENDING && it.content == msg.content
                    }
                    if (idx >= 0) list[idx] = msg.copy(status = ChatMessage.STATUS_SENT)
                    else list.add(msg.copy(status = ChatMessage.STATUS_SENT))
                } else {
                    list.add(msg)
                    // Vibrate on new incoming global message
                    vibrate()
                }
                _globalMessages.postValue(list)
                saveToRoom(msg)
                refreshConversations()
            }

            Packet.PRIVATE_MESSAGE -> {
                val msg = parsePrivateMessage(json)
                val list = _privateMessages.value ?: mutableListOf()
                if (msg.senderUsername == myUsername) {
                    val idx = list.indexOfLast {
                        it.status == ChatMessage.STATUS_PENDING && it.content == msg.content
                    }
                    if (idx >= 0) list[idx] = msg.copy(status = ChatMessage.STATUS_SENT)
                    else list.add(msg.copy(status = ChatMessage.STATUS_SENT))
                } else {
                    list.add(msg)
                    vibrate()
                    // Show local notification if user is NOT currently in this chat
                    if (msg.senderUsername != currentPrivatePeer) {
                        showIncomingNotification(msg.senderUsername, msg.content)
                    }
                }
                _privateMessages.postValue(list)
                saveToRoom(msg)
                refreshConversations()
            }

            Packet.EDITED_MESSAGE -> {
                val sender = json.optString("senderUsername")
                val timestamp = json.optLong("timestamp")
                val newContent = json.optString("newContent")
                val isGlobal = json.optBoolean("isGlobal", true)

                if (isGlobal) {
                    val list = _globalMessages.value ?: mutableListOf()
                    val idx = list.indexOfFirst { it.senderUsername == sender && it.timestamp == timestamp }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(content = newContent, isEdited = true)
                        _globalMessages.postValue(list)
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.updateMessageContent(timestamp, sender, newContent)
                        }
                    }
                } else {
                    val list = _privateMessages.value ?: mutableListOf()
                    val idx = list.indexOfFirst { it.senderUsername == sender && it.timestamp == timestamp }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(content = newContent, isEdited = true)
                        _privateMessages.postValue(list)
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.updateMessageContent(timestamp, sender, newContent)
                        }
                    }
                }
            }

            Packet.MESSAGE_READ -> {
                // Peer has read our messages — mark all sent messages to them as READ
                val reader = json.optString("readerUsername")
                val list   = _privateMessages.value ?: mutableListOf()
                var changed = false
                for (i in list.indices) {
                    val m = list[i]
                    if (m.senderUsername == myUsername &&
                        m.receiverUsername == reader &&
                        m.status != ChatMessage.STATUS_READ) {
                        list[i] = m.copy(status = ChatMessage.STATUS_READ)
                        changed = true
                    }
                }
                if (changed) _privateMessages.postValue(list)
            }

            Packet.USER_LIST -> {
                val arr = json.optJSONArray("users") ?: return
                val users = mutableListOf<OnlineUser>()
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    users.add(OnlineUser(
                        id = u.optInt("id"),
                        username = u.optString("username"),
                        online = u.optBoolean("online", true),
                        statusText = u.optString("statusText"),
                        avatarUrl = u.optString("avatarUrl")
                    ))
                }
                _onlineUsers.postValue(users)

                // Cache avatar URLs in SharedPreferences
                val prefs = getApplication<Application>().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                for (user in users) {
                    if (user.avatarUrl.isNotEmpty()) editor.putString("avatar_${user.username}", user.avatarUrl)
                }
                editor.apply()

                refreshConversations()
            }

            Packet.USER_JOINED -> {
                val user = OnlineUser(json.optInt("userId"), json.optString("username"))
                val list = _onlineUsers.value ?: mutableListOf()
                if (list.none { it.username == user.username }) {
                    list.add(user); _onlineUsers.postValue(list)
                }
                // Update friends online status
                val friendsList = _friends.value?.toMutableList() ?: mutableListOf()
                val fi = friendsList.indexOfFirst { it.username == user.username }
                if (fi >= 0) {
                    friendsList[fi] = friendsList[fi].copy(isOnline = true)
                    _friends.postValue(friendsList)
                }
                refreshConversations()
                _notification.postValue("${user.username} присоединился")
            }

            Packet.USER_LEFT -> {
                val username = json.optString("username")
                val list = _onlineUsers.value ?: mutableListOf()
                list.removeAll { it.username == username }
                _onlineUsers.postValue(list)
                // Update friends online status
                val friendsList = _friends.value?.toMutableList() ?: mutableListOf()
                val fi = friendsList.indexOfFirst { it.username == username }
                if (fi >= 0) {
                    friendsList[fi] = friendsList[fi].copy(isOnline = false)
                    _friends.postValue(friendsList)
                }
                refreshConversations()
                _notification.postValue("$username покинул чат")
            }

            Packet.HISTORY_RESPONSE -> {
                val chatType = json.optString("chatType")
                val arr = json.optJSONArray("messages") ?: return
                val messages = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    // Use chatType from outer packet — server doesn't add isGlobal to history items
                    messages.add(
                        if (chatType == "global") parseGlobalMessage(m)
                        else parsePrivateMessage(m)
                    )
                }
                if (chatType == "global") {
                    _globalMessages.postValue(messages.toMutableList())
                    viewModelScope.launch(Dispatchers.IO) {
                        dao.clearGlobal()
                        dao.insertAll(messages.map { MessageEntity.from(it) })
                    }
                } else {
                    val otherUsername = json.optString("otherUsername")
                    _privateMessages.postValue(messages.toMutableList())
                    viewModelScope.launch(Dispatchers.IO) {
                        // Clear old (possibly corrupted) cache, then insert fresh from server
                        if (otherUsername.isNotEmpty()) {
                            dao.clearPrivateConversation(myUsername, otherUsername)
                        }
                        dao.insertAll(messages.map { MessageEntity.from(it) })
                    }
                }
            }

            Packet.NOTIFICATION -> _notification.postValue(json.optString("content"))
            Packet.ERROR        -> _notification.postValue(json.optString("message"))

            Packet.TYPING ->
                _typing.postValue(Pair(
                    json.optString("senderUsername"),
                    json.optBoolean("isTyping")
                ))

            Packet.PROFILE_UPDATED -> {
                val updatedUsername = json.optString("username")
                val updatedStatus = json.optString("statusText")
                if (updatedUsername == myUsername) ChatSession.statusText = updatedStatus
                val list = _onlineUsers.value ?: mutableListOf()
                val idx = list.indexOfFirst { it.username == updatedUsername }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(statusText = updatedStatus)
                    _onlineUsers.postValue(list)
                }
                _profileUpdated.postValue(Unit)
            }

            Packet.FRIENDS_LIST -> {
                val friendsArr   = json.optJSONArray("friends")  ?: return
                val requestsArr  = json.optJSONArray("requests") ?: return
                val friendsList  = mutableListOf<Friend>()
                val requestsList = mutableListOf<Friend>()
                for (i in 0 until friendsArr.length()) {
                    val u = friendsArr.getJSONObject(i)
                    friendsList.add(Friend(
                        userId     = u.optInt("userId"),
                        username   = u.optString("username"),
                        statusText = u.optString("statusText"),
                        avatarUrl  = u.optString("avatarUrl"),
                        isOnline   = u.optBoolean("online")
                    ))
                }
                for (i in 0 until requestsArr.length()) {
                    val u = requestsArr.getJSONObject(i)
                    requestsList.add(Friend(
                        userId              = u.optInt("userId"),
                        username            = u.optString("username"),
                        statusText          = u.optString("statusText"),
                        avatarUrl           = u.optString("avatarUrl"),
                        isPendingIncoming   = true
                    ))
                }
                _friends.postValue(friendsList)
                _friendRequests.postValue(requestsList)
                refreshConversations()
            }

            Packet.FRIEND_REQUEST_IN -> {
                val req = Friend(
                    userId            = json.optInt("fromUserId"),
                    username          = json.optString("fromUsername"),
                    statusText        = json.optString("fromStatusText"),
                    avatarUrl         = json.optString("fromAvatarUrl"),
                    isPendingIncoming = true
                )
                val list = _friendRequests.value?.toMutableList() ?: mutableListOf()
                if (list.none { it.userId == req.userId }) list.add(req)
                _friendRequests.postValue(list)
                _incomingFriendRequest.postValue(req)
                _notification.postValue("${req.username} хочет добавить вас в друзья")
            }

            Packet.FRIEND_ACCEPTED -> {
                val newFriend = Friend(
                    userId     = json.optInt("userId"),
                    username   = json.optString("username"),
                    statusText = json.optString("statusText"),
                    avatarUrl  = json.optString("avatarUrl"),
                    isOnline   = true
                )
                val list = _friends.value?.toMutableList() ?: mutableListOf()
                if (list.none { it.userId == newFriend.userId }) list.add(newFriend)
                _friends.postValue(list)
                // Remove from requests if we had sent one
                val reqList = _friendRequests.value?.toMutableList() ?: mutableListOf()
                reqList.removeAll { it.userId == newFriend.userId }
                _friendRequests.postValue(reqList)
                _notification.postValue("${newFriend.username} принял запрос дружбы")
                refreshConversations()
            }

            Packet.FRIEND_REMOVED -> {
                val removedId = json.optInt("userId")
                val list = _friends.value?.toMutableList() ?: mutableListOf()
                list.removeAll { it.userId == removedId }
                _friends.postValue(list)
                refreshConversations()
            }

            Packet.MESSAGE_DELETED -> {
                val sender = json.optString("senderUsername")
                val timestamp = json.optLong("timestamp")
                val isGlobal = json.optBoolean("isGlobal", false)

                if (isGlobal) {
                    val list = _globalMessages.value ?: mutableListOf()
                    val idx = list.indexOfFirst { it.senderUsername == sender && it.timestamp == timestamp }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(content = "[Сообщение удалено]", messageType = "deleted")
                        _globalMessages.postValue(list)
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.updateMessageContent(timestamp, sender, "[Сообщение удалено]")
                        }
                    }
                } else {
                    val list = _privateMessages.value ?: mutableListOf()
                    val idx = list.indexOfFirst { it.senderUsername == sender && it.timestamp == timestamp }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(content = "[Сообщение удалено]", messageType = "deleted")
                        _privateMessages.postValue(list)
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.updateMessageContent(timestamp, sender, "[Сообщение удалено]")
                        }
                    }
                }
            }
        }
    }

    private fun vibrate() {
        try {
            val vibrator = getApplication<Application>()
                .getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    private fun saveToRoom(msg: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) { dao.insert(MessageEntity.from(msg)) }
    }

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    fun connect() {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        SocketManager.connect()
        viewModelScope.launch {
            kotlinx.coroutines.delay(12_000)
            if (_connectionStatus.value == ConnectionStatus.CONNECTING) {
                SocketManager.disconnect()
                _connectionStatus.postValue(ConnectionStatus.ERROR)
                _notification.postValue("Сервер не отвечает. Проверьте интернет.")
            }
        }
    }

    // ==================== АВТОРИЗАЦИЯ ====================

    fun login(username: String, password: String) = SocketManager.sendLogin(username, password)
    fun register(username: String, phone: String, password: String) =
        SocketManager.sendRegister(username, phone, password)

    fun logout() {
        SocketManager.sendLogout()
        SocketManager.disconnect()
        myUserId = -1; myUsername = ""
        ChatSession.logout()
        _globalMessages.value = mutableListOf()
        _privateMessages.value = mutableListOf()
        _onlineUsers.value = mutableListOf()
        viewModelScope.launch(Dispatchers.IO) { dao.clearAll() }
    }

    // ==================== СООБЩЕНИЯ ====================

    fun sendGlobalMessage(content: String) {
        if (content.isBlank()) return
        val trimmed = content.trim()
        val reply = replyToMessage
        replyToMessage = null
        val pending = ChatMessage(
            senderId = myUserId, senderUsername = myUsername,
            content = trimmed, timestamp = System.currentTimeMillis(),
            isGlobal = true, status = ChatMessage.STATUS_PENDING,
            localId = UUID.randomUUID().toString(),
            replyToSender = reply?.senderUsername ?: "",
            replyToContent = reply?.content ?: ""
        )
        val list = _globalMessages.value ?: mutableListOf()
        list.add(pending)
        _globalMessages.value = list
        SocketManager.sendGlobalMessage(trimmed, pending.replyToSender, pending.replyToContent)
    }

    fun sendPrivateMessage(receiverUsername: String, content: String, messageType: String = "text") {
        if (content.isBlank() || receiverUsername.isBlank()) return
        val trimmed = content.trim()
        val reply = replyToMessage
        replyToMessage = null
        val pending = ChatMessage(
            senderId = myUserId, senderUsername = myUsername,
            receiverUsername = receiverUsername, content = trimmed,
            timestamp = System.currentTimeMillis(), isGlobal = false,
            status = ChatMessage.STATUS_PENDING,
            localId = UUID.randomUUID().toString(),
            replyToSender = reply?.senderUsername ?: "",
            replyToContent = reply?.content ?: "",
            messageType = messageType
        )
        val list = _privateMessages.value ?: mutableListOf()
        list.add(pending)
        _privateMessages.value = list
        if (SocketManager.isConnected) {
            SocketManager.sendPrivateMessage(receiverUsername, trimmed, pending.replyToSender, pending.replyToContent, messageType)
        } else {
            // Queue for offline sending
            viewModelScope.launch(Dispatchers.IO) {
                pendingDao.insert(PendingMessageEntity(
                    receiverUsername = receiverUsername,
                    content = trimmed,
                    messageType = messageType,
                    isGlobal = false,
                    replyToSender = pending.replyToSender,
                    replyToContent = pending.replyToContent
                ))
            }
        }
    }

    /** Flush queued messages once connected */
    private suspend fun flushPendingMessages() {
        val pending = pendingDao.getAll()
        for (p in pending) {
            if (p.isGlobal) {
                SocketManager.sendGlobalMessageWithType(p.content, p.messageType, p.replyToSender, p.replyToContent)
            } else {
                SocketManager.sendPrivateMessage(p.receiverUsername, p.content, p.replyToSender, p.replyToContent, p.messageType)
            }
            pendingDao.deleteById(p.id)
        }
    }

    /** Delete a message for everyone (server + local) */
    fun deleteForEveryone(msg: ChatMessage) {
        // Update locally
        if (msg.isGlobal) {
            val list = _globalMessages.value ?: mutableListOf()
            val idx = list.indexOfFirst { it.timestamp == msg.timestamp && it.senderUsername == msg.senderUsername }
            if (idx >= 0) {
                list[idx] = list[idx].copy(content = "[Сообщение удалено]", messageType = "deleted")
                _globalMessages.value = list
            }
        } else {
            val list = _privateMessages.value ?: mutableListOf()
            val idx = list.indexOfFirst { it.timestamp == msg.timestamp && it.senderUsername == msg.senderUsername }
            if (idx >= 0) {
                list[idx] = list[idx].copy(content = "[Сообщение удалено]", messageType = "deleted")
                _privateMessages.value = list
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateMessageContent(msg.timestamp, msg.senderUsername, "[Сообщение удалено]")
        }
        // Send to server
        SocketManager.sendDeleteForAll(msg.timestamp, msg.isGlobal, msg.receiverUsername ?: "")
    }

    fun loadGlobalHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = dao.getGlobalMessages().map { it.toChatMessage() }
            if (cached.isNotEmpty()) _globalMessages.postValue(cached.toMutableList())
        }
        SocketManager.requestGlobalHistory(100)
    }

    fun loadPrivateHistory(otherUsername: String) {
        _privateMessages.value = mutableListOf()
        viewModelScope.launch(Dispatchers.IO) {
            val cached = dao.getPrivateMessages(myUsername, otherUsername)
                .map { it.toChatMessage() }
            if (cached.isNotEmpty()) _privateMessages.postValue(cached.toMutableList())
        }
        SocketManager.requestPrivateHistory(otherUsername, 100)
    }

    fun refreshUsers() = SocketManager.requestUserList()
    fun sendTyping(r: String, t: Boolean) = SocketManager.sendTyping(r, t)
    fun addFriend(targetUserId: Int) = SocketManager.sendFriendAdd(targetUserId)
    fun acceptFriend(fromUserId: Int) {
        SocketManager.sendFriendAccept(fromUserId)
        val list = _friendRequests.value?.toMutableList() ?: mutableListOf()
        list.removeAll { it.userId == fromUserId }
        _friendRequests.value = list
    }
    fun declineFriend(fromUserId: Int) {
        SocketManager.sendFriendDecline(fromUserId)
        val list = _friendRequests.value?.toMutableList() ?: mutableListOf()
        list.removeAll { it.userId == fromUserId }
        _friendRequests.value = list
    }
    fun removeFriend(friendUserId: Int) = SocketManager.sendFriendRemove(friendUserId)
    fun refreshFriends() = SocketManager.requestFriends()
    fun updateProfile(statusText: String) = SocketManager.sendUpdateProfile(statusText)
    fun markRead(peerUsername: String)    = SocketManager.sendMarkRead(peerUsername)

    fun markAllRead(peerUsername: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.markConversationRead(peerUsername, myUsername)
            refreshConversations()
        }
        markRead(peerUsername) // also send socket notification
    }

    /** Remove a message locally (only from in-memory list + Room). */
    fun deleteLocalMessage(msg: ChatMessage) {
        // Remove from global list
        val gList = _globalMessages.value
        if (gList != null && gList.remove(msg)) {
            _globalMessages.value = gList
        }
        // Remove from private list
        val pList = _privateMessages.value
        if (pList != null && pList.remove(msg)) {
            _privateMessages.value = pList
        }
        // Remove from Room by timestamp + senderUsername (best-effort)
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteByTimestampAndSender(msg.timestamp, msg.senderUsername)
        }
    }

    /** Edit a message locally and send to server. */
    fun editMessage(msg: ChatMessage, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank()) return

        // Update locally
        if (msg.isGlobal) {
            val list = _globalMessages.value ?: mutableListOf()
            val idx = list.indexOfFirst { it.timestamp == msg.timestamp && it.senderUsername == msg.senderUsername }
            if (idx >= 0) {
                list[idx] = list[idx].copy(content = trimmed, isEdited = true)
                _globalMessages.value = list
            }
        } else {
            val list = _privateMessages.value ?: mutableListOf()
            val idx = list.indexOfFirst { it.timestamp == msg.timestamp && it.senderUsername == msg.senderUsername }
            if (idx >= 0) {
                list[idx] = list[idx].copy(content = trimmed, isEdited = true)
                _privateMessages.value = list
            }
        }

        // Persist locally
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateMessageContent(msg.timestamp, msg.senderUsername, trimmed)
        }

        // Send to server
        SocketManager.sendEditMessage(
            msg.timestamp, trimmed, msg.isGlobal,
            msg.receiverUsername ?: ""
        )
    }

    // ==================== УВЕДОМЛЕНИЯ ====================

    private fun showIncomingNotification(senderUsername: String, messageText: String) {
        val ctx = getApplication<Application>()
        val channelId = "msg_incoming"
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel with sound + vibration (Android 8+)
        val channel = NotificationChannel(
            channelId,
            "Входящие сообщения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 80, 150)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)

        // Intent: tap → open PrivateChatActivity
        val intent = android.content.Intent(ctx,
            com.messageonline.android.ui.PrivateChatActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("peer_username", senderUsername)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            ctx,
            senderUsername.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Avatar initial as large icon color
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(com.messageonline.android.R.drawable.ic_message)
            .setContentTitle(senderUsername)
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColorized(true)
            .setColor(0xFF6366F1.toInt())
            // Group multiple messages from same sender
            .setGroup("messages_$senderUsername")
            .build()

        manager.notify(senderUsername.hashCode(), notification)
    }

    fun showNotification(context: Context, title: String, text: String) {
        showIncomingNotification(title, text)
    }

    // ==================== ПАРСИНГ ====================

    private fun parseGlobalMessage(json: JSONObject) = ChatMessage(
        senderId = json.optInt("senderId"),
        senderUsername = json.optString("senderUsername"),
        content = json.optString("content"),
        timestamp = json.optLong("timestamp"),
        isGlobal = true, status = ChatMessage.STATUS_SENT,
        replyToSender = json.optString("replyToSender"),
        replyToContent = json.optString("replyToContent"),
        messageType = json.optString("messageType", "text").ifEmpty { "text" }
    )

    private fun parsePrivateMessage(json: JSONObject) = ChatMessage(
        senderId = json.optInt("senderId"),
        senderUsername = json.optString("senderUsername"),
        receiverId = json.optInt("receiverId"),
        receiverUsername = json.optString("receiverUsername"),
        content = json.optString("content"),
        timestamp = json.optLong("timestamp"),
        isGlobal = false, status = ChatMessage.STATUS_SENT,
        replyToSender = json.optString("replyToSender"),
        replyToContent = json.optString("replyToContent"),
        messageType = json.optString("messageType", "text").ifEmpty { "text" }
    )
}
