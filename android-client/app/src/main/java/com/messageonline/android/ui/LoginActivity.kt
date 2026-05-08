package com.messageonline.android.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.messageonline.android.databinding.ActivityLoginBinding
import com.messageonline.android.network.ServerConfig
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Экран входа в аккаунт.
 *
 * Поток:
 *  1. Пользователь вводит имя/пароль и IP сервера
 *  2. Нажимает "Войти"
 *  3. Подключаемся к серверу (TCP)
 *  4. Отправляем LOGIN пакет
 *  5. При успехе — переходим в MainActivity
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)

        // Восстанавливаем последний использованный адрес сервера
        // По умолчанию — адрес из ServerConfig.kt (облачный сервер)
        binding.etServerHost.setText(prefs.getString("server_host", ServerConfig.HOST))
        binding.etServerPort.setText(prefs.getString("server_port", ServerConfig.PORT.toString()))
        binding.etServerPort.setText(prefs.getString("server_port", "8888"))
        binding.etUsername.setText(prefs.getString("last_username", ""))

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        // Следим за результатом входа
        viewModel.loginResult.observe(this) { result ->
            setLoading(false)
            if (result.success) {
                // Сохраняем настройки
                prefs.edit()
                    .putString("server_host", binding.etServerHost.text.toString().trim())
                    .putString("server_port", binding.etServerPort.text.toString().trim())
                    .putString("last_username", binding.etUsername.text.toString().trim())
                    .apply()

                // Переходим в главный экран
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                showError(result.message)
            }
        }

        // Следим за статусом подключения
        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTING ->
                    setLoading(true, "Подключение...")
                ChatViewModel.ConnectionStatus.ERROR -> {
                    setLoading(false)
                    showError("Не удалось подключиться. На телефоне укажите IP компьютера в Wi‑Fi сети. 10.0.2.2 работает только на эмуляторе.")
                }
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val host = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            // Валидация
            if (host.isEmpty()) { showError("Укажите IP сервера. Для телефона это IP компьютера, например 192.168.1.5"); return@setOnClickListener }
            if (username.isEmpty()) { showError("Введите имя пользователя"); return@setOnClickListener }
            if (password.isEmpty()) { showError("Введите пароль"); return@setOnClickListener }

            val port = portStr.toIntOrNull() ?: 8888

            setLoading(true, "Подключение к серверу...")

            if (SocketManager.isConnected) {
                // Уже подключены — сразу логинимся
                viewModel.login(username, password)
            } else {
                // Подключаемся, затем логинимся
                viewModel.connect(host, port)
                lifecycleScope.launch {
                    // Ждём подключения и отправляем логин
                    kotlinx.coroutines.delay(1000)
                    if (SocketManager.isConnected) {
                        viewModel.login(username, password)
                    }
                }
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.tvStatus.text = message
        binding.tvStatus.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
