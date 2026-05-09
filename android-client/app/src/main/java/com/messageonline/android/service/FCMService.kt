package com.messageonline.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messageonline.android.network.SocketManager
import com.messageonline.android.ui.ChatsActivity
import com.messageonline.android.ui.PrivateChatActivity

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "msg_push"
    }

    /**
     * Вызывается когда FCM выдаёт новый токен (первый запуск или сброс).
     * Сохраняем локально и отправляем на сервер если уже подключены.
     */
    override fun onNewToken(token: String) {
        getSharedPreferences("MessageOnline", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()

        if (SocketManager.isConnected) {
            SocketManager.sendFCMToken(token)
        }
    }

    /**
     * Входящий push когда приложение на переднем плане или фоне.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: "MessageOnline"
        val body = message.data["body"] ?: message.notification?.body ?: return
        val chatType = message.data["chatType"] ?: "global"
        val peerUsername = message.data["peerUsername"] ?: ""
        showNotification(title, body, chatType, peerUsername)
    }

    private fun showNotification(title: String, body: String, chatType: String = "global", peerUsername: String = "") {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Создаём канал (Android 8+)
        val channel = NotificationChannel(
            CHANNEL_ID, "Сообщения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Входящие сообщения" }
        manager.createNotificationChannel(channel)

        // Intent при тапе на уведомление — открываем правильный чат
        val intent = if (chatType == "private" && peerUsername.isNotEmpty()) {
            Intent(this, PrivateChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("peer_username", peerUsername)
            }
        } else {
            Intent(this, ChatsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
