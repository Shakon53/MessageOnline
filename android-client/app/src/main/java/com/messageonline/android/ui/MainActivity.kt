package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityMainBinding
import com.messageonline.android.viewmodel.ChatViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var wasConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Общий чат"
            subtitle = viewModel.myUsername
        }

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadGlobalHistory()
        viewModel.refreshUsers()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            mutableListOf(),
            viewModel.myUsername,
            onDeleteMessage = { msg -> viewModel.deleteLocalMessage(msg) }
        )
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.apply {
            adapter = messageAdapter
            this.layoutManager = this@MainActivity.layoutManager
        }

        // FAB: show when not at bottom, hide when scrolled to bottom
        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
                val total       = messageAdapter.itemCount - 1
                val showFab     = lastVisible < total - 2

                if (showFab && binding.fabScrollDown.visibility != View.VISIBLE) {
                    binding.fabScrollDown.show()
                } else if (!showFab && binding.fabScrollDown.visibility == View.VISIBLE) {
                    binding.fabScrollDown.hide()
                }
            }
        })
    }

    private fun setupObservers() {
        viewModel.globalMessages.observe(this) { messages ->
            messageAdapter.setMessages(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size - 1)
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED -> {
                    wasConnected = true
                    supportActionBar?.subtitle = viewModel.myUsername
                }
                ChatViewModel.ConnectionStatus.DISCONNECTED -> {
                    if (wasConnected) {
                        supportActionBar?.subtitle = "Нет соединения"
                        showReconnectDialog()
                    }
                }
                else -> {}
            }
        }

        viewModel.notification.observe(this) { msg ->
            if (msg.isNotBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (msg.startsWith("Новое сообщение")) {
                    viewModel.showNotification(this, "MessageOnline", msg)
                }
            }
        }

        viewModel.onlineUsers.observe(this) { users ->
            val onlineCount = users.count { it.online }
            supportActionBar?.subtitle = if (onlineCount > 0) "$onlineCount в сети" else viewModel.myUsername
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { v ->
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                // Pulse animation
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
                viewModel.sendGlobalMessage(text)
                binding.etMessage.text?.clear()
                // Scroll to new message
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }

        binding.fabScrollDown.setOnClickListener {
            binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_users -> {
                startActivity(Intent(this, UsersActivity::class.java))
                true
            }
            R.id.menu_search -> {
                Toast.makeText(this, "Поиск скоро", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                confirmLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
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

    private fun showReconnectDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Соединение потеряно")
            .setMessage("Проверьте интернет и попробуйте снова")
            .setPositiveButton("Повторить") { _, _ -> viewModel.connect() }
            .setNegativeButton("Выйти") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setCancelable(false)
            .show()
    }
}
