package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.messageonline.android.adapter.FriendsAdapter
import com.messageonline.android.databinding.ActivityFriendsBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: FriendsAdapter

    // ─── QR scan launcher ─────────────────────────────────────────────────────
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val raw = result.contents ?: return@registerForActivityResult
        val userId = raw.removePrefix("messageonline://user/").toIntOrNull()
        if (userId != null && userId > 0) {
            showConfirmAddFriend(userId)
        } else {
            Toast.makeText(this, "Неверный QR-код", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Show own ID — entire card is clickable to copy
        val myId = ChatSession.userId
        binding.tvMyId.text = "#$myId"
        // Make the whole ID card clickable
        (binding.tvMyId.parent.parent as? View)?.setOnClickListener { copyMyId(myId) }
        binding.tvMyId.setOnClickListener { copyMyId(myId) }

        setupRecyclerView()
        setupObservers()

        binding.btnAddFriend.setOnClickListener { showAddFriendDialog() }
        binding.btnScanQR.setOnClickListener { launchQRScanner() }

        binding.swipeRefresh.setColorSchemeColors(0xFF6366F1.toInt())
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshFriends() }

        viewModel.refreshFriends()
    }

    // ─── QR Scan ───────────────────────────────────────────────────────────────

    private fun launchQRScanner() {
        val opts = ScanOptions().apply {
            setPrompt("Наведите на QR-код друга")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        }
        qrScanLauncher.launch(opts)
    }

    private fun showConfirmAddFriend(userId: Int) {
        if (userId == ChatSession.userId) {
            Toast.makeText(this, "Нельзя добавить себя", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить друга")
            .setMessage("Отправить запрос дружбы пользователю с ID: #$userId ?")
            .setPositiveButton("Добавить") { _, _ ->
                SocketManager.sendFriendAdd(userId)
                Toast.makeText(this, "Запрос отправлен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun copyMyId(id: Int) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("id", "$id"))
        Toast.makeText(this, "ID #$id скопирован", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "✓ Запрос принят", Toast.LENGTH_SHORT).show()
            },
            onDecline = { friend ->
                viewModel.declineFriend(friend.userId)
            },
            onRemoveFriend = { friend ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Удалить из друзей")
                    .setMessage("Убрать ${friend.username} из списка друзей?")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.removeFriend(friend.userId) }
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
            updateEmptyState(friends.isEmpty() && requests.isEmpty())
        }

        viewModel.friendRequests.observe(this) { requests ->
            val friends = viewModel.friends.value ?: emptyList()
            adapter.setData(friends, requests)
            updateEmptyState(friends.isEmpty() && requests.isEmpty())
        }

        viewModel.notification.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFriends.visibility  = if (isEmpty) View.GONE  else View.VISIBLE
    }

    private fun showAddFriendDialog() {
        val et = EditText(this).apply {
            hint = "Введите ID пользователя"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(56, 32, 56, 16)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("👥 Добавить друга")
            .setMessage("Попросите друга открыть профиль и сообщить вам его ID")
            .setView(et)
            .setPositiveButton("Отправить запрос") { _, _ ->
                val id = et.text.toString().trim().toIntOrNull()
                when {
                    id == null || id <= 0     -> Toast.makeText(this, "Введите корректный ID", Toast.LENGTH_SHORT).show()
                    id == ChatSession.userId  -> Toast.makeText(this, "Нельзя добавить себя", Toast.LENGTH_SHORT).show()
                    else                      -> viewModel.addFriend(id)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
