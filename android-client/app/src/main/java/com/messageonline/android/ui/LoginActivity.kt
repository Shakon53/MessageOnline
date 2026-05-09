package com.messageonline.android.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.messageonline.android.R
import com.messageonline.android.databinding.ActivityLoginBinding
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private var pendingUsername: String? = null
    private var pendingPassword: String? = null

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val email = account.email ?: ""
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.putExtra("verified_phone", email)
                    startActivity(intent)
                } else {
                    showError("Ошибка Google входа: ${authTask.exception?.message}")
                }
            }
        } catch (e: ApiException) {
            showError("Ошибка Google: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        binding.etUsername.setText(prefs.getString("last_username", ""))

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(this) { result ->
            setLoading(false)
            if (result.success) {
                prefs.edit()
                    .putString("last_username", binding.etUsername.text.toString().trim())
                    .apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                showError(result.message.ifEmpty { "Неверное имя пользователя или пароль" })
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTING ->
                    setLoading(true, "Подключение...")

                ChatViewModel.ConnectionStatus.CONNECTED -> {
                    val user = pendingUsername
                    val pass = pendingPassword
                    if (user != null && pass != null) {
                        pendingUsername = null
                        pendingPassword = null
                        setLoading(true, "Вход...")
                        viewModel.login(user, pass)
                    }
                }

                ChatViewModel.ConnectionStatus.ERROR -> {
                    pendingUsername = null
                    pendingPassword = null
                    setLoading(false)
                    showError("Сервер недоступен. Проверьте интернет-соединение.")
                }

                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                showError("Введите логин и пароль")
                return@setOnClickListener
            }

            setLoading(true, "Вход...")

            if (SocketManager.isConnected) {
                viewModel.login(username, password)
            } else {
                pendingUsername = username
                pendingPassword = password
                viewModel.connect()
            }
        }

        binding.tvRegister.setOnClickListener {
            // Сначала выходим из предыдущего Google аккаунта чтобы показать выбор
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.tvStatus.text = message
        binding.tvStatus.visibility = if (message.isNotEmpty() && loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
