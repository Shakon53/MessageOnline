package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.R
import com.messageonline.android.adapter.ChatsAdapter
import com.messageonline.android.databinding.ActivityChatsBinding
import com.messageonline.android.viewmodel.ChatViewModel

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatsAdapter: ChatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No default ActionBar title — we use custom tvToolbarTitle
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        setupChatsList()
        setupBottomNav()
        setupSearch()
        setupFab()
        setupObservers()

        if (!com.messageonline.android.network.SocketManager.isConnected) {
            viewModel.connect()
        }
        viewModel.refreshConversations()
        viewModel.refreshUsers()
    }

    // ─── Chats list ────────────────────────────────────────────────────────────

    private fun setupChatsList() {
        chatsAdapter = ChatsAdapter { conv ->
            if (conv.isGlobal) {
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                startActivity(Intent(this, PrivateChatActivity::class.java).apply {
                    putExtra("peer_username", conv.peerUsername)
                })
            }
        }
        binding.rvChats.apply {
            adapter       = chatsAdapter
            layoutManager = LinearLayoutManager(this@ChatsActivity)
        }
    }

    // ─── Bottom navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats   -> true  // already here
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    binding.bottomNav.selectedItemId = R.id.nav_chats  // stay on chats
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    binding.bottomNav.selectedItemId = R.id.nav_chats  // stay on chats
                    true
                }
                else -> false
            }
        }
    }

    // ─── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                chatsAdapter.applyFilter(s.toString().trim())
            }
        })
    }

    // ─── FAB (new chat / go to contacts) ──────────────────────────────────────

    private fun setupFab() {
        binding.fabNewChat.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }
    }

    // ─── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.conversations.observe(this) { convList ->
            chatsAdapter.setItems(convList)
            binding.layoutEmpty.visibility = if (convList.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.connectionStatus.observe(this) { status ->
            val subtitle = when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED    -> null
                ChatViewModel.ConnectionStatus.CONNECTING  -> "Подключение..."
                ChatViewModel.ConnectionStatus.DISCONNECTED -> "Нет соединения"
                ChatViewModel.ConnectionStatus.ERROR        -> "Ошибка соединения"
            }
            binding.tvToolbarSubtitle.visibility = if (subtitle != null) View.VISIBLE else View.GONE
            binding.tvToolbarSubtitle.text = subtitle ?: ""
        }

        viewModel.onlineUsers.observe(this) { users ->
            val count = users.size
            if (count > 0) {
                binding.tvToolbarSubtitle.visibility = View.VISIBLE
                binding.tvToolbarSubtitle.text = "$count в сети"
            }
        }

        viewModel.incomingFriendRequest.observe(this) { request ->
            if (request != null) {
                // Show badge on Friends nav item
                val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_friends)
                badge.isVisible = true

                com.google.android.material.snackbar.Snackbar
                    .make(binding.root, "📩 ${request.username} хочет подружиться",
                          com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setAction("Перейти") {
                        startActivity(Intent(this, FriendsActivity::class.java))
                    }
                    .setAnchorView(binding.bottomNav)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConversations()
        // Reset friends badge after returning from FriendsActivity
        binding.bottomNav.removeBadge(R.id.nav_friends)
    }
}
