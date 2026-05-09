package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Сообщения"

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

        // Search filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                chatsAdapter.applyFilter(s.toString().trim())
            }
        })

        setupObservers()

        // Connect and load data on first open
        if (!com.messageonline.android.network.SocketManager.isConnected) {
            viewModel.connect()
        }
        viewModel.refreshConversations()
        viewModel.refreshUsers()
    }

    private fun setupObservers() {
        // Main list of chats — live updates
        viewModel.conversations.observe(this) { convList ->
            chatsAdapter.setItems(convList)
            binding.layoutEmpty.visibility = if (convList.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.connectionStatus.observe(this) { status ->
            val subtitle = when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED    -> null  // clear
                ChatViewModel.ConnectionStatus.CONNECTING  -> "Подключение..."
                ChatViewModel.ConnectionStatus.DISCONNECTED -> "Нет соединения"
                ChatViewModel.ConnectionStatus.ERROR        -> "Ошибка соединения"
            }
            supportActionBar?.subtitle = subtitle
        }

        viewModel.notification.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.onlineUsers.observe(this) { users ->
            val onlineCount = users.size
            if (onlineCount > 0) supportActionBar?.subtitle = "$onlineCount в сети"
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConversations()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chats_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_contacts -> { startActivity(Intent(this, UsersActivity::class.java)); true }
            R.id.menu_profile  -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
            R.id.menu_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.menu_logout   -> { confirmLogout(); true }
            else               -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmLogout() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Выход")
            .setMessage("Вы хотите выйти из аккаунта?")
            .setPositiveButton("Выйти") { _, _ ->
                viewModel.logout()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
