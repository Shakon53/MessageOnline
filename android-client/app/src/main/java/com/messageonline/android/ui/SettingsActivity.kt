package com.messageonline.android.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.messageonline.android.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences("MessageOnline", MODE_PRIVATE)

        // Load saved settings
        binding.switchNotifications.isChecked = prefs.getBoolean("notif_enabled", true)
        binding.switchSound.isChecked         = prefs.getBoolean("sound_enabled", true)
        binding.seekFontSize.progress         = prefs.getInt("font_size_level", 1)
        updateFontSizeLabel(binding.seekFontSize.progress)

        // Save on change
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_enabled", checked).apply()
        }
        binding.switchSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sound_enabled", checked).apply()
        }
        binding.seekFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updateFontSizeLabel(progress)
                prefs.edit().putInt("font_size_level", progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.rowProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.rowChangePassword.setOnClickListener {
            // TODO: implement password change screen
        }
    }

    private fun updateFontSizeLabel(level: Int) {
        binding.tvFontSizeValue.text = when (level) {
            0    -> "Маленький"
            2    -> "Большой"
            else -> "Средний"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
