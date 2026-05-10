package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.messageonline.android.R
import com.messageonline.android.database.AppDatabase
import com.messageonline.android.databinding.ActivityProfileBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ChatViewModel by viewModels()

    // ─── Image picker launcher ─────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) processAndUploadAvatar(uri)
    }

    // ─── QR scan launcher ─────────────────────────────────────────────────────
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            // Scanned a user ID — prefill the add friend dialog
            val scannedId = result.contents.removePrefix("messageonline://user/")
            showAddFriendFromQR(scannedId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        populateProfile()
        setupStatusTextWatcher()
        setupSaveButton()
        setupLogoutButton()
        setupEditUsername()
        setupQRButton()
        setupDND()
        setupPrivacyToggle()
        observeProfileUpdate()
        observeUsernameChange()
        loadStats()

        // Avatar click → pick image
        binding.flAvatarContainer.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ─── Populate ──────────────────────────────────────────────────────────────

    private fun populateProfile() {
        val username = ChatSession.username
        val initial  = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        binding.tvAvatarInitial.text  = initial
        binding.tvUsername.text       = username
        binding.tvPhone.text          = ChatSession.phone.ifBlank { "Не указан" }
        binding.etStatusText.setText(ChatSession.statusText)
        binding.tvCharCount.text      = "${ChatSession.statusText.length}/140"

        // Header status preview
        binding.tvHeaderStatus.text = ChatSession.statusText.ifBlank { "Нет статуса" }

        // Online dot — always visible (you ARE online if you see this screen)
        binding.viewOnlineDot.visibility = View.VISIBLE

        // Registration date
        if (ChatSession.createdAt > 0L) {
            val fmt = SimpleDateFormat("MMMM yyyy", Locale("ru"))
            binding.tvMemberSince.text = "Участник с ${fmt.format(Date(ChatSession.createdAt))}"
            binding.tvMemberSince.visibility = View.VISIBLE
        } else {
            binding.tvMemberSince.visibility = View.GONE
        }

        // ID with copy
        val myId = ChatSession.userId
        binding.tvUserId.text = "ID: #$myId"
        val copyId = {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("id", "$myId"))
            Toast.makeText(this, "ID #$myId скопирован", Toast.LENGTH_SHORT).show()
        }
        binding.tvUserId.setOnClickListener { copyId() }
        binding.ivCopyId.setOnClickListener { copyId() }

        // Load saved avatar
        loadSavedAvatar()
    }

    private fun loadSavedAvatar() {
        val prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        val avatarData = prefs.getString("avatar_${ChatSession.username}", "")
            ?: prefs.getString("last_avatar", "") ?: ""

        if (avatarData.isNotEmpty()) {
            showAvatarImage(avatarData)
        }
    }

    private fun showAvatarImage(dataOrUrl: String) {
        if (dataOrUrl.startsWith("data:image")) {
            // Coil не поддерживает data: URL — декодируем base64 вручную
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val base64Part = dataOrUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.ivAvatar.setImageBitmap(bitmap)
                            binding.ivAvatar.visibility        = View.VISIBLE
                            binding.tvAvatarInitial.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) { /* оставляем инициал */ }
            }
        } else {
            // Обычный http/https URL — грузим через Coil
            binding.ivAvatar.visibility        = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.load(dataOrUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(
                    onError = { _, _ ->
                        binding.ivAvatar.visibility        = View.GONE
                        binding.tvAvatarInitial.visibility = View.VISIBLE
                    }
                )
            }
        }
    }

    // ─── Image processing ──────────────────────────────────────────────────────

    private fun processAndUploadAvatar(uri: Uri) {
        try {
            Toast.makeText(this, "Обработка фото...", Toast.LENGTH_SHORT).show()

            val stream  = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(stream)
            stream.close()

            val scaled = scaleBitmap(original, 300)

            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            val bytes   = bos.toByteArray()
            val b64     = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$b64"

            showAvatarImage(dataUrl)

            getSharedPreferences("MessageOnline", MODE_PRIVATE).edit()
                .putString("avatar_${ChatSession.username}", dataUrl)
                .putString("last_avatar", dataUrl)
                .apply()

            SocketManager.sendUpdateAvatar(dataUrl)

            Toast.makeText(this, "Фото обновлено ✓", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(src: Bitmap, maxSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxSize && h <= maxSize) return src
        val ratio = minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
        val newW  = (w * ratio).toInt()
        val newH  = (h * ratio).toInt()
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    // ─── Status & Save ─────────────────────────────────────────────────────────

    private fun setupStatusTextWatcher() {
        binding.etStatusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                binding.tvCharCount.text = "$len/140"
                val preview = s?.toString()?.trim()
                val newText = if (preview.isNullOrBlank()) "Нет статуса" else preview
                if (binding.tvHeaderStatus.text != newText) {
                    binding.tvHeaderStatus.animate()
                        .alpha(0f).setDuration(120)
                        .withEndAction {
                            binding.tvHeaderStatus.text = newText
                            binding.tvHeaderStatus.animate().alpha(1f).setDuration(120).start()
                        }.start()
                }
            }
        })
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val newStatus = binding.etStatusText.text.toString().trim()
            viewModel.updateProfile(newStatus)
            binding.btnSave.isEnabled = false
        }
    }

    private fun observeProfileUpdate() {
        viewModel.profileUpdated.observe(this) {
            binding.btnSave.isEnabled = true
            Toast.makeText(this, "Профиль сохранён ✓", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Edit username ─────────────────────────────────────────────────────────

    private fun setupEditUsername() {
        binding.ibEditUsername.setOnClickListener { showChangeUsernameDialog() }
    }

    private fun showChangeUsernameDialog() {
        val editText = EditText(this).apply {
            hint = "Новое имя (3–20 символов)"
            setText(ChatSession.username)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf(InputFilter.LengthFilter(20))
            setTextColor(Color.WHITE)
            setHintTextColor(0x664B5563.toInt())
            setPadding(32, 24, 32, 8)
        }

        MaterialAlertDialogBuilder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
            .setTitle("Изменить имя")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = editText.text.toString().trim()
                when {
                    newName.length < 3 ->
                        Toast.makeText(this, "Минимум 3 символа", Toast.LENGTH_SHORT).show()
                    newName == ChatSession.username ->
                        Toast.makeText(this, "Это уже ваше имя", Toast.LENGTH_SHORT).show()
                    !newName.matches(Regex("[a-zA-Z0-9_]+")) ->
                        Toast.makeText(this, "Только латинские буквы, цифры и _", Toast.LENGTH_SHORT).show()
                    else -> {
                        viewModel.changeUsername(newName)
                        binding.btnSave.isEnabled = false
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun observeUsernameChange() {
        viewModel.usernameChangeResult.observe(this) { result ->
            binding.btnSave.isEnabled = true
            if (result.success) {
                val newName = result.message
                // Update local session + UI
                ChatSession.username = newName
                binding.tvUsername.text = newName
                binding.tvAvatarInitial.text = newName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                // Re-save prefs with new username key for avatar
                val prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
                val avatar = prefs.getString("last_avatar", "") ?: ""
                if (avatar.isNotEmpty()) {
                    prefs.edit().putString("avatar_$newName", avatar).apply()
                }
                prefs.edit().putString("last_username", newName).apply()
                Toast.makeText(this, "Имя изменено на «$newName» ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, result.message.ifBlank { "Не удалось изменить имя" }, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── QR Code ───────────────────────────────────────────────────────────────

    private fun setupQRButton() {
        binding.btnShowQR.setOnClickListener { showQRDialog() }
    }

    private fun showQRDialog() {
        val userId = ChatSession.userId
        val qrContent = "messageonline://user/$userId"

        val bitmap = generateQRBitmap(qrContent, 600)
            ?: run { Toast.makeText(this, "Ошибка генерации QR", Toast.LENGTH_SHORT).show(); return }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Мой QR-код")
            .setMessage("ID: #$userId\nПокажите друзьям для добавления")
            .setView(imageView)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Сканировать") { _, _ -> launchQRScanner() }
            .show()
    }

    private fun generateQRBitmap(content: String, size: Int): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h) { index ->
                val x = index % w
                val y = index / w
                if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
        } catch (e: Exception) { null }
    }

    private fun launchQRScanner() {
        val opts = ScanOptions().apply {
            setPrompt("Наведите на QR-код друга")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        }
        qrScanLauncher.launch(opts)
    }

    private fun showAddFriendFromQR(userId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Добавить друга")
            .setMessage("Добавить пользователя с ID: #$userId ?")
            .setPositiveButton("Добавить") { _, _ ->
                val id = userId.toIntOrNull()
                if (id != null) {
                    SocketManager.sendFriendAdd(id)
                    Toast.makeText(this, "Запрос отправлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Неверный QR-код", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ─── Do Not Disturb ────────────────────────────────────────────────────────

    private fun setupDND() {
        val prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        // Restore saved state without triggering listener
        binding.switchDND.isChecked = prefs.getBoolean("dnd_enabled", false)

        binding.switchDND.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dnd_enabled", isChecked).apply()
            val msg = if (isChecked) "Режим «Не беспокоить» включён" else "Уведомления включены"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Privacy Toggle ────────────────────────────────────────────────────────

    private fun setupPrivacyToggle() {
        val prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)
        val savedMode = prefs.getString("privacy_mode", "all") ?: "all"

        // Restore saved state (server uses "friends_only", "all")
        when (savedMode) {
            "friends_only" -> binding.togglePrivacy.check(R.id.btnPrivacyFriends)
            else           -> binding.togglePrivacy.check(R.id.btnPrivacyAll)
        }

        binding.togglePrivacy.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.btnPrivacyFriends) "friends_only" else "all"
            prefs.edit().putString("privacy_mode", mode).apply()
            viewModel.updatePrivacy(mode)
            val msg = if (mode == "friends_only") "Только друзья могут писать вам" else "Все могут писать вам"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Stats ─────────────────────────────────────────────────────────────────

    private fun loadStats() {
        viewModel.friends.observe(this) { friends ->
            binding.tvStatFriends.text = friends.size.toString()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val count = AppDatabase.getInstance(this@ProfileActivity)
                .messageDao()
                .countSentMessages(ChatSession.username)
            withContext(Dispatchers.Main) {
                binding.tvStatMessages.text = count.toString()
            }
        }
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            SocketManager.disconnect()

            getSharedPreferences("MessageOnline", MODE_PRIVATE).edit()
                .remove("last_username")
                .remove("last_uid")
                .remove("last_avatar")
                .apply()

            ChatSession.logout()
            FirebaseAuth.getInstance().signOut()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
