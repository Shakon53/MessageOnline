package com.messageonline.android.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.messageonline.android.databinding.ActivityRegisterBinding
import com.messageonline.android.network.ServerConfig
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: ChatViewModel by viewModels()

    private var verifiedEmail: String = ""
    private var pendingRegister: Triple<String, String, String>? = null // username, email, password

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем email от Google Sign-In
        verifiedEmail = intent.getStringExtra("verified_phone") ?: ""
        binding.etPhone.setText(verifiedEmail)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.registerResult.observe(this) { result ->
            setLoading(false)
            if (result.success) {
                Toast.makeText(this, "Регистрация успешна! Войдите в аккаунт",
                    Toast.LENGTH_LONG).show()
                finish()
            } else {
                showError(result.message.ifEmpty { "Ошибка регистрации" })
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED -> {
                    val p = pendingRegister
                    if (p != null) {
                        pendingRegister = null
                        viewModel.register(p.first, p.second, p.third) // username, email, password
                    }
                }
                ChatViewModel.ConnectionStatus.ERROR -> {
                    pendingRegister = null
                    setLoading(false)
                    showError("Сервер недоступен. Проверьте интернет-соединение.")
                }
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnRegister.setOnClickListener {
            if (!validateInput()) return@setOnClickListener

            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            setLoading(true)

            // Всегда переподключаемся для чистого состояния
            pendingRegister = Triple(username, verifiedEmail, password)
            SocketManager.disconnect()
            viewModel.connect()
        }
    }

    private fun validateInput(): Boolean {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etPasswordConfirm.text.toString()

        return when {
            verifiedEmail.isEmpty() -> { showError("Email не получен от Google"); false }
            username.length < 3 -> { showError("Имя слишком короткое (мин. 3 символа)"); false }
            password.length < 4 -> { showError("Пароль минимум 4 символа"); false }
            password != confirm -> { showError("Пароли не совпадают"); false }
            else -> true
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
