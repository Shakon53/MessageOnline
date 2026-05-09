package com.messageonline.android.adapter

import android.graphics.Color
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

class MessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUsername: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT     = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private val timeFormat     = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    // ─── ViewHolders ───────────────────────────────────────────────

    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime:    TextView = view.findViewById(R.id.tvMessageTime)
        val tvStatus:  TextView = view.findViewById(R.id.tvStatus)
    }

    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender:  TextView = view.findViewById(R.id.tvSenderName)
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime:    TextView = view.findViewById(R.id.tvMessageTime)
    }

    // ─── Adapter overrides ─────────────────────────────────────────

    override fun getItemViewType(position: Int) =
        if (messages[position].isMine(myUsername)) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedViewHolder(inflater.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg  = messages[position]
        val time = formatTime(msg.timestamp)

        if (holder is SentViewHolder) {
            holder.tvContent.text = msg.content
            holder.tvTime.text    = time
            bindStatus(holder.tvStatus, msg.status)
        } else if (holder is ReceivedViewHolder) {
            holder.tvSender.text  = msg.senderUsername
            holder.tvContent.text = msg.content
            holder.tvTime.text    = time
        }
    }

    override fun getItemCount() = messages.size

    // ─── Status indicator ──────────────────────────────────────────

    /**
     * ⏱ PENDING  — одна серая галочка (ещё отправляется)
     * ✓✓ SENT    — двойная серая галочка (дошло до сервера)
     * ✓✓ READ    — двойная синяя галочка (прочитано)
     */
    private fun bindStatus(tv: TextView, status: Int) {
        when (status) {
            ChatMessage.STATUS_PENDING -> {
                tv.text = "⏱"
                tv.setTextColor(Color.parseColor("#8696A0"))
            }
            ChatMessage.STATUS_READ -> {
                tv.text = "✓✓"
                tv.setTextColor(Color.parseColor("#53BDEB"))
            }
            else -> { // STATUS_SENT
                tv.text = "✓✓"
                tv.setTextColor(Color.parseColor("#8696A0"))
            }
        }
    }

    // ─── Public helpers ────────────────────────────────────────────

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    // ─── Time formatting ───────────────────────────────────────────

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val date = Date(timestamp)
        val diffMs = System.currentTimeMillis() - timestamp
        return if (diffMs < 86_400_000L) timeFormat.format(date)
               else dateTimeFormat.format(date)
    }
}
