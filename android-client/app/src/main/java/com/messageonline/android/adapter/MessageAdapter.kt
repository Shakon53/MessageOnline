package com.messageonline.android.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.messageonline.android.R
import com.messageonline.android.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val myUsername: String,
    private val onDeleteMessage: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SENT     = 1
        const val VIEW_TYPE_RECEIVED = 2
        const val VIEW_TYPE_DATE     = 3
    }

    // ─── Display model ─────────────────────────────────────────────────────────

    private sealed class DisplayItem {
        data class MessageItem(
            val msg: ChatMessage,
            val isFirstInGroup: Boolean,
            val isLastInGroup: Boolean
        ) : DisplayItem()

        data class DateItem(val label: String) : DisplayItem()
    }

    private val displayList = mutableListOf<DisplayItem>()

    // ─── Formatters ────────────────────────────────────────────────────────────

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val dayFormat  = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    // Last position that had a slide-in animation (avoid re-animating on rebind)
    private var lastAnimatedPos = -1

    // ─── ViewHolders ───────────────────────────────────────────────────────────

    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent:      TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime:         TextView = view.findViewById(R.id.tvMessageTime)
        val tvStatus:       TextView = view.findViewById(R.id.tvStatus)
        val llReplyQuote:   View     = view.findViewById(R.id.llReplyQuote)
        val tvReplySender:  TextView = view.findViewById(R.id.tvReplyToSender)
        val tvReplyContent: TextView = view.findViewById(R.id.tvReplyToContent)
    }

    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flAvatar:       View     = view.findViewById(R.id.flAvatar)
        val tvInitial:      TextView = view.findViewById(R.id.tvAvatarInitial)
        val tvSender:       TextView = view.findViewById(R.id.tvSenderName)
        val tvContent:      TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime:         TextView = view.findViewById(R.id.tvMessageTime)
        val llReplyQuote:   View     = view.findViewById(R.id.llReplyQuote)
        val tvReplySender:  TextView = view.findViewById(R.id.tvReplyToSender)
        val tvReplyContent: TextView = view.findViewById(R.id.tvReplyToContent)
    }

    inner class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDateLabel)
    }

    // ─── Adapter overrides ─────────────────────────────────────────────────────

    override fun getItemCount() = displayList.size

    override fun getItemViewType(position: Int): Int {
        val item = displayList[position]
        return when {
            item is DisplayItem.DateItem -> VIEW_TYPE_DATE
            item is DisplayItem.MessageItem && item.msg.isMine(myUsername) -> VIEW_TYPE_SENT
            else -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT     -> SentViewHolder(inf.inflate(R.layout.item_message_sent, parent, false))
            VIEW_TYPE_RECEIVED -> ReceivedViewHolder(inf.inflate(R.layout.item_message_received, parent, false))
            else               -> DateViewHolder(inf.inflate(R.layout.item_date_separator, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // Slide-in animation only for newly appended items
        if (position > lastAnimatedPos) {
            lastAnimatedPos = position
            val anim = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_in_bottom)
            holder.itemView.startAnimation(anim)
        }

        val item = displayList[position]

        when {
            holder is DateViewHolder && item is DisplayItem.DateItem -> {
                holder.tvDate.text = item.label
            }

            holder is SentViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                holder.tvContent.text = msg.content
                holder.tvTime.text    = timeFormat.format(Date(msg.timestamp))
                bindStatus(holder.tvStatus, msg.status)
                bindReplyQuote(holder.llReplyQuote, holder.tvReplySender, holder.tvReplyContent, msg)

                // Group spacing
                val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                lp?.topMargin = dpToPx(holder.itemView.context, if (item.isFirstInGroup) 6 else 2)
                holder.itemView.layoutParams = lp

                setupLongPress(holder.itemView, msg)
            }

            holder is ReceivedViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                holder.tvContent.text = msg.content
                holder.tvTime.text    = timeFormat.format(Date(msg.timestamp))
                bindReplyQuote(holder.llReplyQuote, holder.tvReplySender, holder.tvReplyContent, msg)

                // Avatar — visible only on last message of group (bottom of bubble stack)
                if (item.isLastInGroup) {
                    holder.flAvatar.visibility = View.VISIBLE
                    holder.tvInitial.text = msg.senderUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                } else {
                    holder.flAvatar.visibility = View.INVISIBLE   // keep layout space
                }

                // Sender name — visible only on first message of group
                if (item.isFirstInGroup) {
                    holder.tvSender.visibility = View.VISIBLE
                    holder.tvSender.text       = msg.senderUsername
                } else {
                    holder.tvSender.visibility = View.GONE
                }

                // Group spacing
                val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                lp?.topMargin = dpToPx(holder.itemView.context, if (item.isFirstInGroup) 6 else 2)
                holder.itemView.layoutParams = lp

                setupLongPress(holder.itemView, msg)
            }
        }
    }

    // ─── Reply quote binding ───────────────────────────────────────────────────

    private fun bindReplyQuote(
        container: View,
        tvSender: TextView,
        tvContent: TextView,
        msg: com.messageonline.android.model.ChatMessage
    ) {
        if (msg.hasReply) {
            container.visibility = View.VISIBLE
            tvSender.text  = msg.replyToSender
            tvContent.text = msg.replyToContent
        } else {
            container.visibility = View.GONE
        }
    }

    // ─── Long press: copy / delete ─────────────────────────────────────────────

    private fun setupLongPress(view: View, msg: ChatMessage) {
        view.setOnLongClickListener {
            val ctx = view.context
            val options = if (msg.isMine(myUsername)) arrayOf("Копировать", "Удалить")
                          else arrayOf("Копировать")

            MaterialAlertDialogBuilder(ctx)
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "Копировать" -> {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("msg", msg.content))
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                        "Удалить" -> onDeleteMessage?.invoke(msg)
                    }
                }
                .show()
            true
        }
    }

    // ─── Status indicator ──────────────────────────────────────────────────────

    private fun bindStatus(tv: TextView, status: Int) {
        when (status) {
            ChatMessage.STATUS_PENDING -> { tv.text = "⏱"; tv.setTextColor(Color.parseColor("#8696A0")) }
            ChatMessage.STATUS_READ    -> { tv.text = "✓✓"; tv.setTextColor(Color.parseColor("#53BDEB")) }
            else                       -> { tv.text = "✓✓"; tv.setTextColor(Color.parseColor("#8696A0")) }
        }
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        lastAnimatedPos = -1        // reset so history items don't all animate
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    /** Return the ChatMessage at a given adapter position, or null for date separators. */
    fun getMessageAt(position: Int): ChatMessage? {
        val item = displayList.getOrNull(position) ?: return null
        return if (item is DisplayItem.MessageItem) item.msg else null
    }

    fun updateMessageStatus(localId: String, newStatus: Int) {
        val idx = messages.indexOfFirst { it.localId == localId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(status = newStatus)
            rebuildDisplayList()
            notifyDataSetChanged()
        }
    }

    // ─── Display list builder ──────────────────────────────────────────────────

    private fun rebuildDisplayList() {
        displayList.clear()
        var lastDayKey = ""

        for (i in messages.indices) {
            val msg = messages[i]

            // Insert date chip when day changes
            val dayKey = dayFormat.format(Date(msg.timestamp))
            if (dayKey != lastDayKey) {
                displayList.add(DisplayItem.DateItem(formatDateLabel(msg.timestamp)))
                lastDayKey = dayKey
            }

            // Grouping flags
            val prevMsg = if (i > 0) messages[i - 1] else null
            val nextMsg = if (i < messages.size - 1) messages[i + 1] else null

            val prevDay = prevMsg?.let { dayFormat.format(Date(it.timestamp)) }
            val nextDay = nextMsg?.let { dayFormat.format(Date(it.timestamp)) }

            val isFirstInGroup = prevMsg == null
                || prevMsg.senderUsername != msg.senderUsername
                || prevDay != dayKey

            val isLastInGroup = nextMsg == null
                || nextMsg.senderUsername != msg.senderUsername
                || nextDay != dayKey

            displayList.add(DisplayItem.MessageItem(msg, isFirstInGroup, isLastInGroup))
        }
    }

    // ─── Date label helpers ────────────────────────────────────────────────────

    private fun formatDateLabel(timestamp: Long): String {
        val msgCal   = Calendar.getInstance().apply { time = Date(timestamp) }
        val today    = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(msgCal, today)     -> "Сегодня"
            isSameDay(msgCal, yesterday) -> "Вчера"
            else                         -> dateFormat.format(Date(timestamp))
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR)        == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun dpToPx(ctx: Context, dp: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt()
}
