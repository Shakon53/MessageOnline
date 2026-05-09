package com.messageonline.android.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityPrivateChatBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

class PrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var peerUsername: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        peerUsername = intent.getStringExtra("peer_username") ?: run {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        // Custom toolbar header
        binding.tvPeerName.text = peerUsername
        binding.tvPeerInitial.text = peerUsername.first().uppercaseChar().toString()
        binding.tvTypingStatus.text = "В сети"

        viewModel.currentPrivatePeer = peerUsername

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadPrivateHistory(peerUsername)
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(mutableListOf(), ChatSession.username)
        binding.rvMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@PrivateChatActivity).apply {
                stackFromEnd = true
            }
        }
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
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendTyping(peerUsername, false)
                viewModel.sendPrivateMessage(peerUsername, text)
                binding.etMessage.text?.clear()
            }
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
