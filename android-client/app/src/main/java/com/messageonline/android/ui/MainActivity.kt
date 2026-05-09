package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityMainBinding
import com.messageonline.android.viewmodel.ChatViewModel

/**
 * Главный экран — глобальный чат.
 *
 * Показывает:
 *  - Список сообщений глобального чата
 *  - Поле ввода + кнопка отправки
 *  - Кнопка перехода к списку пользователей
 *  - Меню с выходом
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Общий чат"
        supportActionBar?.subtitle = "Подключено: ${viewModel.myUsername}"

        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        // Загружаем историю при входе
        viewModel.loadGlobalHistory()
        viewModel.refreshUsers()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(mutableListOf(), viewModel.myUsername)
        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Новые сообщения снизу
        }
        binding.rvMessages.apply {
            adapter = messageAdapter
            this.layoutManager = this@MainActivity.layoutManager
        }
    }

    private fun setupObservers() {
        // Наблюдаем за сообщениями
        viewModel.globalMessages.observe(this) { messages ->
            messageAdapter.setMessages(messages)
            // Прокручиваем к последнему сообщению
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
        }

        // Наблюдаем за статусом подключения
        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.DISCONNECTED -> {
                    binding.toolbar.subtitle = "Отключено"
                    showReconnectDialog()
                }
                ChatViewModel.ConnectionStatus.CONNECTED ->
                    binding.toolbar.subtitle = viewModel.myUsername
                else -> {}
            }
        }

        // Наблюдаем за уведомлениями
        viewModel.notification.observe(this) { msg ->
            if (msg.isNotBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                // Показываем системное уведомление для личных сообщений
                if (msg.startsWith("Новое сообщение")) {
                    viewModel.showNotification(this, "MessageOnline", msg)
                }
            }
        }

        // Обновляем счётчик онлайн пользователей
        viewModel.onlineUsers.observe(this) { users ->
            binding.btnUsers.text = "Пользователи (${users.size})"
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendGlobalMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        binding.btnUsers.setOnClickListener {
            startActivity(Intent(this, UsersActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
            .setTitle("Соединение разорвано")
            .setMessage("Связь с сервером потеряна. Войти снова?")
            .setPositiveButton("Войти") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .setCancelable(false)
            .show()
    }
}
