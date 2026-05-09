package com.messageonline.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messageonline.android.R
import com.messageonline.android.network.SocketManager
import com.messageonline.android.ui.ChatsActivity
import com.messageonline.android.ui.PrivateChatActivity

class FCMService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID   = "msg_incoming"
        const val CHANNEL_NAME = "Входящие сообщения"
    }

    override fun onNewToken(token: String) {
        getSharedPreferences("MessageOnline", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
        if (SocketManager.isConnected) SocketManager.sendFCMToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data         = message.data
        val title        = data["title"]       ?: message.notification?.title ?: "MessageOnline"
        val body         = data["body"]        ?: message.notification?.body  ?: return
        val chatType     = data["chatType"]    ?: "global"
        val peerUsername = data["peerUsername"] ?: ""
        showNotification(title, body, chatType, peerUsername)
    }

    private fun showNotification(
        title: String,
        body: String,
        chatType: String = "global",
        peerUsername: String = ""
    ) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // High-priority channel with sound + vibration
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 80, 150)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)

        // Intent: открыть нужный чат при нажатии
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
            this,
            peerUsername.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF6366F1.toInt())
            .setColorized(true)
            .setGroup("messages_$peerUsername")
            .build()

        manager.notify(peerUsername.hashCode(), notification)
    }
}
