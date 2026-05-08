package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.adapter.UsersAdapter
import com.messageonline.android.databinding.ActivityUsersBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

/**
 * Экран списка онлайн-пользователей.
 * Клик по пользователю открывает личный чат.
 */
class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var usersAdapter: UsersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Пользователи онлайн"
            setDisplayHomeAsUpEnabled(true)
        }

        setupRecyclerView()
        setupObservers()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshUsers()
        }
    }

    private fun setupRecyclerView() {
        usersAdapter = UsersAdapter(
            users = mutableListOf(),
            myUsername = ChatSession.username,
            onUserClick = { user ->
                // Открываем личный чат с этим пользователем
                val intent = Intent(this, PrivateChatActivity::class.java)
                intent.putExtra("peer_username", user.username)
                startActivity(intent)
            }
        )
        binding.rvUsers.apply {
            adapter = usersAdapter
            layoutManager = LinearLayoutManager(this@UsersActivity)
        }
    }

    private fun setupObservers() {
        viewModel.onlineUsers.observe(this) { users ->
            usersAdapter.setUsers(users)
            binding.swipeRefresh.isRefreshing = false
            binding.toolbar.subtitle = "Онлайн: ${users.size}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
