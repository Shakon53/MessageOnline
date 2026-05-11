package com.messageonline.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.messageonline.android.adapter.FriendsAdapter
import com.messageonline.android.databinding.FragmentFriendsBinding
import com.messageonline.android.model.ChatSession
import com.messageonline.android.network.SocketManager
import com.messageonline.android.viewmodel.ChatViewModel

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var adapter: FriendsAdapter

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val raw = result.contents ?: return@registerForActivityResult
        val userId = raw.removePrefix("messageonline://user/").toIntOrNull()
        if (userId != null && userId > 0) showConfirmAddFriend(userId)
        else Toast.makeText(requireContext(), "Неверный QR-код", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val myId = ChatSession.userId
        binding.tvMyId.text = "#$myId"
        (binding.tvMyId.parent.parent as? View)?.setOnClickListener { copyMyId(myId) }
        binding.tvMyId.setOnClickListener { copyMyId(myId) }

        setupRecyclerView()
        setupObservers()
        setupSearch()

        binding.btnAddFriend.setOnClickListener { showAddFriendDialog() }
        binding.btnScanQR.setOnClickListener { launchQRScanner() }

        binding.swipeRefresh.setColorSchemeColors(0xFF6366F1.toInt())
        binding.swipeRefresh.setOnRefreshListener { viewModel.refreshFriends() }

        viewModel.refreshFriends()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun launchQRScanner() {
        qrScanLauncher.launch(ScanOptions().apply {
            setPrompt("Наведите на QR-код друга")
            setBeepEnabled(false)
            setOrientationLocked(true)
            setBarcodeImageEnabled(false)
        })
    }

    private fun showConfirmAddFriend(userId: Int) {
        if (userId == ChatSession.userId) {
            Toast.makeText(requireContext(), "Нельзя добавить себя", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить друга")
            .setMessage("Отправить запрос дружбы пользователю с ID: #$userId ?")
            .setPositiveButton("Добавить") { _, _ ->
                SocketManager.sendFriendAdd(userId)
                Toast.makeText(requireContext(), "Запрос отправлен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun copyMyId(id: Int) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("id", "$id"))
        Toast.makeText(requireContext(), "ID #$id скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = FriendsAdapter(
            onStartChat = { friend ->
                startActivity(Intent(requireContext(), PrivateChatActivity::class.java).apply {
                    putExtra("peer_username", friend.username)
                })
            },
            onAccept = { friend ->
                viewModel.acceptFriend(friend.userId)
                Toast.makeText(requireContext(), "✓ Запрос принят", Toast.LENGTH_SHORT).show()
            },
            onDecline = { friend -> viewModel.declineFriend(friend.userId) },
            onRemoveFriend = { friend ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Удалить из друзей")
                    .setMessage("Убрать ${friend.username} из списка друзей?")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.removeFriend(friend.userId) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvFriends.apply {
            this.adapter = this@FriendsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupSearch() {
        binding.etFriendSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.applySearch(s.toString().trim())
            }
        })
    }

    private fun setupObservers() {
        viewModel.friends.observe(viewLifecycleOwner) { friends ->
            val requests = viewModel.friendRequests.value ?: emptyList()
            adapter.setData(friends, requests)
            binding.swipeRefresh.isRefreshing = false
            updateEmptyState(friends.isEmpty() && requests.isEmpty())
        }
        viewModel.friendRequests.observe(viewLifecycleOwner) { requests ->
            val friends = viewModel.friends.value ?: emptyList()
            adapter.setData(friends, requests)
            updateEmptyState(friends.isEmpty() && requests.isEmpty())
        }
        viewModel.onlineUsers.observe(viewLifecycleOwner) { users ->
            adapter.updateOnlineStatuses(users.map { it.username }.toSet())
        }
        viewModel.notification.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFriends.visibility   = if (isEmpty) View.GONE  else View.VISIBLE
    }

    private fun showAddFriendDialog() {
        val et = EditText(requireContext()).apply {
            hint = "Введите ID пользователя"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(56, 32, 56, 16)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("👥 Добавить друга")
            .setMessage("Попросите друга открыть профиль и сообщить вам его ID")
            .setView(et)
            .setPositiveButton("Отправить запрос") { _, _ ->
                val id = et.text.toString().trim().toIntOrNull()
                when {
                    id == null || id <= 0    -> Toast.makeText(requireContext(), "Введите корректный ID", Toast.LENGTH_SHORT).show()
                    id == ChatSession.userId -> Toast.makeText(requireContext(), "Нельзя добавить себя", Toast.LENGTH_SHORT).show()
                    else                     -> viewModel.addFriend(id)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
