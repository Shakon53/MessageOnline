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

    // После Google Sign-In сохраняем данные для авто-входа на сервер
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null  // Firebase UID используется как пароль
    private var pendingEmail: String? = null
    private var isRegisterMode = false  // true = нужна регистрация перед входом

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            setLoading(false)
            showError("Ошибка Google: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupObservers()

        binding.btnGoogleSignIn.setOnClickListener {
            setLoading(true, "Открываем Google...")
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        // Авто-вход если уже есть сохранённый Firebase пользователь
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val savedUsername = prefs.getString("last_username", "") ?: ""
            val savedUid = prefs.getString("last_uid", "") ?: ""
            if (savedUsername.isNotEmpty() && savedUid.isNotEmpty()) {
                setLoading(true, "Вход...")
                connectAndLogin(savedUsername, savedUid, null)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoading(true, "Аутентификация...")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser!!
                val username = sanitizeUsername(user.displayName ?: user.email ?: "User")
                val uid = user.uid
                val email = user.email ?: ""

                val photoUrl = user.photoUrl?.toString() ?: ""
                prefs.edit()
                    .putString("last_username", username)
                    .putString("last_uid", uid)
                    .putString("last_avatar", photoUrl)
                    .apply()

                connectAndLogin(username, uid, email)
            } else {
                setLoading(false)
                showError("Ошибка Firebase: ${task.exception?.message}")
            }
        }
    }

    private fun connectAndLogin(username: String, uid: String, email: String?) {
        pendingUsername = username
        pendingPassword = uid
        pendingEmail = email
        isRegisterMode = false

        setLoading(true, "Подключение...")
        SocketManager.disconnect()
        viewModel.connect()
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(this) { result ->
            if (result.success) {
                setLoading(false)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                // Вход не удался — возможно пользователь не зарегистрирован, попробуем регистрацию
                if (!isRegisterMode) {
                    val user = pendingUsername
                    val pass = pendingPassword
                    val email = pendingEmail
                    if (user != null && pass != null && email != null) {
                        isRegisterMode = true
                        setLoading(true, "Регистрация...")
                        viewModel.register(user, email, pass)
                    } else {
                        setLoading(false)
                        showError(result.message.ifEmpty { "Ошибка входа" })
                    }
                } else {
                    setLoading(false)
                    showError(result.message.ifEmpty { "Ошибка входа" })
                }
            }
        }

        viewModel.registerResult.observe(this) { result ->
            if (result.success) {
                // Регистрация прошла, теперь входим
                val user = pendingUsername
                val pass = pendingPassword
                if (user != null && pass != null) {
                    setLoading(true, "Вход...")
                    viewModel.login(user, pass)
                }
            } else {
                setLoading(false)
                showError(result.message.ifEmpty { "Ошибка регистрации" })
            }
        }

        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED -> {
                    val user = pendingUsername
                    val pass = pendingPassword
                    if (user != null && pass != null && !isRegisterMode) {
                        setLoading(true, "Вход...")
                        viewModel.login(user, pass)
                    }
                }

                ChatViewModel.ConnectionStatus.ERROR -> {
                    pendingUsername = null
                    pendingPassword = null
                    pendingEmail = null
                    isRegisterMode = false
                    setLoading(false)
                }

                else -> {}
            }
        }

        viewModel.notification.observe(this) { msg ->
            if (!msg.isNullOrEmpty() && binding.progressBar.visibility == View.GONE) {
                showError(msg)
            }
        }
    }

    private fun sanitizeUsername(name: String): String {
        // Убираем пробелы и спецсимволы, оставляем буквы/цифры/подчёркивание
        val clean = name.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_]"), "").take(20)
        return if (clean.length >= 3) clean else "user_${System.currentTimeMillis() % 100000}"
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogleSignIn.isEnabled = !loading
        binding.tvStatus.text = message
        binding.tvStatus.visibility = if (message.isNotEmpty() && loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
