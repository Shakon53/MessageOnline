package com.messageonline.android.ui

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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

    // pending state for WS auth flow
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null
    private var pendingPhone: String?    = null
    private var pendingMode: Mode = Mode.NONE

    private enum class Mode { NONE, MANUAL_LOGIN, MANUAL_REGISTER, GOOGLE_LOGIN, GOOGLE_REGISTER }

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
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        auth  = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setupTabs()
        setupClickListeners()
        setupObservers()
        tryAutoLogin()
    }

    // ── Tab switching ───────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.tabLogin.setOnClickListener    { switchTab(isLogin = true) }
        binding.tabRegister.setOnClickListener { switchTab(isLogin = false) }
    }

    private fun switchTab(isLogin: Boolean) {
        if (isLogin) {
            binding.tabLogin.setBackgroundColor(Color.parseColor("#6366F1"))
            binding.tabLogin.setTextColor(Color.WHITE)
            binding.tabRegister.setBackgroundColor(Color.TRANSPARENT)
            binding.tabRegister.setTextColor(Color.parseColor("#64748B"))
            binding.layoutLoginForm.visibility    = View.VISIBLE
            binding.layoutRegisterForm.visibility = View.GONE
        } else {
            binding.tabRegister.setBackgroundColor(Color.parseColor("#6366F1"))
            binding.tabRegister.setTextColor(Color.WHITE)
            binding.tabLogin.setBackgroundColor(Color.TRANSPARENT)
            binding.tabLogin.setTextColor(Color.parseColor("#64748B"))
            binding.layoutRegisterForm.visibility = View.VISIBLE
            binding.layoutLoginForm.visibility    = View.GONE
        }
    }

    // ── Click listeners ─────────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Manual Login
        binding.btnLogin.setOnClickListener {
            val username = binding.etLoginUsername.text.toString().trim()
            val password = binding.etLoginPassword.text.toString()
            if (username.length < 3) { showError("Имя пользователя слишком короткое"); return@setOnClickListener }
            if (password.length < 4) { showError("Пароль минимум 4 символа");           return@setOnClickListener }
            startManualLogin(username, password)
        }

        // Manual Register
        binding.btnRegister.setOnClickListener {
            val username = binding.etRegUsername.text.toString().trim()
            val phone    = binding.etRegPhone.text.toString().trim()
            val password = binding.etRegPassword.text.toString()
            val confirm  = binding.etRegPasswordConfirm.text.toString()
            if (username.length < 3)    { showError("Имя слишком короткое (мин. 3 символа)"); return@setOnClickListener }
            if (password.length < 4)    { showError("Пароль минимум 4 символа");              return@setOnClickListener }
            if (password != confirm)    { showError("Пароли не совпадают");                    return@setOnClickListener }
            startManualRegister(username, phone.ifEmpty { "" }, password)
        }

        // Google
        binding.btnGoogleSignIn.setOnClickListener {
            setLoading(true, "Открываем Google...")
            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }
    }

    // ── Manual auth ─────────────────────────────────────────────────────────────

    private fun startManualLogin(username: String, password: String) {
        pendingUsername = username
        pendingPassword = password
        pendingMode     = Mode.MANUAL_LOGIN
        setLoading(true, "Подключение...")
        SocketManager.disconnect()
        viewModel.connect()
    }

    private fun startManualRegister(username: String, phone: String, password: String) {
        pendingUsername = username
        pendingPassword = password
        pendingPhone    = phone
        pendingMode     = Mode.MANUAL_REGISTER
        setLoading(true, "Подключение...")
        SocketManager.disconnect()
        viewModel.connect()
    }

    // ── Google auth ─────────────────────────────────────────────────────────────

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoading(true, "Аутентификация...")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser!!
                val username = sanitizeUsername(user.displayName ?: user.email ?: "User")
                val uid      = user.uid
                val email    = user.email ?: ""
                prefs.edit()
                    .putString("last_username", username)
                    .putString("last_uid",      uid)
                    .putString("last_avatar",   user.photoUrl?.toString() ?: "")
                    .apply()
                pendingUsername = username
                pendingPassword = uid
                pendingPhone    = email
                pendingMode     = Mode.GOOGLE_LOGIN
                setLoading(true, "Подключение...")
                SocketManager.disconnect()
                viewModel.connect()
            } else {
                setLoading(false)
                showError("Ошибка Firebase: ${task.exception?.message}")
            }
        }
    }

    private fun tryAutoLogin() {
        val currentUser = auth.currentUser ?: return
        val savedUsername = prefs.getString("last_username", "") ?: ""
        val savedUid      = prefs.getString("last_uid",      "") ?: ""
        if (savedUsername.isNotEmpty() && savedUid.isNotEmpty()) {
            pendingUsername = savedUsername
            pendingPassword = savedUid
            pendingPhone    = currentUser.email ?: ""
            pendingMode     = Mode.GOOGLE_LOGIN
            setLoading(true, "Вход...")
            SocketManager.disconnect()
            viewModel.connect()
        }
    }

    // ── Observers ────────────────────────────────────────────────────────────────

    private fun setupObservers() {

        viewModel.connectionStatus.observe(this) { status ->
            when (status) {
                ChatViewModel.ConnectionStatus.CONNECTED -> onConnected()
                ChatViewModel.ConnectionStatus.ERROR     -> {
                    clearPending()
                    setLoading(false)
                    showError("Сервер недоступен. Проверьте интернет.")
                }
                else -> {}
            }
        }

        viewModel.loginResult.observe(this) { result ->
            if (result.success) {
                setLoading(false)
                goToChats()
            } else {
                val msg = result.message
                if (msg.contains("заблокирован", ignoreCase = true)) {
                    setLoading(false)
                    clearPending()
                    showBlockedDialog(msg)
                } else if (pendingMode == Mode.GOOGLE_LOGIN) {
                    // Google: try register
                    val user  = pendingUsername
                    val pass  = pendingPassword
                    val phone = pendingPhone
                    if (user != null && pass != null) {
                        pendingMode = Mode.GOOGLE_REGISTER
                        setLoading(true, "Регистрация...")
                        viewModel.register(user, phone ?: "", pass)
                    } else {
                        setLoading(false); clearPending()
                        showError(msg.ifEmpty { "Ошибка входа" })
                    }
                } else {
                    setLoading(false); clearPending()
                    showError(msg.ifEmpty { "Неверное имя пользователя или пароль" })
                }
            }
        }

        viewModel.registerResult.observe(this) { result ->
            if (result.success) {
                val user = pendingUsername
                val pass = pendingPassword
                if (user != null && pass != null) {
                    setLoading(true, "Вход...")
                    viewModel.login(user, pass)
                }
            } else {
                setLoading(false); clearPending()
                showError(result.message.ifEmpty { "Ошибка регистрации" })
            }
        }

        viewModel.notification.observe(this) { msg ->
            if (!msg.isNullOrEmpty() && binding.progressBar.visibility == View.GONE) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onConnected() {
        val user = pendingUsername ?: return
        val pass = pendingPassword ?: return
        when (pendingMode) {
            Mode.MANUAL_LOGIN    -> { setLoading(true, "Вход..."); viewModel.login(user, pass) }
            Mode.MANUAL_REGISTER -> { setLoading(true, "Регистрация..."); viewModel.register(user, pendingPhone ?: "", pass) }
            Mode.GOOGLE_LOGIN    -> { setLoading(true, "Вход..."); viewModel.login(user, pass) }
            else -> {}
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun goToChats() {
        startActivity(Intent(this, ChatsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun clearPending() {
        pendingUsername = null; pendingPassword = null; pendingPhone = null
        pendingMode = Mode.NONE
    }

    private fun showBlockedDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("⛔ Аккаунт заблокирован")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        binding.progressBar.visibility    = if (loading) View.VISIBLE  else View.GONE
        binding.btnLogin.isEnabled        = !loading
        binding.btnRegister.isEnabled     = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
        binding.tvStatus.text             = message
        binding.tvStatus.visibility = if (message.isNotEmpty() && loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun sanitizeUsername(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_]"), "").take(20)
        return if (clean.length >= 3) clean else "user_${System.currentTimeMillis() % 100000}"
    }
}
