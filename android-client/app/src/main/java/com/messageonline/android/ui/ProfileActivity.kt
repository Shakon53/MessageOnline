package com.messageonline.android.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.CircleCropTransformation
import com.messageonline.android.databinding.ActivityProfileBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.viewmodel.ChatViewModel

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
    }

    private fun populateProfile() {
        val username = ChatSession.username
        val initial  = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        binding.tvAvatarInitial.text = initial
        binding.tvUsername.text      = username
        binding.tvPhone.text         = ChatSession.phone.ifBlank { "Не указан" }
        binding.etStatusText.setText(ChatSession.statusText)
        binding.tvCharCount.text     = "${ChatSession.statusText.length}/140"

        // Загружаем фото Google если есть
        val avatarUrl = getSharedPreferences("MessageOnline", MODE_PRIVATE)
            .getString("last_avatar", "") ?: ""
        if (avatarUrl.isNotEmpty()) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.load(avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(android.R.drawable.ic_menu_myplaces)
            }
        }
    }

    private fun setupStatusTextWatcher() {
        binding.etStatusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                binding.tvCharCount.text = "$len/140"
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
            Toast.makeText(this, "Профиль обновлён", Toast.LENGTH_SHORT).show()
        }
    }
}
