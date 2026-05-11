package com.messageonline.android.ui

import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityMainBinding
import com.messageonline.android.model.ChatMessage
import com.messageonline.android.viewmodel.ChatViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var wasConnected = false
    private var reconnectDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Общий чат"
            subtitle = viewModel.myUsername
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

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
            onDeleteMessage  = { msg -> viewModel.deleteLocalMessage(msg) },
            onEditMessage    = { msg, newContent -> viewModel.editMessage(msg, newContent) },
            onReplyMessage   = { msg -> showReplyBar(msg) },
            onForwardMessage = { msg -> showForwardDialog(msg) }
        )
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.apply {
            adapter = messageAdapter
            this.layoutManager = this@MainActivity.layoutManager
        }

        // Swipe-to-reply
        val swipeHelper = buildSwipeHelper()
        ItemTouchHelper(swipeHelper).attachToRecyclerView(binding.rvMessages)

        // FAB: show when not at bottom
        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last  = layoutManager.findLastCompletelyVisibleItemPosition()
                val total = messageAdapter.itemCount - 1
                if (last < total - 2) binding.fabScrollDown.show() else binding.fabScrollDown.hide()
            }
        })
    }

    // ─── Swipe-to-reply ────────────────────────────────────────────────────────

    private fun buildSwipeHelper(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private var replyTriggered = false
            private val triggerDx = 180f
            private val icon by lazy {
                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_reply)!!
                    .apply { setTint(0xFFFFFFFF.toInt()) }
            }
            private val iconSize = (42 * resources.displayMetrics.density).toInt()

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                messageAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
            }

            // Never auto-dismiss — snap back always
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 10f
            override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                    return
                }
                // Trigger reply once when threshold crossed
                if (dX >= triggerDx && !replyTriggered) {
                    replyTriggered = true
                    val msg = messageAdapter.getMessageAt(vh.bindingAdapterPosition)
                    if (msg != null) showReplyBar(msg)
                }
                if (!isCurrentlyActive) replyTriggered = false

                val cappedDx = minOf(dX, triggerDx + 20f)
                val itemView = vh.itemView

                // Draw reply icon
                val alpha = (cappedDx / triggerDx).coerceIn(0f, 1f)
                icon.alpha = (alpha * 255).toInt()
                val iconTop  = itemView.top + (itemView.height - iconSize) / 2
                val iconLeft = itemView.left + dpToPx(16)
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                icon.draw(c)

                super.onChildDraw(c, rv, vh, cappedDx, dY, actionState, isCurrentlyActive)
            }

            private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
        }
    }

    private fun showReplyBar(msg: ChatMessage) {
        viewModel.replyToMessage = msg
        binding.layoutReplyBar.visibility = View.VISIBLE
        binding.tvReplyToName.text = msg.senderUsername
        binding.tvReplyToText.text = msg.content
        binding.etMessage.requestFocus()
    }

    private fun hideReplyBar() {
        viewModel.replyToMessage = null
        binding.layoutReplyBar.visibility = View.GONE
    }

    private fun showForwardDialog(msg: ChatMessage) {
        val conversations = viewModel.conversations.value ?: return
        val names = conversations.map { it.peerUsername }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Переслать в...")
            .setItems(names) { _, which ->
                val target = conversations[which]
                val fwdText = "🔁 ${msg.senderUsername}:\n${msg.content}"
                if (target.isGlobal) {
                    viewModel.sendGlobalMessage(fwdText)
                } else {
                    viewModel.sendPrivateMessage(target.peerUsername, fwdText)
                }
            }
            .show()
    }

    // ─── Observers ─────────────────────────────────────────────────────────────

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
                    reconnectDialogShown = false
                    supportActionBar?.subtitle = viewModel.myUsername
                }
                ChatViewModel.ConnectionStatus.DISCONNECTED -> {
                    if (wasConnected && !reconnectDialogShown) {
                        supportActionBar?.subtitle = "Нет соединения"
                        reconnectDialogShown = true
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

        viewModel.systemMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                AlertDialog.Builder(this)
                    .setTitle("📢 Объявление")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        viewModel.onlineUsers.observe(this) { users ->
            val onlineCount = users.count { it.online }
            supportActionBar?.subtitle = if (onlineCount > 0) "$onlineCount в сети" else viewModel.myUsername
        }
    }

    // ─── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { v ->
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
                viewModel.sendGlobalMessage(text)
                binding.etMessage.text?.clear()
                hideReplyBar()
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }

        binding.btnCancelReply.setOnClickListener { hideReplyBar() }

        binding.fabScrollDown.setOnClickListener {
            binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }

        // Search
        binding.btnCloseSearch.setOnClickListener { closeSearch() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                messageAdapter.setSearchQuery(query)
                val count = messageAdapter.getMatchCount()
                binding.tvSearchCount.text = if (query.isBlank()) "" else "$count совп."
            }
        })
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else false
        }
    }

    private fun openSearch() {
        binding.layoutSearch.visibility  = View.VISIBLE
        binding.searchDivider.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        binding.layoutSearch.visibility  = View.GONE
        binding.searchDivider.visibility = View.GONE
        binding.etSearch.text?.clear()
        messageAdapter.setSearchQuery("")
        binding.tvSearchCount.text = ""
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // ─── Menu ──────────────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_users    -> { startActivity(Intent(this, UsersActivity::class.java)); true }
            R.id.menu_chats    -> { startActivity(Intent(this, ChatsActivity::class.java)); true }
            R.id.menu_search   -> { openSearch(); true }
            R.id.menu_profile  -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
            R.id.menu_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.menu_logout   -> { confirmLogout(); true }
            else              -> super.onOptionsItemSelected(item)
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
            .setPositiveButton("Повторить") { _, _ ->
                reconnectDialogShown = false
                viewModel.connect()
            }
            .setNegativeButton("Выйти") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setOnDismissListener { reconnectDialogShown = false }
            .setCancelable(false)
            .show()
    }
}
