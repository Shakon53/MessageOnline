package com.messageonline.android.network

import android.util.Log
import com.messageonline.android.model.Packet
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Менеджер сетевого соединения с сервером.
 *
 * Singleton объект — единственное соединение на всё приложение.
 * Использует корутины для асинхронной работы.
 *
 * Паттерн Observer через callback-функции.
 */
object SocketManager {

    private const val TAG = "SocketManager"
    private const val CONNECT_TIMEOUT = ServerConfig.CONNECT_TIMEOUT_MS

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    private var listenerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ==================== CALLBACKS ====================
    // Вызываются из IO потока — переключайте на Main для UI

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onPacketReceived: ((JSONObject) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ==================== СОСТОЯНИЕ ====================

    var isConnected: Boolean = false
        private set

    // Значения по умолчанию из ServerConfig.kt — меняй там!
    var serverHost: String = ServerConfig.HOST
    var serverPort: Int    = ServerConfig.PORT

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    /**
     * Подключиться к серверу.
     * Запускает фоновый поток для прослушивания сообщений.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()

            socket = Socket().apply {
                connect(InetSocketAddress(serverHost, serverPort), CONNECT_TIMEOUT)
                keepAlive = true
            }

            reader = BufferedReader(
                InputStreamReader(socket!!.getInputStream(), "UTF-8")
            )
            writer = PrintWriter(
                OutputStreamWriter(socket!!.getOutputStream(), "UTF-8"), true
            )

            isConnected = true
            Log.i(TAG, "Подключено к $serverHost:$serverPort")
            onConnected?.invoke()

            // Запускаем фоновое чтение
            startListening()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка подключения: ${e.message}")
            onError?.invoke("Не удалось подключиться: ${e.message}")
            false
        }
    }

    /**
     * Фоновое прослушивание входящих сообщений.
     */
    private fun startListening() {
        listenerJob?.cancel()
        listenerJob = scope.launch {
            try {
                while (isActive && isConnected) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotBlank()) {
                        try {
                            val json = JSONObject(line)
                            Log.d(TAG, "Получен пакет: ${json.optString("type")}")
                            onPacketReceived?.invoke(json)
                        } catch (e: Exception) {
                            Log.w(TAG, "Ошибка парсинга JSON: $line")
                        }
                    }
                }
            } catch (e: Exception) {
                if (isConnected) {
                    Log.w(TAG, "Соединение разорвано: ${e.message}")
                }
            } finally {
                if (isConnected) {
                    isConnected = false
                    onDisconnected?.invoke()
                }
            }
        }
    }

    /**
     * Отключиться от сервера.
     */
    fun disconnect() {
        isConnected = false
        listenerJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка закрытия соединения: ${e.message}")
        }
        socket = null
        reader = null
        writer = null
    }

    // ==================== ОТПРАВКА ====================

    /**
     * Отправить JSON-пакет серверу.
     * Потокобезопасен.
     */
    @Synchronized
    fun send(json: JSONObject) {
        if (!isConnected || writer == null) {
            Log.w(TAG, "Попытка отправки без соединения")
            return
        }
        try {
            writer!!.println(json.toString())
            Log.d(TAG, "Отправлен пакет: ${json.optString("type")}")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки: ${e.message}")
            onError?.invoke("Ошибка отправки сообщения")
        }
    }

    // ==================== ФАБРИЧНЫЕ МЕТОДЫ ====================

    fun sendRegister(username: String, email: String, password: String) {
        send(JSONObject().apply {
            put("type", Packet.REGISTER)
            put("username", username)
            put("email", email)
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

    fun sendGlobalMessage(content: String) {
        send(JSONObject().apply {
            put("type", Packet.GLOBAL_MESSAGE)
            put("content", content)
        })
    }

    fun sendPrivateMessage(receiverUsername: String, content: String) {
        send(JSONObject().apply {
            put("type", Packet.PRIVATE_MESSAGE)
            put("receiverUsername", receiverUsername)
            put("content", content)
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
        send(JSONObject().apply {
            put("type", Packet.GET_USERS)
        })
    }

    fun sendLogout() {
        send(JSONObject().apply {
            put("type", Packet.LOGOUT)
        })
    }
}
