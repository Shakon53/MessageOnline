package com.messageonline.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.messageonline.android.R
import com.messageonline.android.model.Friend

class FriendsAdapter(
    private val onStartChat:    (Friend) -> Unit,
    private val onAccept:       (Friend) -> Unit,
    private val onDecline:      (Friend) -> Unit,
    private val onRemoveFriend: (Friend) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VT_HEADER  = 0
        private const val VT_REQUEST = 1
        private const val VT_FRIEND  = 2
    }

    private sealed class Item {
        data class Header(val title: String) : Item()
        data class RequestItem(val friend: Friend) : Item()
        data class FriendItem(val friend: Friend) : Item()
    }

    private val items = mutableListOf<Item>()

    fun setData(friends: List<Friend>, requests: List<Friend>) {
        items.clear()
        if (requests.isNotEmpty()) {
            items.add(Item.Header("ЗАПРОСЫ (${requests.size})"))
            requests.forEach { items.add(Item.RequestItem(it)) }
        }
        items.add(Item.Header(if (friends.isEmpty()) "ДРУЗЬЯ" else "ДРУЗЬЯ (${friends.size})"))
        friends.forEach { items.add(Item.FriendItem(it)) }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Header      -> VT_HEADER
        is Item.RequestItem -> VT_REQUEST
        is Item.FriendItem  -> VT_FRIEND
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            VT_HEADER  -> HeaderVH(inf.inflate(R.layout.item_section_header, parent, false))
            VT_REQUEST -> RequestVH(inf.inflate(R.layout.item_friend_request, parent, false))
            else       -> FriendVH(inf.inflate(R.layout.item_friend, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderVH).bind(item.title)
            is Item.RequestItem -> (holder as RequestVH).bind(item.friend)
            is Item.FriendItem  -> (holder as FriendVH).bind(item.friend)
        }
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvSectionHeader)
        fun bind(title: String) { tv.text = title }
    }

    inner class RequestVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName:   TextView  = view.findViewById(R.id.tvFriendName)
        private val tvStatus: TextView  = view.findViewById(R.id.tvFriendStatus)
        private val ivAvatar: ImageView = view.findViewById(R.id.ivFriendAvatar)
        private val tvInit:   TextView  = view.findViewById(R.id.tvFriendInitial)
        private val btnAccept:  View    = view.findViewById(R.id.btnAccept)
        private val btnDecline: View    = view.findViewById(R.id.btnDecline)

        fun bind(f: Friend) {
            tvName.text   = f.username
            tvStatus.text = f.statusText.ifBlank { "ID: #${f.userId}" }
            tvInit.text   = f.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            loadAvatar(ivAvatar, tvInit, f.avatarUrl)
            btnAccept.setOnClickListener  { onAccept(f) }
            btnDecline.setOnClickListener { onDecline(f) }
        }
    }

    inner class FriendVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName:    TextView  = view.findViewById(R.id.tvFriendName)
        private val tvStatus:  TextView  = view.findViewById(R.id.tvFriendStatus)
        private val ivAvatar:  ImageView = view.findViewById(R.id.ivFriendAvatar)
        private val tvInit:    TextView  = view.findViewById(R.id.tvFriendInitial)
        private val vOnline:   View      = view.findViewById(R.id.vOnlineDot)
        private val btnChat:   View      = view.findViewById(R.id.btnStartChat)
        private val btnRemove: View      = view.findViewById(R.id.btnRemoveFriend)

        fun bind(f: Friend) {
            tvName.text   = f.username
            tvStatus.text = f.statusText.ifBlank { "ID: #${f.userId}" }
            tvInit.text   = f.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            vOnline.visibility = if (f.isOnline) View.VISIBLE else View.GONE
            loadAvatar(ivAvatar, tvInit, f.avatarUrl)
            btnChat.setOnClickListener   { onStartChat(f) }
            btnRemove.setOnClickListener { onRemoveFriend(f) }
        }
    }

    private fun loadAvatar(iv: ImageView, tvInitial: TextView, url: String) {
        if (url.isNotEmpty()) {
            iv.visibility        = View.VISIBLE
            tvInitial.visibility = View.INVISIBLE
            iv.load(url) {
                transformations(CircleCropTransformation())
                listener(onError = { _, _ ->
                    iv.visibility        = View.GONE
                    tvInitial.visibility = View.VISIBLE
                })
            }
        } else {
            iv.visibility        = View.GONE
            tvInitial.visibility = View.VISIBLE
        }
    }
}
