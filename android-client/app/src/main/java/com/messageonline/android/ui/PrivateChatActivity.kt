package com.messageonline.android.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityPrivateChatBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

/**
 * Экран личного диалога между двумя пользователями.
 *
 * Принимает Intent с параметром "peer_username" — имя собеседника.
 */
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
            title = peerUsername
            subtitle = "Личный чат"
            setDisplayHomeAsUpEnabled(true)
        }

        viewModel.currentPrivatePeer = peerUsername

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        // Загружаем историю личных сообщений
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
            // Фильтруем сообщения — только с текущим собеседником
            val filtered = allMessages.filter { msg ->
                (msg.senderUsername == peerUsername && msg.receiverUsername == ChatSession.username)
                || (msg.senderUsername == ChatSession.username && msg.receiverUsername == peerUsername)
            }
            messageAdapter.setMessages(filtered)
            if (filtered.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(filtered.size - 1)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendPrivateMessage(peerUsername, text)
                binding.etMessage.text?.clear()
            }
        }
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
