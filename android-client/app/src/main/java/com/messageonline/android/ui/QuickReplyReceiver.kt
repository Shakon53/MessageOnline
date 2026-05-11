package com.messageonline.android.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager

class QuickReplyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_QUICK_REPLY   = "com.messageonline.QUICK_REPLY"
        const val KEY_REPLY_TEXT       = "quick_reply_text"
        const val EXTRA_SENDER         = "sender_username"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_REPLY) return

        val bundle      = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText   = bundle.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim() ?: return
        val sender      = intent.getStringExtra(EXTRA_SENDER) ?: return

        if (replyText.isBlank() || sender.isBlank()) return

        if (SocketManager.isConnected && ChatSession.username.isNotBlank()) {
            SocketManager.sendPrivateMessage(sender, replyText)
        } else {
            // App not connected — open the chat with the text pre-filled
            context.startActivity(
                Intent(context, PrivateChatActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("peer_username", sender)
                    putExtra("prefill_text", replyText)
                }
            )
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(sender.hashCode())
    }
}
