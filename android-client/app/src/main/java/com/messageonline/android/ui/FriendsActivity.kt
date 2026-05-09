package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.messageonline.android.adapter.FriendsAdapter
import com.messageonline.android.databinding.ActivityFriendsBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: FriendsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Show own ID
        val myId = ChatSession.userId
        binding.tvMyId.text = "Ваш ID: #$myId"
        binding.tvMyId.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("id", "#$myId"))
            Toast.makeText(this, "ID скопирован", Toast.LENGTH_SHORT).show()
        }

        setupRecyclerView()
        setupObservers()

        binding.btnAddFriend.setOnClickListener { showAddFriendDialog() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshFriends()
        }

        viewModel.refreshFriends()
    }

    private fun setupRecyclerView() {
        adapter = FriendsAdapter(
            onStartChat = { friend ->
                startActivity(Intent(this, PrivateChatActivity::class.java).apply {
                    putExtra("peer_username", friend.username)
                })
            },
            onAccept = { friend ->
                viewModel.acceptFriend(friend.userId)
                Toast.makeText(this, "Принято!", Toast.LENGTH_SHORT).show()
            },
            onDecline = { friend ->
                viewModel.declineFriend(friend.userId)
            },
            onRemoveFriend = { friend ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Удалить из друзей")
                    .setMessage("Убрать ${friend.username} из списка друзей?")
                    .setPositiveButton("Удалить") { _, _ ->
                        viewModel.removeFriend(friend.userId)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvFriends.apply {
            this.adapter = this@FriendsActivity.adapter
            layoutManager = LinearLayoutManager(this@FriendsActivity)
        }
    }

    private fun setupObservers() {
        viewModel.friends.observe(this) { friends ->
            val requests = viewModel.friendRequests.value ?: emptyList()
            adapter.setData(friends, requests)
            binding.swipeRefresh.isRefreshing = false
            supportActionBar?.subtitle = if (friends.isEmpty()) null else "${friends.size} друзей"
        }

        viewModel.friendRequests.observe(this) { requests ->
            val friends = viewModel.friends.value ?: emptyList()
            adapter.setData(friends, requests)
        }

        viewModel.notification.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddFriendDialog() {
        val et = EditText(this).apply {
            hint = "Введите ID (например 1234)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить друга")
            .setMessage("Попросите друга открыть профиль и сообщить вам его ID")
            .setView(et)
            .setPositiveButton("Отправить запрос") { _, _ ->
                val idStr = et.text.toString().trim()
                val id = idStr.toIntOrNull()
                if (id == null || id <= 0) {
                    Toast.makeText(this, "Введите корректный ID", Toast.LENGTH_SHORT).show()
                } else if (id == ChatSession.userId) {
                    Toast.makeText(this, "Нельзя добавить себя", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addFriend(id)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
