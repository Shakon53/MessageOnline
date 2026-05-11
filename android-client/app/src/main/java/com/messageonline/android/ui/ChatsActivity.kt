package com.messageonline.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.R
import com.messageonline.android.adapter.ChatsAdapter
import com.messageonline.android.databinding.ActivityChatsBinding
import com.messageonline.android.viewmodel.ChatViewModel

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatsAdapter: ChatsAdapter

    // Runtime permission request for Android 13+
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — nothing extra needed */ }

    override fun onResume() {
        super.onResume()
        viewModel.reattachCallbacks()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        requestNotificationPermission()
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

    // ─── Notification permission (Android 13+) ────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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
                R.id.nav_chats   -> true
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    binding.bottomNav.selectedItemId = R.id.nav_chats
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    binding.bottomNav.selectedItemId = R.id.nav_chats
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

    // ─── FAB ───────────────────────────────────────────────────────────────────

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
                ChatViewModel.ConnectionStatus.CONNECTED     -> null
                ChatViewModel.ConnectionStatus.CONNECTING   -> "Подключение..."
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

        viewModel.systemMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                AlertDialog.Builder(this)
                    .setTitle("📢 Объявление")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        viewModel.incomingFriendRequest.observe(this) { request ->
            if (request != null) {
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
        binding.bottomNav.removeBadge(R.id.nav_friends)
    }
}
