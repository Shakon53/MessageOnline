package com.messageonline.android.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.messageonline.android.R
import com.messageonline.android.model.OnlineUser

/**
 * Адаптер для списка онлайн-пользователей.
 */
class UsersAdapter(
    private val users: MutableList<OnlineUser>,
    private val myUsername: String,
    private val onUserClick: (OnlineUser) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInitial: TextView = view.findViewById(R.id.tvUserInitial)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val vOnlineDot: View = view.findViewById(R.id.vOnlineDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        // Первая буква имени как аватар
        holder.tvInitial.text = user.username.first().uppercaseChar().toString()
        holder.tvUsername.text = if (user.username == myUsername)
            "${user.username} (Вы)" else user.username
        holder.tvStatus.text = if (user.online) "онлайн" else "офлайн"
        holder.vOnlineDot.visibility = if (user.online) View.VISIBLE else View.GONE

        // Нельзя кликнуть на себя
        if (user.username != myUsername) {
            holder.itemView.setOnClickListener { onUserClick(user) }
        }
    }

    override fun getItemCount() = users.size

    fun setUsers(newUsers: List<OnlineUser>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }
}
