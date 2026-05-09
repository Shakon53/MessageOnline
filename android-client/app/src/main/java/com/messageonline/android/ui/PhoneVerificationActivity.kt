package com.messageonline.android.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.messageonline.android.databinding.ActivityPhoneVerifyBinding
import java.util.concurrent.TimeUnit

class PhoneVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneVerifyBinding
    private lateinit var auth: FirebaseAuth

    private var storedVerificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Автоматически ставим + в начало
        binding.etPhone.setText("+")
        binding.etPhone.setSelection(1)

        setupPhoneWatcher()
        setupClickListeners()
    }

    private fun setupPhoneWatcher() {
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                var text = s.toString()

                // Всегда начинается с +
                if (!text.startsWith("+")) {
                    text = "+$text"
                    binding.etPhone.setText(text)
                    binding.etPhone.setSelection(text.length)
                }

                // Активируем кнопку только если номер достаточно длинный
                val digits = text.replace(Regex("[^0-9]"), "")
                binding.btnSendCode.isEnabled = digits.length >= 10

                isFormatting = false
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSendCode.setOnClickListener {
            val phone = normalizePhone(binding.etPhone.text.toString().trim())

            if (phone == null) {
                showError("Введите номер в формате +7 999 000 00 00")
                return@setOnClickListener
            }

            sendVerificationCode(phone)
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etOtpCode.text.toString().trim()
            if (code.length != 6) {
                showError("Введите 6-значный код из SMS")
                return@setOnClickListener
            }
            val verificationId = storedVerificationId ?: run {
                showError("Сначала запросите код SMS")
                return@setOnClickListener
            }
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            signInWithCredential(credential)
        }

        binding.tvResend.setOnClickListener {
            val phone = normalizePhone(binding.etPhone.text.toString().trim())
            val token = resendToken
            if (phone != null && token != null) {
                sendVerificationCode(phone, token)
            } else {
                showError("Введите номер и нажмите 'Отправить код SMS'")
            }
        }
    }

    // Нормализует номер: убирает пробелы, тире, скобки; проверяет длину
    private fun normalizePhone(input: String): String? {
        val digits = input.replace(Regex("[^0-9+]"), "")
        // Должно быть + и минимум 10 цифр
        val onlyDigits = digits.replace("+", "")
        if (onlyDigits.length < 10 || onlyDigits.length > 15) return null
        return if (digits.startsWith("+")) digits else "+$digits"
    }

    private fun sendVerificationCode(
        phone: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ) {
        setPhoneLoading(true)

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    setPhoneLoading(false)
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    setPhoneLoading(false)
                    showError("Firebase ошибка: ${e.message}")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    storedVerificationId = verificationId
                    this@PhoneVerificationActivity.resendToken = token
                    setPhoneLoading(false)
                    showOtpSection(phone)
                }
            })

        if (resendToken != null) builder.setForceResendingToken(resendToken)

        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        setOtpLoading(true)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setOtpLoading(false)
                if (task.isSuccessful) {
                    val phone = normalizePhone(binding.etPhone.text.toString().trim()) ?: ""
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.putExtra("verified_phone", phone)
                    startActivity(intent)
                    finish()
                } else {
                    showError("Неверный код. Попробуйте ещё раз.")
                }
            }
    }

    private fun showOtpSection(phone: String) {
        binding.cardOtp.visibility = View.VISIBLE
        binding.btnSendCode.text = "Отправить новый код"
        binding.etPhone.isEnabled = false
        Toast.makeText(this, "Код отправлен на $phone", Toast.LENGTH_SHORT).show()
    }

    private fun setPhoneLoading(loading: Boolean) {
        binding.progressBarPhone.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendCode.isEnabled = !loading
    }

    private fun setOtpLoading(loading: Boolean) {
        binding.progressBarOtp.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerify.isEnabled = !loading
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
