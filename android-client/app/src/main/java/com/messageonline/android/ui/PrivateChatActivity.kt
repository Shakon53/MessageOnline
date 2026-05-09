package com.messageonline.android.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityPrivateChatBinding
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

        binding.tvPeerName.text    = peerUsername
        binding.tvPeerInitial.text = peerUsername.first().uppercaseChar().toString()
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

        // FAB scroll listener
        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
                val total       = messageAdapter.itemCount - 1
                val showFab     = lastVisible < total - 2

                if (showFab && binding.fabScrollDown.visibility != android.view.View.VISIBLE) {
                    binding.fabScrollDown.show()
                } else if (!showFab && binding.fabScrollDown.visibility == android.view.View.VISIBLE) {
                    binding.fabScrollDown.hide()
                }
            }
        })
    }

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

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { v ->
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                // Pulse animation on send
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
                viewModel.sendTyping(peerUsername, false)
                viewModel.sendPrivateMessage(peerUsername, text)
                binding.etMessage.text?.clear()
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }

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
