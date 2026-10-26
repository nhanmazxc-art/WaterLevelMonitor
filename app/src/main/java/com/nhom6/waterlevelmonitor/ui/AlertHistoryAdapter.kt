package com.nhom6.waterlevelmonitor.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhom6.waterlevelmonitor.R
import com.nhom6.waterlevelmonitor.data.AlertLogEntry

/**
 * RecyclerView Adapter for displaying alert log entries.
 * Uses the colored dot drawables (ic_dot_danger, ic_dot_warning, ic_dot_safe).
 */
class AlertHistoryAdapter : ListAdapter<AlertLogEntry, AlertHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.alertIcon)
        private val title: TextView = view.findViewById(R.id.alertTitle)
        private val time: TextView = view.findViewById(R.id.alertTime)

        fun bind(entry: AlertLogEntry) {
            // Choose icon based on type
            val iconRes = when (entry.type) {
                "DANGER" -> R.drawable.ic_dot_danger
                "WARNING" -> R.drawable.ic_dot_warning
                "SAFE" -> R.drawable.ic_dot_safe
                else -> R.drawable.ic_dot_warning // SENSOR_ERROR treated as warning color
            }
            icon.setImageResource(iconRes)
            title.text = entry.message
            // Format timestamp to HH:mm:ss
            val formatted = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(entry.timestamp))
            time.text = formatted
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AlertLogEntry>() {
        override fun areItemsTheSame(oldItem: AlertLogEntry, newItem: AlertLogEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AlertLogEntry, newItem: AlertLogEntry): Boolean = oldItem == newItem
    }
}
