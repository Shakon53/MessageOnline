package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import coil.load
import coil.transform.CircleCropTransformation
import com.messageonline.android.databinding.ActivityProfileBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel
import java.io.ByteArrayOutputStream

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ChatViewModel by viewModels()

    // ─── Image picker launcher ─────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) processAndUploadAvatar(uri)
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
        observeProfileUpdate()

        // Avatar click → pick image
        binding.flAvatarContainer.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ─── Populate ──────────────────────────────────────────────────────────────

    private fun populateProfile() {
        val username = ChatSession.username
        val initial  = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        binding.tvAvatarInitial.text = initial
        binding.tvUsername.text      = username
        binding.tvPhone.text         = ChatSession.phone.ifBlank { "Не указан" }
        binding.etStatusText.setText(ChatSession.statusText)
        binding.tvCharCount.text     = "${ChatSession.statusText.length}/140"

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

    // ─── Image processing ──────────────────────────────────────────────────────

    private fun processAndUploadAvatar(uri: Uri) {
        try {
            Toast.makeText(this, "Обработка фото...", Toast.LENGTH_SHORT).show()

            // Decode & compress
            val stream  = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(stream)
            stream.close()

            // Scale down to max 300x300
            val scaled = scaleBitmap(original, 300)

            // Encode to JPEG base64
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            val bytes   = bos.toByteArray()
            val b64     = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$b64"

            // Show locally immediately
            showAvatarImage(dataUrl)

            // Save to prefs
            getSharedPreferences("MessageOnline", MODE_PRIVATE).edit()
                .putString("avatar_${ChatSession.username}", dataUrl)
                .putString("last_avatar", dataUrl)
                .apply()

            // Send to server
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
                binding.tvCharCount.text = "${s?.length ?: 0}/140"
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
}
