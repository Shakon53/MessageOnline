package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.messageonline.android.databinding.ActivityRegisterBinding
import com.messageonline.android.network.ServerConfig
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Экран регистрации нового пользователя.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Подставляем адрес сервера из ServerConfig по умолчанию
        binding.etServerHost.setText(ServerConfig.HOST)
        binding.etServerPort.setText(ServerConfig.PORT.toString())

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.registerResult.observe(this) { result ->
            setLoading(false)
            if (result.success) {
                Toast.makeText(this, "Регистрация успешна! Теперь войдите в аккаунт",
                    Toast.LENGTH_LONG).show()
                finish() // Возвращаемся на экран входа
            } else {
                showError(result.message)
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            if (status == ChatViewModel.ConnectionStatus.ERROR) {
                setLoading(false)
                showError("Не удалось подключиться к серверу")
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            val host = binding.etServerHost.text.toString().trim()
            val portStr = binding.etServerPort.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val passwordConfirm = binding.etPasswordConfirm.text.toString()

            // Валидация
            when {
                host.isEmpty() -> { showError("Укажите адрес сервера"); return@setOnClickListener }
                username.length < 3 -> { showError("Имя минимум 3 символа"); return@setOnClickListener }
                !email.contains("@") -> { showError("Неверный email"); return@setOnClickListener }
                password.length < 4 -> { showError("Пароль минимум 4 символа"); return@setOnClickListener }
                password != passwordConfirm -> { showError("Пароли не совпадают"); return@setOnClickListener }
            }

            val port = portStr.toIntOrNull() ?: 8888
            setLoading(true)

            if (SocketManager.isConnected) {
                viewModel.register(username, email, password)
            } else {
                viewModel.connect(host, port)
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    if (SocketManager.isConnected) {
                        viewModel.register(username, email, password)
                    } else {
                        setLoading(false)
                        showError("Не удалось подключиться к серверу")
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
