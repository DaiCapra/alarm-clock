package com.example.clock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.clock.data.Alarm
import com.google.android.material.materialswitch.MaterialSwitch

class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onDelete: (Alarm) -> Unit,
    private val onEdit: (Alarm) -> Unit,
    private val onPreview: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(DIFF) {

    private val timeFormat = hhmmFormatter()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val time: TextView = itemView.findViewById(R.id.alarm_time)
        private val label: TextView = itemView.findViewById(R.id.alarm_label)
        private val days: TextView = itemView.findViewById(R.id.alarm_days)
        private val toggle: MaterialSwitch = itemView.findViewById(R.id.alarm_toggle)
        private val menu: ImageButton = itemView.findViewById(R.id.alarm_menu)

        fun bind(alarm: Alarm) {
            time.text = formatClockTime(alarm.hour, alarm.minute)
            label.text = alarm.label
            label.visibility = if (alarm.label.isEmpty()) View.GONE else View.VISIBLE

            if (alarm.snoozeUntil > System.currentTimeMillis()) {
                val ringsAt = timeFormat.format(java.util.Date(alarm.snoozeUntil))
                days.text = itemView.context.getString(R.string.snoozed_rings_at, ringsAt)
            } else {
                days.text = formatRepeatDays(alarm.repeatDays)
            }

            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = alarm.isEnabled
            toggle.setOnCheckedChangeListener { _, isChecked -> onToggle(alarm, isChecked) }

            menu.setOnClickListener { anchor -> showMenu(anchor, alarm) }

            itemView.setOnClickListener { onEdit(alarm) }
            itemView.setOnLongClickListener {
                onDelete(alarm)
                true
            }
        }

        private fun showMenu(anchor: View, alarm: Alarm) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(0, MENU_EDIT, 0, R.string.edit)
                menu.add(0, MENU_PREVIEW, 1, R.string.preview)
                menu.add(0, MENU_DELETE, 2, R.string.delete)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_EDIT -> { onEdit(alarm); true }
                        MENU_PREVIEW -> { onPreview(alarm); true }
                        MENU_DELETE -> { onDelete(alarm); true }
                        else -> false
                    }
                }
                show()
            }
        }
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_PREVIEW = 2
        private const val MENU_DELETE = 3

        private val DIFF = object : DiffUtil.ItemCallback<Alarm>() {
            override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) =
                oldItem == newItem
        }
    }
}
