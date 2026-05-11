package com.messageonline.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.messageonline.android.R
import com.messageonline.android.adapter.MessageAdapter
import com.messageonline.android.databinding.ActivityPrivateChatBinding
import com.messageonline.android.model.ChatMessage
import com.messageonline.android.model.ChatSession
import com.messageonline.android.model.Conversation
import com.messageonline.android.viewmodel.ChatViewModel
import java.io.ByteArrayOutputStream
import java.io.File

class PrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var peerUsername: String

    // ─── Voice recording ───────────────────────────────────────────────────────
    private var mediaRecorder: MediaRecorder? = null
    private var audioTempFile: File? = null
    private var isRecording = false
    private var recordingStartTime = 0L

    // ─── Image picker ──────────────────────────────────────────────────────────
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { sendImage(it) }
    }

    // ─── Permission launchers ──────────────────────────────────────────────────
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(this, "Нет разрешения на запись звука", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private val requestImagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) pickImageLauncher.launch("image/*")
    }

    // ──────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // AMOLED: pure black background
        val savedTheme = getSharedPreferences("MessageOnline", MODE_PRIVATE)
            .getString("app_theme", "dark") ?: "dark"
        if (savedTheme == "amoled") {
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
            binding.root.setBackgroundColor(android.graphics.Color.BLACK)
        }

        peerUsername = intent.getStringExtra("peer_username") ?: run { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        binding.tvPeerName.text     = peerUsername
        binding.tvPeerInitial.text  = peerUsername.first().uppercaseChar().toString()
        binding.tvTypingStatus.text = "офлайн"

        viewModel.currentPrivatePeer = peerUsername
        setupRecyclerView()
        setupObservers()
        setupClickListeners()

        viewModel.loadPrivateHistory(peerUsername)
        viewModel.markAllRead(peerUsername)
        viewModel.refreshUsers()   // populate onlineUsers for status indicator
        viewModel.refreshFriends() // fallback online status via friends list

        // Pre-fill from quick reply (when socket was disconnected)
        val prefill = intent.getStringExtra("prefill_text")
        if (!prefill.isNullOrBlank()) {
            binding.etMessage.setText(prefill)
            binding.etMessage.setSelection(prefill.length)
            binding.etMessage.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reattachCallbacks()
        viewModel.refreshUsers()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            mutableListOf(),
            ChatSession.username,
            onDeleteMessage  = { msg -> viewModel.deleteLocalMessage(msg) },
            onEditMessage    = { msg, newContent -> viewModel.editMessage(msg, newContent) },
            onReplyMessage   = { msg -> showReplyBar(msg) },
            onForwardMessage = { msg -> showForwardDialog(msg) },
            onDeleteForAll   = { msg -> viewModel.deleteForEveryone(msg) }
        )
        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.apply {
            adapter = messageAdapter
            this.layoutManager = this@PrivateChatActivity.layoutManager
        }

        ItemTouchHelper(buildSwipeHelper()).attachToRecyclerView(binding.rvMessages)

        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last  = layoutManager.findLastCompletelyVisibleItemPosition()
                val total = messageAdapter.itemCount - 1
                if (last < total - 2) binding.fabScrollDown.show() else binding.fabScrollDown.hide()
            }
        })
    }

    // ─── Swipe-to-reply ────────────────────────────────────────────────────────

    private fun buildSwipeHelper(): ItemTouchHelper.SimpleCallback {
        return object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private var replyTriggered = false
            private val triggerDx = 180f
            private val icon by lazy {
                ContextCompat.getDrawable(this@PrivateChatActivity, R.drawable.ic_reply)!!
                    .apply { setTint(0xFFFFFFFF.toInt()) }
            }
            private val iconSize = (42 * resources.displayMetrics.density).toInt()

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                messageAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 10f
            override fun getSwipeEscapeVelocity(defaultValue: Float) = Float.MAX_VALUE

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE) {
                    super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                if (dX >= triggerDx && !replyTriggered) {
                    replyTriggered = true
                    val msg = messageAdapter.getMessageAt(vh.bindingAdapterPosition)
                    if (msg != null) showReplyBar(msg)
                }
                if (!isCurrentlyActive) replyTriggered = false

                val cappedDx = minOf(dX, triggerDx + 20f)
                val itemView = vh.itemView
                val alpha    = (cappedDx / triggerDx).coerceIn(0f, 1f)
                icon.alpha   = (alpha * 255).toInt()
                val iconTop  = itemView.top + (itemView.height - iconSize) / 2
                val iconLeft = itemView.left + dpToPx(16)
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                icon.draw(c)

                super.onChildDraw(c, rv, vh, cappedDx, dY, actionState, isCurrentlyActive)
            }

            private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
        }
    }

    private fun showReplyBar(msg: ChatMessage) {
        viewModel.replyToMessage = msg
        binding.layoutReplyBar.visibility = View.VISIBLE
        binding.tvReplyToName.text = msg.senderUsername
        binding.tvReplyToText.text = msg.content
        binding.etMessage.requestFocus()
    }

    private fun hideReplyBar() {
        viewModel.replyToMessage = null
        binding.layoutReplyBar.visibility = View.GONE
    }

    private fun showForwardDialog(msg: ChatMessage) {
        val conversations = viewModel.conversations.value ?: return
        val names = conversations.map { it.peerUsername }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Переслать в...")
            .setItems(names) { _, which ->
                val target = conversations[which]
                val fwdText = "🔁 ${msg.senderUsername}:\n${msg.content}"
                if (target.isGlobal) {
                    viewModel.sendGlobalMessage(fwdText)
                } else {
                    viewModel.sendPrivateMessage(target.peerUsername, fwdText)
                }
            }
            .show()
    }

    // ─── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.privateMessages.observe(this) { allMessages ->
            val filtered = allMessages.filter { msg ->
                val recv = msg.receiverUsername.orEmpty()
                val send = msg.senderUsername
                !msg.isGlobal &&
                ((send == peerUsername && recv == ChatSession.username)
                        || (send == ChatSession.username && recv == peerUsername))
            }
            messageAdapter.setMessages(filtered)
            if (filtered.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(filtered.size - 1)
                viewModel.markAllRead(peerUsername)
            }
        }

        // Track actual online status to restore after typing indicator clears
        viewModel.onlineUsers.observe(this) { users ->
            val isOnline = users.any { it.username == peerUsername }
            // Only update if not currently showing "typing"
            if (binding.tvTypingStatus.text != "печатает...") {
                binding.tvTypingStatus.text = if (isOnline) "В сети" else "офлайн"
            }
        }

        viewModel.friends.observe(this) { friends ->
            // Also check friends list for online status (populated earlier than onlineUsers)
            val onlineUsers = viewModel.onlineUsers.value ?: emptyList()
            if (onlineUsers.isEmpty()) {
                val friend = friends.firstOrNull { it.username == peerUsername }
                if (friend != null && binding.tvTypingStatus.text != "печатает...") {
                    binding.tvTypingStatus.text = if (friend.isOnline) "В сети" else "офлайн"
                }
            }
        }

        viewModel.typing.observe(this) { (sender, isTyping) ->
            if (sender == peerUsername) {
                if (isTyping) {
                    binding.tvTypingStatus.text = "печатает..."
                } else {
                    // Restore actual online status
                    val isOnline = viewModel.onlineUsers.value?.any { it.username == peerUsername } == true
                    binding.tvTypingStatus.text = if (isOnline) "В сети" else "офлайн"
                }
            }
        }
    }

    // ─── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener { v ->
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                v.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
                viewModel.sendTyping(peerUsername, false)
                viewModel.sendPrivateMessage(peerUsername, text)
                binding.etMessage.text?.clear()
                hideReplyBar()
                binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }

        binding.btnCancelReply.setOnClickListener { hideReplyBar() }

        binding.fabScrollDown.setOnClickListener {
            binding.rvMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }

        binding.etMessage.addTextChangedListener(object : TextWatcher {
            private var wasTyping = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typing = !s.isNullOrBlank()
                if (typing != wasTyping) {
                    wasTyping = typing
                    viewModel.sendTyping(peerUsername, typing)
                }
            }
        })

        // Attach / photo button
        binding.btnAttach.setOnClickListener {
            launchImagePicker()
        }

        // Mic button — long press to record, tap to show hint
        binding.btnMic.setOnClickListener {
            android.widget.Toast.makeText(this, "Удерживайте для записи", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnMic.setOnLongClickListener {
            startVoiceRecording()
            true
        }

        binding.btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        v.performClick()
                        stopVoiceRecordingAndSend()
                    }
                }
            }
            false
        }
    }

    // ─── Image handling ────────────────────────────────────────────────────────

    private fun launchImagePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*")
            } else {
                requestImagePermission.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*")
            } else {
                requestImagePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    private fun sendImage(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale to max 800x800
            val scaled = scaleBitmap(original, 800, 800)

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64"

            viewModel.sendPrivateMessage(peerUsername, dataUrl, "image")
            binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Ошибка отправки фото", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxW && h <= maxH) return bitmap
        val ratio = minOf(maxW.toFloat() / w, maxH.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    // ─── Voice recording ───────────────────────────────────────────────────────

    private fun startVoiceRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        try {
            audioTempFile = File.createTempFile("voice_", ".amr", cacheDir)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioTempFile!!.absolutePath)
                setMaxDuration(60_000) // 60 seconds max
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopVoiceRecordingAndSend()
                    }
                }
                prepare()
                start()
            }
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            binding.btnMic.setColorFilter(android.graphics.Color.RED)
            android.widget.Toast.makeText(this, "Запись...", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Ошибка записи", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceRecordingAndSend() {
        if (!isRecording) return
        isRecording = false
        binding.btnMic.clearColorFilter()
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            val file = audioTempFile ?: return
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:audio/amr;base64,$base64"

            viewModel.sendPrivateMessage(peerUsername, dataUrl, "audio")
            binding.rvMessages.scrollToPosition(messageAdapter.itemCount - 1)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Ошибка отправки голосового", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        }
        viewModel.currentPrivatePeer = ""
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
