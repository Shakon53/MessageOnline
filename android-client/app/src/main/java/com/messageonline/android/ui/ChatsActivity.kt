package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.adapter.ChatsAdapter
import com.messageonline.android.database.AppDatabase
import com.messageonline.android.databinding.ActivityChatsBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsBinding
    private lateinit var chatsAdapter: ChatsAdapter
    private var allConversations = listOf<Conversation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityChatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        chatsAdapter = ChatsAdapter { conv ->
            startActivity(Intent(this, PrivateChatActivity::class.java).apply {
                putExtra("peer_username", conv.peerUsername)
            })
        }

        binding.rvChats.apply {
            adapter = chatsAdapter
            layoutManager = LinearLayoutManager(this@ChatsActivity)
            addItemDecoration(object : DividerItemDecoration(this@ChatsActivity, VERTICAL) {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect, view: View,
                    parent: androidx.recyclerview.widget.RecyclerView,
                    state: androidx.recyclerview.widget.RecyclerView.State
                ) {
                    outRect.set(0, 0, 0, 0)  // no divider, use padding
                }
            })
        }

        // Search filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s.toString().trim())
            }
        })

        loadConversations()
    }

    private fun loadConversations() {
        val dao = AppDatabase.getInstance(this).messageDao()
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) { dao.getAllPrivateMessages() }

            // Group by peer, take most recent message per conversation
            val myUsername = ChatSession.username
            val convMap = mutableMapOf<String, Conversation>()

            for (msg in messages) {
                val peer = if (msg.senderUsername == myUsername) msg.receiverUsername
                           else msg.senderUsername
                if (peer.isBlank()) continue
                if (!convMap.containsKey(peer)) {
                    convMap[peer] = Conversation(
                        peerUsername  = peer,
                        lastMessage   = msg.content,
                        lastTimestamp = msg.timestamp
                    )
                }
            }

            allConversations = convMap.values.sortedByDescending { it.lastTimestamp }
            applyFilter(binding.etSearch.text.toString().trim())

            binding.layoutEmpty.visibility = if (allConversations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isBlank()) allConversations
                       else allConversations.filter {
                           it.peerUsername.contains(query, ignoreCase = true) ||
                           it.lastMessage.contains(query, ignoreCase = true)
                       }
        chatsAdapter.setItems(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadConversations()  // refresh when returning from a chat
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
