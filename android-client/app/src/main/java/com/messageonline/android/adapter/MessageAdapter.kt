package com.messageonline.android.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
    private val onDeleteMessage: ((ChatMessage) -> Unit)? = null,
    private val onEditMessage: ((ChatMessage, String) -> Unit)? = null,
    private val onReplyMessage: ((ChatMessage) -> Unit)? = null,
    private val onForwardMessage: ((ChatMessage) -> Unit)? = null,
    private val onDeleteForAll: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SENT          = 1
        const val VIEW_TYPE_RECEIVED      = 2
        const val VIEW_TYPE_DATE          = 3
        const val VIEW_TYPE_SENT_IMAGE    = 4
        const val VIEW_TYPE_RECV_IMAGE    = 5
        const val VIEW_TYPE_SENT_AUDIO    = 6
        const val VIEW_TYPE_RECV_AUDIO    = 7
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

    private var lastAnimatedPos = -1
    private var searchQuery = ""

    // ─── ViewHolders ───────────────────────────────────────────────────────────

    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent:      TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime:         TextView = view.findViewById(R.id.tvMessageTime)
        val tvStatus:       TextView = view.findViewById(R.id.tvStatus)
        val tvEdited:       TextView = view.findViewById(R.id.tvEdited)
        val llReplyQuote:   View     = view.findViewById(R.id.llReplyQuote)
        val tvReplySender:  TextView = view.findViewById(R.id.tvReplyToSender)
        val tvReplyContent: TextView = view.findViewById(R.id.tvReplyToContent)
    }

    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flAvatar:       View      = view.findViewById(R.id.flAvatar)
        val tvInitial:      TextView  = view.findViewById(R.id.tvAvatarInitial)
        val ivAvatarImage:  ImageView = view.findViewById(R.id.ivAvatarImage)
        val tvSender:       TextView  = view.findViewById(R.id.tvSenderName)
        val tvContent:      TextView  = view.findViewById(R.id.tvMessageContent)
        val tvTime:         TextView  = view.findViewById(R.id.tvMessageTime)
        val tvEdited:       TextView  = view.findViewById(R.id.tvEdited)
        val llReplyQuote:   View      = view.findViewById(R.id.llReplyQuote)
        val tvReplySender:  TextView  = view.findViewById(R.id.tvReplyToSender)
        val tvReplyContent: TextView  = view.findViewById(R.id.tvReplyToContent)
    }

    inner class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDateLabel)
    }

    inner class SentImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage:  ImageView = view.findViewById(R.id.ivMessageImage)
        val tvTime:   TextView  = view.findViewById(R.id.tvMessageTime)
        val tvStatus: TextView  = view.findViewById(R.id.tvStatus)
    }

    inner class ReceivedImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flAvatar:  View      = view.findViewById(R.id.flAvatar)
        val tvInitial: TextView  = view.findViewById(R.id.tvAvatarInitial)
        val tvSender:  TextView  = view.findViewById(R.id.tvSenderName)
        val ivImage:   ImageView = view.findViewById(R.id.ivMessageImage)
        val tvTime:    TextView  = view.findViewById(R.id.tvMessageTime)
    }

    inner class SentAudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPlayPause: ImageView = view.findViewById(R.id.ivPlayPause)
        val tvDuration:  TextView  = view.findViewById(R.id.tvDuration)
        val tvTime:      TextView  = view.findViewById(R.id.tvMessageTime)
        val tvStatus:    TextView  = view.findViewById(R.id.tvStatus)
        var mediaPlayer: MediaPlayer? = null
        var isPlaying = false
    }

    inner class ReceivedAudioViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val flAvatar:    View      = view.findViewById(R.id.flAvatar)
        val tvInitial:   TextView  = view.findViewById(R.id.tvAvatarInitial)
        val tvSender:    TextView  = view.findViewById(R.id.tvSenderName)
        val ivPlayPause: ImageView = view.findViewById(R.id.ivPlayPause)
        val tvDuration:  TextView  = view.findViewById(R.id.tvDuration)
        val tvTime:      TextView  = view.findViewById(R.id.tvMessageTime)
        var mediaPlayer: MediaPlayer? = null
        var isPlaying = false
    }

    // ─── Adapter overrides ─────────────────────────────────────────────────────

    override fun getItemCount() = displayList.size

    override fun getItemViewType(position: Int): Int {
        val item = displayList[position]
        if (item is DisplayItem.DateItem) return VIEW_TYPE_DATE
        if (item !is DisplayItem.MessageItem) return VIEW_TYPE_DATE
        val msg = item.msg
        val isMine = msg.isMine(myUsername)
        return when (msg.messageType) {
            "image"   -> if (isMine) VIEW_TYPE_SENT_IMAGE else VIEW_TYPE_RECV_IMAGE
            "audio"   -> if (isMine) VIEW_TYPE_SENT_AUDIO else VIEW_TYPE_RECV_AUDIO
            else      -> if (isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT        -> SentViewHolder(inf.inflate(R.layout.item_message_sent, parent, false))
            VIEW_TYPE_RECEIVED    -> ReceivedViewHolder(inf.inflate(R.layout.item_message_received, parent, false))
            VIEW_TYPE_SENT_IMAGE  -> SentImageViewHolder(inf.inflate(R.layout.item_message_image_sent, parent, false))
            VIEW_TYPE_RECV_IMAGE  -> ReceivedImageViewHolder(inf.inflate(R.layout.item_message_image_received, parent, false))
            VIEW_TYPE_SENT_AUDIO  -> SentAudioViewHolder(inf.inflate(R.layout.item_message_audio_sent, parent, false))
            VIEW_TYPE_RECV_AUDIO  -> ReceivedAudioViewHolder(inf.inflate(R.layout.item_message_audio_received, parent, false))
            else                  -> DateViewHolder(inf.inflate(R.layout.item_date_separator, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
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
                // Deleted message styling
                if (msg.messageType == "deleted" || msg.content == "[Сообщение удалено]") {
                    holder.tvContent.text = "🚫 Сообщение удалено"
                    holder.tvContent.setTextColor(Color.parseColor("#94A3B8"))
                    holder.tvContent.setTypeface(null, android.graphics.Typeface.ITALIC)
                } else {
                    highlightText(holder.tvContent, msg.content)
                    holder.tvContent.setTextColor(Color.WHITE)
                    holder.tvContent.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                bindStatus(holder.tvStatus, msg.status)
                bindReplyQuote(holder.llReplyQuote, holder.tvReplySender, holder.tvReplyContent, msg)
                holder.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE

                val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                lp?.topMargin = dpToPx(holder.itemView.context, if (item.isFirstInGroup) 6 else 2)
                holder.itemView.layoutParams = lp

                setupLongPress(holder.itemView, msg)
            }

            holder is ReceivedViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                if (msg.messageType == "deleted" || msg.content == "[Сообщение удалено]") {
                    holder.tvContent.text = "🚫 Сообщение удалено"
                    holder.tvContent.setTextColor(Color.parseColor("#94A3B8"))
                    holder.tvContent.setTypeface(null, android.graphics.Typeface.ITALIC)
                } else {
                    highlightText(holder.tvContent, msg.content)
                    holder.tvContent.setTextColor(Color.parseColor("#E2E8F0"))
                    holder.tvContent.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                bindReplyQuote(holder.llReplyQuote, holder.tvReplySender, holder.tvReplyContent, msg)
                holder.tvEdited.visibility = if (msg.isEdited) View.VISIBLE else View.GONE

                if (item.isLastInGroup) {
                    holder.flAvatar.visibility = View.VISIBLE
                    holder.tvInitial.text = msg.senderUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    holder.ivAvatarImage.visibility = View.GONE
                    holder.tvInitial.visibility = View.VISIBLE
                } else {
                    holder.flAvatar.visibility = View.INVISIBLE
                }

                if (item.isFirstInGroup) {
                    holder.tvSender.visibility = View.VISIBLE
                    holder.tvSender.text = msg.senderUsername
                } else {
                    holder.tvSender.visibility = View.GONE
                }

                val lp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                lp?.topMargin = dpToPx(holder.itemView.context, if (item.isFirstInGroup) 6 else 2)
                holder.itemView.layoutParams = lp

                setupLongPress(holder.itemView, msg)
            }

            holder is SentImageViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                loadBase64Image(holder.ivImage, msg.content)
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                bindStatus(holder.tvStatus, msg.status)
                setupLongPress(holder.itemView, msg)
            }

            holder is ReceivedImageViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                loadBase64Image(holder.ivImage, msg.content)
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                if (item.isLastInGroup) {
                    holder.flAvatar.visibility = View.VISIBLE
                    holder.tvInitial.text = msg.senderUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                } else {
                    holder.flAvatar.visibility = View.INVISIBLE
                }
                if (item.isFirstInGroup) {
                    holder.tvSender.visibility = View.VISIBLE
                    holder.tvSender.text = msg.senderUsername
                } else {
                    holder.tvSender.visibility = View.GONE
                }
                setupLongPress(holder.itemView, msg)
            }

            holder is SentAudioViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                bindStatus(holder.tvStatus, msg.status)
                setupAudioPlayer(holder.ivPlayPause, holder.tvDuration, msg.content, holder)
                setupLongPress(holder.itemView, msg)
            }

            holder is ReceivedAudioViewHolder && item is DisplayItem.MessageItem -> {
                val msg = item.msg
                holder.tvTime.text = timeFormat.format(Date(msg.timestamp))
                if (item.isLastInGroup) {
                    holder.flAvatar.visibility = View.VISIBLE
                    holder.tvInitial.text = msg.senderUsername.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                } else {
                    holder.flAvatar.visibility = View.INVISIBLE
                }
                if (item.isFirstInGroup) {
                    holder.tvSender.visibility = View.VISIBLE
                    holder.tvSender.text = msg.senderUsername
                } else {
                    holder.tvSender.visibility = View.GONE
                }
                setupAudioPlayer(holder.ivPlayPause, holder.tvDuration, msg.content, holder)
                setupLongPress(holder.itemView, msg)
            }
        }
    }

    // ─── Image loading ─────────────────────────────────────────────────────────

    private fun loadBase64Image(imageView: ImageView, content: String) {
        try {
            val base64 = when {
                content.startsWith("data:") -> content.substringAfter(",")
                else -> content
            }
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            imageView.load(bytes) {
                crossfade(true)
                error(android.R.drawable.ic_menu_gallery)
            }
        } catch (e: Exception) {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    // ─── Audio player ──────────────────────────────────────────────────────────

    private fun setupAudioPlayer(
        playBtn: ImageView,
        tvDuration: TextView,
        content: String,
        holder: RecyclerView.ViewHolder
    ) {
        var mediaPlayer: MediaPlayer? = null
        var isPlaying = false

        playBtn.setImageResource(R.drawable.ic_play)

        playBtn.setOnClickListener {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
                playBtn.setImageResource(R.drawable.ic_play)
            } else {
                try {
                    if (mediaPlayer == null) {
                        val base64 = when {
                            content.startsWith("data:") -> content.substringAfter(",")
                            else -> content
                        }
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val tempFile = java.io.File.createTempFile("audio_", ".amr", playBtn.context.cacheDir)
                        tempFile.writeBytes(bytes)

                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            val durSec = duration / 1000
                            tvDuration.text = "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}"
                            setOnCompletionListener {
                                isPlaying = false
                                playBtn.setImageResource(R.drawable.ic_play)
                                tvDuration.text = "0:00"
                            }
                        }
                    }
                    mediaPlayer?.start()
                    isPlaying = true
                    playBtn.setImageResource(R.drawable.ic_pause)
                } catch (e: Exception) {
                    Toast.makeText(playBtn.context, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ─── Reply quote binding ───────────────────────────────────────────────────

    private fun bindReplyQuote(
        container: View,
        tvSender: TextView,
        tvContent: TextView,
        msg: ChatMessage
    ) {
        if (msg.hasReply) {
            container.visibility = View.VISIBLE
            tvSender.text  = msg.replyToSender
            tvContent.text = msg.replyToContent
        } else {
            container.visibility = View.GONE
        }
    }

    // ─── Long press: copy / reply / edit / forward / delete ───────────────────

    private fun setupLongPress(view: View, msg: ChatMessage) {
        view.setOnLongClickListener {
            val ctx = view.context
            val options = if (msg.isMine(myUsername)) {
                arrayOf("Копировать", "Ответить", "Редактировать", "Переслать", "Удалить у всех", "Удалить")
            } else {
                arrayOf("Копировать", "Ответить", "Переслать")
            }

            MaterialAlertDialogBuilder(ctx)
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "Копировать" -> {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("msg", msg.content))
                            Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                        }
                        "Ответить"       -> onReplyMessage?.invoke(msg)
                        "Редактировать"  -> showEditDialog(ctx, msg)
                        "Переслать"      -> onForwardMessage?.invoke(msg)
                        "Удалить у всех" -> onDeleteForAll?.invoke(msg)
                        "Удалить"        -> onDeleteMessage?.invoke(msg)
                    }
                }
                .show()
            true
        }
    }

    private fun showEditDialog(ctx: Context, msg: ChatMessage) {
        val editText = android.widget.EditText(ctx).apply {
            setText(msg.content)
            setSelection(text.length)
            setSingleLine(false)
            maxLines = 5
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Редактировать")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newContent = editText.text.toString().trim()
                if (newContent.isNotEmpty() && newContent != msg.content) {
                    onEditMessage?.invoke(msg, newContent)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
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
        lastAnimatedPos = -1
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    fun getMessageAt(position: Int): ChatMessage? {
        val item = displayList.getOrNull(position) ?: return null
        return if (item is DisplayItem.MessageItem) item.msg else null
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        searchQuery = query.trim()
        rebuildDisplayList()
        notifyDataSetChanged()
    }

    fun getMatchCount(): Int {
        if (searchQuery.isBlank()) return 0
        return messages.count { it.content.contains(searchQuery, ignoreCase = true) }
    }

    private fun highlightText(tv: TextView, text: String) {
        if (searchQuery.isBlank()) {
            tv.text = text
            return
        }
        val lowerText  = text.lowercase()
        val lowerQuery = searchQuery.lowercase()
        val spannable  = SpannableString(text)
        var start = lowerText.indexOf(lowerQuery)
        while (start >= 0) {
            val end = start + lowerQuery.length
            spannable.setSpan(
                BackgroundColorSpan(Color.parseColor("#4D6366F1")),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = lowerText.indexOf(lowerQuery, end)
        }
        tv.text = spannable
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

        val source = if (searchQuery.isBlank()) messages
                     else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }

        for (i in source.indices) {
            val msg = source[i]

            val dayKey = dayFormat.format(Date(msg.timestamp))
            if (dayKey != lastDayKey) {
                displayList.add(DisplayItem.DateItem(formatDateLabel(msg.timestamp)))
                lastDayKey = dayKey
            }

            val prevMsg = if (i > 0) source[i - 1] else null
            val nextMsg = if (i < source.size - 1) source[i + 1] else null

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
