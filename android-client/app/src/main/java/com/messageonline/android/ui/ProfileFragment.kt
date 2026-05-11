package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.messageonline.android.databinding.FragmentProfileBinding
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

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by activityViewModels()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) processAndUploadAvatar(uri)
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            showAddFriendFromQR(result.contents.removePrefix("messageonline://user/"))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateProfile()
        setupStatusTextWatcher()
        setupSaveButton()
        setupLogoutButton()
        setupEditUsername()
        setupQRButton()
        setupDND()
        setupPrivacyToggle()
        setupThemeToggle()
        observeProfileUpdate()
        observeUsernameChange()
        loadStats()
        binding.flAvatarContainer.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun populateProfile() {
        val username = ChatSession.username
        val initial  = username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        binding.tvAvatarInitial.text  = initial
        binding.tvUsername.text       = username
        binding.tvPhone.text          = ChatSession.phone.ifBlank { "Не указан" }
        binding.etStatusText.setText(ChatSession.statusText)
        binding.tvCharCount.text      = "${ChatSession.statusText.length}/140"
        binding.tvHeaderStatus.text   = ChatSession.statusText.ifBlank { "Нет статуса" }
        binding.viewOnlineDot.visibility = View.VISIBLE

        if (ChatSession.createdAt > 0L) {
            val fmt = SimpleDateFormat("MMMM yyyy", Locale("ru"))
            binding.tvMemberSince.text       = "Участник с ${fmt.format(Date(ChatSession.createdAt))}"
            binding.tvMemberSince.visibility = View.VISIBLE
        } else {
            binding.tvMemberSince.visibility = View.GONE
        }

        val myId   = ChatSession.userId
        binding.tvUserId.text = "ID: #$myId"
        val copyId = {
            val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("id", "$myId"))
            Toast.makeText(requireContext(), "ID #$myId скопирован", Toast.LENGTH_SHORT).show()
        }
        binding.tvUserId.setOnClickListener { copyId() }
        binding.ivCopyId.setOnClickListener { copyId() }

        loadSavedAvatar()
    }

    private fun loadSavedAvatar() {
        val prefs = requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        val avatarData = prefs.getString("avatar_${ChatSession.username}", "")
            ?: prefs.getString("last_avatar", "") ?: ""
        if (avatarData.isNotEmpty()) showAvatarImage(avatarData)
    }

    private fun showAvatarImage(dataOrUrl: String) {
        if (dataOrUrl.startsWith("data:image")) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bytes  = Base64.decode(dataOrUrl.substringAfter("base64,"), Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null && _binding != null) {
                            binding.ivAvatar.setImageBitmap(bitmap)
                            binding.ivAvatar.visibility        = View.VISIBLE
                            binding.tvAvatarInitial.visibility = View.GONE
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            binding.ivAvatar.visibility        = View.VISIBLE
            binding.tvAvatarInitial.visibility = View.GONE
            binding.ivAvatar.load(dataOrUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(onError = { _, _ ->
                    binding.ivAvatar.visibility        = View.GONE
                    binding.tvAvatarInitial.visibility = View.VISIBLE
                })
            }
        }
    }

    private fun processAndUploadAvatar(uri: Uri) {
        try {
            Toast.makeText(requireContext(), "Обработка фото...", Toast.LENGTH_SHORT).show()
            val stream   = requireContext().contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(stream)
            stream.close()

            val scaled  = scaleBitmap(original, 300)
            val bos     = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            val dataUrl = "data:image/jpeg;base64,${Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)}"

            showAvatarImage(dataUrl)
            requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE).edit()
                .putString("avatar_${ChatSession.username}", dataUrl)
                .putString("last_avatar", dataUrl)
                .apply()
            SocketManager.sendUpdateAvatar(dataUrl)
            Toast.makeText(requireContext(), "Фото обновлено ✓", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(src: Bitmap, maxSize: Int): Bitmap {
        val w = src.width; val h = src.height
        if (w <= maxSize && h <= maxSize) return src
        val ratio = minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    private fun setupStatusTextWatcher() {
        binding.etStatusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tvCharCount.text = "${s?.length ?: 0}/140"
                val newText = s?.toString()?.trim()?.ifBlank { "Нет статуса" } ?: "Нет статуса"
                if (binding.tvHeaderStatus.text != newText) {
                    binding.tvHeaderStatus.animate().alpha(0f).setDuration(120).withEndAction {
                        binding.tvHeaderStatus.text = newText
                        binding.tvHeaderStatus.animate().alpha(1f).setDuration(120).start()
                    }.start()
                }
            }
        })
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            viewModel.updateProfile(binding.etStatusText.text.toString().trim())
            binding.btnSave.isEnabled = false
        }
    }

    private fun observeProfileUpdate() {
        viewModel.profileUpdated.observe(viewLifecycleOwner) {
            binding.btnSave.isEnabled = true
            Toast.makeText(requireContext(), "Профиль сохранён ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEditUsername() {
        binding.ibEditUsername.setOnClickListener {
            val et = EditText(requireContext()).apply {
                hint = "Новое имя (3–20 символов)"
                setText(ChatSession.username)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                filters = arrayOf(InputFilter.LengthFilter(20))
                setTextColor(Color.WHITE)
                setHintTextColor(0x664B5563.toInt())
                setPadding(32, 24, 32, 8)
            }
            MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Изменить имя")
                .setView(et)
                .setPositiveButton("Сохранить") { _, _ ->
                    val newName = et.text.toString().trim()
                    when {
                        newName.length < 3                         -> Toast.makeText(requireContext(), "Минимум 3 символа", Toast.LENGTH_SHORT).show()
                        newName == ChatSession.username            -> Toast.makeText(requireContext(), "Это уже ваше имя", Toast.LENGTH_SHORT).show()
                        !newName.matches(Regex("[a-zA-Z0-9_]+"))  -> Toast.makeText(requireContext(), "Только латинские буквы, цифры и _", Toast.LENGTH_SHORT).show()
                        else                                       -> { viewModel.changeUsername(newName); binding.btnSave.isEnabled = false }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun observeUsernameChange() {
        viewModel.usernameChangeResult.observe(viewLifecycleOwner) { result ->
            binding.btnSave.isEnabled = true
            if (result.success) {
                val newName = result.message
                ChatSession.username = newName
                binding.tvUsername.text       = newName
                binding.tvAvatarInitial.text  = newName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                val prefs = requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
                val avatar = prefs.getString("last_avatar", "") ?: ""
                if (avatar.isNotEmpty()) prefs.edit().putString("avatar_$newName", avatar).apply()
                prefs.edit().putString("last_username", newName).apply()
                Toast.makeText(requireContext(), "Имя изменено на «$newName» ✓", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), result.message.ifBlank { "Не удалось изменить имя" }, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupQRButton() {
        binding.btnShowQR.setOnClickListener {
            val userId    = ChatSession.userId
            val bitmap    = generateQRBitmap("messageonline://user/$userId", 600)
                ?: run { Toast.makeText(requireContext(), "Ошибка генерации QR", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val imageView = ImageView(requireContext()).apply {
                setImageBitmap(bitmap)
                val pad = (24 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Мой QR-код")
                .setMessage("ID: #$userId\nПокажите друзьям для добавления")
                .setView(imageView)
                .setPositiveButton("Закрыть", null)
                .setNeutralButton("Сканировать") { _, _ -> launchQRScanner() }
                .show()
        }
    }

    private fun generateQRBitmap(content: String, size: Int): Bitmap? {
        return try {
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val w = matrix.width; val h = matrix.height
            Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { bmp ->
                bmp.setPixels(IntArray(w * h) { i ->
                    if (matrix[i % w, i / w]) Color.BLACK else Color.WHITE
                }, 0, w, 0, 0, w, h)
            }
        } catch (_: Exception) { null }
    }

    private fun launchQRScanner() {
        qrScanLauncher.launch(ScanOptions().apply {
            setPrompt("Наведите на QR-код друга")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        })
    }

    private fun showAddFriendFromQR(userId: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить друга")
            .setMessage("Добавить пользователя с ID: #$userId ?")
            .setPositiveButton("Добавить") { _, _ ->
                val id = userId.toIntOrNull()
                if (id != null) { SocketManager.sendFriendAdd(id); Toast.makeText(requireContext(), "Запрос отправлен", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(requireContext(), "Неверный QR-код", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupDND() {
        val prefs = requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        binding.switchDND.isChecked = prefs.getBoolean("dnd_enabled", false)
        binding.switchDND.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dnd_enabled", isChecked).apply()
            Toast.makeText(requireContext(), if (isChecked) "Режим «Не беспокоить» включён" else "Уведомления включены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPrivacyToggle() {
        val prefs      = requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        val savedMode  = prefs.getString("privacy_mode", "all") ?: "all"
        if (savedMode == "friends_only") binding.togglePrivacy.check(R.id.btnPrivacyFriends)
        else                             binding.togglePrivacy.check(R.id.btnPrivacyAll)

        binding.togglePrivacy.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.btnPrivacyFriends) "friends_only" else "all"
            prefs.edit().putString("privacy_mode", mode).apply()
            viewModel.updatePrivacy(mode)
            Toast.makeText(requireContext(), if (mode == "friends_only") "Только друзья могут писать вам" else "Все могут писать вам", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStats() {
        viewModel.friends.observe(viewLifecycleOwner) { friends ->
            if (_binding != null) binding.tvStatFriends.text = friends.size.toString()
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val count = AppDatabase.getInstance(requireContext()).messageDao().countSentMessages(ChatSession.username)
            withContext(Dispatchers.Main) {
                if (_binding != null) binding.tvStatMessages.text = count.toString()
            }
        }
    }

    private fun setupThemeToggle() {
        val prefs = requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE)
        val saved = prefs.getString("app_theme", "dark") ?: "dark"
        when (saved) {
            "light"  -> binding.toggleTheme.check(R.id.btnThemeLight)
            "amoled" -> binding.toggleTheme.check(R.id.btnThemeAmoled)
            else     -> binding.toggleTheme.check(R.id.btnThemeDark)
        }
        binding.toggleTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val (mode, nightMode) = when (checkedId) {
                R.id.btnThemeLight  -> "light"  to androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeAmoled -> "amoled" to androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else                -> "dark"   to androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            }
            prefs.edit().putString("app_theme", mode).apply()
            if (mode == "amoled") {
                requireActivity().window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
            }
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
            requireContext().getSharedPreferences("MessageOnline", Context.MODE_PRIVATE).edit().clear().apply()
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }
}
