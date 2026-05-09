package com.messageonline.android.ui

import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityPrivateChatBinding
import com.messageonline.android.model.ChatMessage
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

class PrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var peerUsername: String

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerUsername = intent.getStringExtra("peer_username") ?: run { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        binding.tvPeerName.text     = peerUsername
        binding.tvPeerInitial.text  = peerUsername.first().uppercaseChar().toString()
        binding.tvTypingStatus.text = "В сети"

        viewModel.currentPrivatePeer = peerUsername

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadPrivateHistory(peerUsername)
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            mutableListOf(),
            ChatSession.username,
            onDeleteMessage = { msg -> viewModel.deleteLocalMessage(msg) }
        )
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.apply {
            adapter = messageAdapter
            this.layoutManager = this@PrivateChatActivity.layoutManager
        }

        // Swipe-to-reply
        ItemTouchHelper(buildSwipeHelper()).attachToRecyclerView(binding.rvMessages)

        // FAB scroll listener
        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last  = layoutManager.findLastCompletelyVisibleItemPosition()
                val total = messageAdapter.itemCount - 1
                if (last < total - 2) binding.fabScrollDown.show() else binding.fabScrollDown.hide()
            }
        })
    }

    // ─── Swipe-to-reply ────────────────────────────────────────────────────────

    private fun buildSwipeHelper(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private var replyTriggered = false
            private val triggerDx = 180f
            private val icon by lazy {
                ContextCompat.getDrawable(this@PrivateChatActivity, R.drawable.ic_reply)!!
                    .apply { setTint(0xFFFFFFFF.toInt()) }
            }
            private val iconSize = (42 * resources.displayMetrics.density).toInt()

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                messageAdapter.notifyItemChanged(viewHolder.adapterPosition)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 10f
            override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                if (dX >= triggerDx && !replyTriggered) {
                    replyTriggered = true
                    val msg = messageAdapter.getMessageAt(vh.adapterPosition)
                    if (msg != null) showReplyBar(msg)
                }
                if (!isCurrentlyActive) replyTriggered = false

                val cappedDx = minOf(dX, triggerDx + 20f)
                val itemView = vh.itemView
                val alpha    = (cappedDx / triggerDx).coerceIn(0f, 1f)
                icon.alpha   = (alpha * 255).toInt()
                val iconTop  = itemView.top + (itemView.height - iconSize) / 2
                val iconLeft = itemView.left + dpToPx(16)
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                icon.draw(c)

                super.onChildDraw(c, rv, vh, cappedDx, dY, actionState, isCurrentlyActive)
            }

            private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
        }
    }

    private fun showReplyBar(msg: ChatMessage) {
        viewModel.replyToMessage = msg
        binding.layoutReplyBar.visibility = View.VISIBLE
        binding.tvReplyToName.text = msg.senderUsername
        binding.tvReplyToText.text = msg.content
        binding.etMessage.requestFocus()
    }

    private fun hideReplyBar() {
        viewModel.replyToMessage = null
        binding.layoutReplyBar.visibility = View.GONE
    }

    // ─── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.privateMessages.observe(this) { allMessages ->
            val filtered = allMessages.filter { msg ->
                (msg.senderUsername == peerUsername && msg.receiverUsername == ChatSession.username)
                        || (msg.senderUsername == ChatSession.username && msg.receiverUsername == peerUsername)
            }
            messageAdapter.setMessages(filtered)
            if (filtered.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(filtered.size - 1)
            }
        }

        viewModel.typing.observe(this) { (sender, isTyping) ->
            if (sender == peerUsername) {
                binding.tvTypingStatus.text = if (isTyping) "печатает..." else "В сети"
            }
        }
    }

    // ─── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { v ->
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
                viewModel.sendTyping(peerUsername, false)
                viewModel.sendPrivateMessage(peerUsername, text)
                binding.etMessage.text?.clear()
                hideReplyBar()
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }

        binding.btnCancelReply.setOnClickListener { hideReplyBar() }

        binding.fabScrollDown.setOnClickListener {
            binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            private var wasTyping = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typing = !s.isNullOrBlank()
                if (typing != wasTyping) {
                    wasTyping = typing
                    viewModel.sendTyping(peerUsername, typing)
                }
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.currentPrivatePeer = ""
    }
}
