package com.messageonline.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.messageonline.android.R
import com.messageonline.android.adapter.ChatsAdapter
import com.messageonline.android.adapter.ChipFilter
import com.messageonline.android.databinding.ActivityChatsBinding
import com.messageonline.android.viewmodel.ChatViewModel

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatsAdapter: ChatsAdapter

    private val prefs by lazy { getSharedPreferences("MessageOnline", MODE_PRIVATE) }
    private val pinnedKey = "pinned_chats"

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied */ }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        viewModel.reattachCallbacks()
        viewModel.refreshConversations()
        binding.bottomNav.removeBadge(R.id.nav_friends)
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
        setupFilterChips()
        setupSwipeToDelete()
        setupFab()
        setupObservers()
        loadPinnedChats()

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
        chatsAdapter = ChatsAdapter(
            onChatClick = { conv ->
                if (conv.isGlobal) {
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    startActivity(Intent(this, PrivateChatActivity::class.java).apply {
                        putExtra("peer_username", conv.peerUsername)
                    })
                }
            },
            onChatLongClick = { conv ->
                if (!conv.isGlobal) showChatContextMenu(conv)
            }
        )
        binding.rvChats.apply {
            adapter       = chatsAdapter
            layoutManager = LinearLayoutManager(this@ChatsActivity)
        }
    }

    // ─── Pinned chats ──────────────────────────────────────────────────────────

    private fun loadPinnedChats() {
        val set = prefs.getStringSet(pinnedKey, emptySet()) ?: emptySet()
        chatsAdapter.pinnedSet = set
    }

    private fun savePinnedChats(set: Set<String>) {
        prefs.edit().putStringSet(pinnedKey, set).apply()
        chatsAdapter.pinnedSet = set
    }

    private fun showChatContextMenu(conv: com.messageonline.android.model.Conversation) {
        val pinned  = prefs.getStringSet(pinnedKey, emptySet()) ?: emptySet()
        val isPinned = pinned.contains(conv.peerUsername)
        val pinLabel = if (isPinned) "📌 Открепить" else "📌 Закрепить"

        AlertDialog.Builder(this)
            .setTitle(conv.peerUsername)
            .setItems(arrayOf(pinLabel, "🗑 Удалить чат")) { _, which ->
                when (which) {
                    0 -> {
                        val newSet = pinned.toMutableSet()
                        if (isPinned) newSet.remove(conv.peerUsername) else newSet.add(conv.peerUsername)
                        savePinnedChats(newSet)
                        val msg = if (isPinned) "Чат откреплён" else "Чат закреплён 📌"
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).setAnchorView(binding.bottomNav).show()
                    }
                    1 -> confirmDeleteChat(conv)
                }
            }
            .show()
    }

    private fun confirmDeleteChat(conv: com.messageonline.android.model.Conversation) {
        AlertDialog.Builder(this)
            .setTitle("Удалить чат?")
            .setMessage("Переписка с ${conv.peerUsername} будет удалена локально")
            .setPositiveButton("Удалить") { _, _ ->
                val pos = (0 until chatsAdapter.itemCount).firstOrNull {
                    // find by walking shown list — use adapter's data
                    true
                } ?: return@setPositiveButton
                // Remove from ViewModel conversations list
                val list = viewModel.conversations.value?.toMutableList() ?: return@setPositiveButton
                list.removeAll { it.peerUsername == conv.peerUsername }
                chatsAdapter.setItems(list)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ─── Swipe to delete ──────────────────────────────────────────────────────

    private fun setupSwipeToDelete() {
        val bgPaint = Paint().apply { color = 0xFFE53935.toInt() }
        val icon    = ContextCompat.getDrawable(this, R.drawable.ic_delete)!!
            .apply { setTint(0xFFFFFFFF.toInt()) }
        val iconSize = (24 * resources.displayMetrics.density).toInt()

        val helper = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos  = viewHolder.bindingAdapterPosition
                val conv = chatsAdapter.removeAt(pos)
                Snackbar.make(binding.root, "Чат удалён", Snackbar.LENGTH_LONG)
                    .setAction("Отмена") { chatsAdapter.restore(conv) }
                    .setAnchorView(binding.bottomNav)
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX < 0) {
                    c.drawRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat(), bgPaint)
                    val cx = item.right - (item.right - (item.right + dX)) / 2
                    val cy = (item.top + item.bottom) / 2
                    icon.setBounds(
                        (cx - iconSize / 2).toInt(), cy - iconSize / 2,
                        (cx + iconSize / 2).toInt(), cy + iconSize / 2
                    )
                    icon.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(helper).attachToRecyclerView(binding.rvChats)
    }

    // ─── Filter chips ──────────────────────────────────────────────────────────

    private fun setupFilterChips() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipUnread -> ChipFilter.UNREAD
                R.id.chipOnline -> ChipFilter.ONLINE
                else            -> ChipFilter.ALL
            }
            chatsAdapter.activeChip = filter
        }
    }

    // ─── Bottom navigation ─────────────────────────────────────────────────────

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chats   -> { showChatsTab();   true }
                R.id.nav_friends -> { showFriendsTab(); true }
                R.id.nav_profile -> { showProfileTab(); true }
                else -> false
            }
        }
    }

    private fun showChatsTab() {
        binding.tvToolbarTitle.text           = "Сообщения"
        binding.layoutSearch.visibility       = View.VISIBLE
        binding.layoutChips.visibility        = View.VISIBLE
        binding.layoutChatsContent.visibility = View.VISIBLE
        binding.fabNewChat.visibility         = View.VISIBLE
        binding.fragmentContainer.visibility  = View.GONE
    }

    private fun showFriendsTab() {
        binding.tvToolbarTitle.text           = "Друзья"
        binding.layoutSearch.visibility       = View.GONE
        binding.layoutChips.visibility        = View.GONE
        binding.layoutChatsContent.visibility = View.GONE
        binding.fabNewChat.visibility         = View.GONE
        binding.fragmentContainer.visibility  = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FriendsFragment())
            .commit()
    }

    private fun showProfileTab() {
        binding.tvToolbarTitle.text           = "Профиль"
        binding.layoutSearch.visibility       = View.GONE
        binding.layoutChips.visibility        = View.GONE
        binding.layoutChatsContent.visibility = View.GONE
        binding.fabNewChat.visibility         = View.GONE
        binding.fragmentContainer.visibility  = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ProfileFragment())
            .commit()
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
            binding.tvToolbarSubtitle.text       = subtitle ?: ""
        }

        viewModel.onlineUsers.observe(this) { users ->
            if (users.size > 0 && binding.bottomNav.selectedItemId == R.id.nav_chats) {
                binding.tvToolbarSubtitle.visibility = View.VISIBLE
                binding.tvToolbarSubtitle.text       = "${users.size} в сети"
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
                Snackbar.make(binding.root, "📩 ${request.username} хочет подружиться", Snackbar.LENGTH_LONG)
                    .setAction("Перейти") { binding.bottomNav.selectedItemId = R.id.nav_friends }
                    .setAnchorView(binding.bottomNav)
                    .show()
            }
        }
    }
}
