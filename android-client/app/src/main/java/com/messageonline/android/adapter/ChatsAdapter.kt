package com.messageonline.android.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.messageonline.android.R
import com.messageonline.android.model.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatsAdapter(
    private val onChatClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

    private val all   = mutableListOf<Conversation>()
    private val shown = mutableListOf<Conversation>()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial:     TextView = view.findViewById(R.id.tvChatInitial)
        val tvName:        TextView = view.findViewById(R.id.tvChatName)
        val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val tvTime:        TextView = view.findViewById(R.id.tvChatTime)
        val tvBadge:       TextView = view.findViewById(R.id.tvUnreadBadge)
        val vOnlineDot:    View     = view.findViewById(R.id.viewOnlineDot)
        val vAvatar:       View     = view.rootView.findViewById<View?>(R.id.tvChatInitial)
                                        ?.parent as? View ?: view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ChatViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))

    override fun getItemCount() = shown.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val conv = shown[position]

        holder.tvName.text        = conv.peerUsername
        holder.tvLastMessage.text = conv.lastMessage
        holder.tvTime.text        = if (conv.lastTimestamp > 0) formatTime(conv.lastTimestamp) else ""

        // Avatar letter & color
        val initial = if (conv.isGlobal) "#" else conv.peerUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvInitial.text = initial

        // Online dot
        holder.vOnlineDot.visibility = if (!conv.isGlobal && conv.isOnline) View.VISIBLE else View.GONE

        // Unread badge
        if (conv.unreadCount > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text       = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onChatClick(conv) }
    }

    fun setItems(list: List<Conversation>) {
        all.clear()
        all.addAll(list)
        applyFilter("")
    }

    fun applyFilter(query: String) {
        shown.clear()
        if (query.isBlank()) {
            shown.addAll(all)
        } else {
            shown.addAll(all.filter {
                it.peerUsername.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            })
        }
        notifyDataSetChanged()
    }

    private fun formatTime(ts: Long): String {
        val msgCal = Calendar.getInstance().apply { time = Date(ts) }
        val today  = Calendar.getInstance()
        val isSameDay = msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                        msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (isSameDay) timeFormat.format(Date(ts)) else dateFormat.format(Date(ts))
    }
}
