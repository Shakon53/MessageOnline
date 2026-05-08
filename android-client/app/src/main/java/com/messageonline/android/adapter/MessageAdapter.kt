package com.messageonline.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.messageonline.android.R
import com.messageonline.android.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Адаптер для RecyclerView с сообщениями чата.
 *
 * Два типа View:
 *   - VIEW_TYPE_SENT (1):     моё сообщение (справа)
 *   - VIEW_TYPE_RECEIVED (2): чужое сообщение (слева)
 */
class MessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUsername: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    // ViewHolder для отправленных сообщений (справа)
    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
    }

    // ViewHolder для полученных сообщений (слева)
    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSenderName)
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isMine(myUsername)) VIEW_TYPE_SENT
        else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val time = formatTime(msg.timestamp)

        if (holder is SentViewHolder) {
            holder.tvContent.text = msg.content
            holder.tvTime.text = time
        } else if (holder is ReceivedViewHolder) {
            holder.tvSender.text = msg.senderUsername
            holder.tvContent.text = msg.content
            holder.tvTime.text = time
        }
    }

    override fun getItemCount() = messages.size

    /** Добавить новое сообщение в конец списка */
    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    /** Заменить весь список (для загрузки истории) */
    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    private fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val now = Date()
        val diffMs = now.time - timestamp
        // Если сегодня — показываем только время, иначе дату
        return if (diffMs < 86_400_000L) timeFormat.format(date)
        else dateTimeFormat.format(date)
    }
}
