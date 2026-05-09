package com.messageonline.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.messageonline.android.databinding.ActivitySplashBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Logo pulse animation
        ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0.85f, 1.05f, 1.0f).apply {
            duration = 700
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0.85f, 1.05f, 1.0f).apply {
            duration = 700
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Dots pulse animation
        animateDots()

        // Navigate after delay
        lifecycleScope.launch {
            delay(1600)
            navigateNext()
        }
    }

    private fun animateDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { index, dot ->
            ObjectAnimator.ofFloat(dot, "alpha", 0.2f, 1.0f, 0.2f).apply {
                duration = 900
                startDelay = index * 200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                start()
            }
        }
    }

    private fun navigateNext() {
        val dest = if (ChatSession.isLoggedIn()) {
            SocketManager.connect()
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(dest.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
