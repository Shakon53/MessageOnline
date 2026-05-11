package com.messageonline.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.messageonline.android.R
import com.messageonline.android.model.Conversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChipFilter { ALL, UNREAD, ONLINE }

class ChatsAdapter(
    private val onChatClick:      (Conversation) -> Unit,
    private val onChatLongClick:  (Conversation) -> Unit = {}
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

    private val all   = mutableListOf<Conversation>()
    private val shown = mutableListOf<Conversation>()

    var pinnedSet: Set<String> = emptySet()
        set(value) { field = value; recompute() }

    var activeChip: ChipFilter = ChipFilter.ALL
        set(value) { field = value; recompute() }

    private var searchQuery = ""

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial:     TextView  = view.findViewById(R.id.tvChatInitial)
        val ivAvatar:      ImageView = view.findViewById(R.id.ivChatAvatar)
        val tvName:        TextView  = view.findViewById(R.id.tvChatName)
        val tvLastMessage: TextView  = view.findViewById(R.id.tvLastMessage)
        val tvTime:        TextView  = view.findViewById(R.id.tvChatTime)
        val tvBadge:       TextView  = view.findViewById(R.id.tvUnreadBadge)
        val vOnlineDot:    View      = view.findViewById(R.id.viewOnlineDot)
        val ivPin:         ImageView = view.findViewById(R.id.ivPinIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ChatViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false))

    override fun getItemCount() = shown.size

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val conv = shown[position]
        val isPinned = pinnedSet.contains(conv.peerUsername)

        holder.tvName.text        = conv.peerUsername
        holder.tvLastMessage.text = conv.lastMessage
        holder.tvTime.text        = if (conv.lastTimestamp > 0) formatTime(conv.lastTimestamp) else ""
        holder.ivPin.visibility   = if (isPinned) View.VISIBLE else View.GONE

        val initial = if (conv.isGlobal) "#" else conv.peerUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvInitial.text = initial

        holder.vOnlineDot.visibility = if (!conv.isGlobal && conv.isOnline) View.VISIBLE else View.GONE

        if (conv.unreadCount > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text       = if (conv.unreadCount > 99) "99+" else conv.unreadCount.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        if (!conv.avatarUrl.isNullOrEmpty() && !conv.isGlobal) {
            holder.ivAvatar.visibility  = View.VISIBLE
            holder.tvInitial.visibility = View.INVISIBLE
            holder.ivAvatar.load(conv.avatarUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.avatar_circle_bg)
                listener(onError = { _, _ ->
                    holder.ivAvatar.visibility  = View.GONE
                    holder.tvInitial.visibility = View.VISIBLE
                })
            }
        } else {
            holder.ivAvatar.visibility  = View.GONE
            holder.tvInitial.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener     { onChatClick(conv) }
        holder.itemView.setOnLongClickListener { onChatLongClick(conv); true }
    }

    // ─── Data ──────────────────────────────────────────────────────────────────

    fun setItems(list: List<Conversation>) {
        all.clear()
        all.addAll(list)
        recompute()
    }

    fun applyFilter(query: String) {
        searchQuery = query
        recompute()
    }

    /** Remove item at position; returns the removed conversation for undo. */
    fun removeAt(position: Int): Conversation {
        val conv = shown[position]
        all.remove(conv)
        shown.removeAt(position)
        notifyItemRemoved(position)
        return conv
    }

    /** Restore a previously removed conversation. */
    fun restore(conv: Conversation) {
        all.add(0, conv)
        recompute()
    }

    // ─── Private ───────────────────────────────────────────────────────────────

    private fun recompute() {
        val filtered = all.filter { conv ->
            val matchSearch = searchQuery.isBlank() ||
                conv.peerUsername.contains(searchQuery, ignoreCase = true) ||
                conv.lastMessage.contains(searchQuery, ignoreCase = true)
            val matchChip = when (activeChip) {
                ChipFilter.ALL    -> true
                ChipFilter.UNREAD -> conv.unreadCount > 0
                ChipFilter.ONLINE -> conv.isOnline || conv.isGlobal
            }
            matchSearch && matchChip
        }
        val sorted = filtered.sortedWith(
            compareByDescending<Conversation> { pinnedSet.contains(it.peerUsername) }
                .thenByDescending { it.lastTimestamp }
        )
        shown.clear()
        shown.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun formatTime(ts: Long): String {
        val msgCal = Calendar.getInstance().apply { time = Date(ts) }
        val today  = Calendar.getInstance()
        val isSameDay = msgCal.get(Calendar.YEAR)        == today.get(Calendar.YEAR) &&
                        msgCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (isSameDay) timeFormat.format(Date(ts)) else dateFormat.format(Date(ts))
    }
}
