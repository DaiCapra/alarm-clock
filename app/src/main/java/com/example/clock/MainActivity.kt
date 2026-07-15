package com.example.clock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clock.alarm.AlarmActivity
import com.example.clock.alarm.putAlarm
import com.example.clock.data.Alarm
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var preview: RingtonePreview

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Alarm still rings; without it the full-screen ringing view may not show. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        requestNotificationPermission()

        applySystemBarInsetsAsPadding(findViewById(R.id.main))

        val currentTime: TextView = findViewById(R.id.current_time)
        val nextAlarm: TextView = findViewById(R.id.next_alarm)
        val nextAlarmIcon: View = findViewById(R.id.next_alarm_icon)
        val nextAlarmLabel: View = findViewById(R.id.next_alarm_label)
        val snoozeLabel: TextView = findViewById(R.id.snooze_label)
        val emptyView: View = findViewById(R.id.empty_view)
        val list: RecyclerView = findViewById(R.id.alarm_list)
        val addButton: FloatingActionButton = findViewById(R.id.add_alarm_button)

        preview = RingtonePreview(this, list, RingtonePreview.SAMPLE_CLIP_MS)

        val adapter = AlarmAdapter(
            onToggle = { alarm, enabled -> viewModel.setEnabled(alarm, enabled) },
            onDelete = { alarm -> confirmDelete(alarm) },
            onEdit = { alarm -> editAlarm(alarm) },
            onPreview = { alarm -> previewAlarm(alarm) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        addButton.setOnClickListener { openAddAlarm() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentTime.collectLatest { currentTime.text = it }
                }
                launch {
                    viewModel.scheduledMessage.collectLatest {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.ringingAlarm.collectLatest { alarm ->
                        // An alarm is ringing and the user is in the app — show the
                        // snooze/dismiss screen instead of leaving them on the list.
                        if (alarm != null) {
                            startActivity(
                                Intent(this@MainActivity, AlarmActivity::class.java).putAlarm(alarm)
                            )
                        }
                    }
                }
                launch {
                    viewModel.nextAlarm.collectLatest { info ->
                        nextAlarm.text = info?.countdown
                        // INVISIBLE, not GONE — reserves the row's height so the
                        // list below doesn't jump when an alarm is toggled off.
                        val visible = if (info == null) View.INVISIBLE else View.VISIBLE
                        nextAlarm.visibility = visible
                        nextAlarmIcon.visibility = visible
                        nextAlarmLabel.visibility = visible
                        snoozeLabel.visibility =
                            if (info?.isSnooze == true) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.alarms.collectLatest { alarms ->
                        adapter.submitList(alarms)
                        emptyView.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Also covers a real alarm arriving mid-preview: it launches the ringing
        // screen, this stops, and the sample can't play over the alarm.
        preview.stop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openAddAlarm() {
        startActivity(Intent(this, AddAlarmActivity::class.java))
    }

    private fun editAlarm(alarm: Alarm) {
        startActivity(Intent(this, AddAlarmActivity::class.java).apply {
            putExtra(AddAlarmActivity.EXTRA_EDIT_ID, alarm.id)
        })
    }

    private fun confirmDelete(alarm: Alarm) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_alarm_title)
            .setMessage(R.string.delete_alarm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAlarm(alarm) }
            .show()
    }

    /** Play a bounded sample of the alarm's tone at its volume. Deliberately not
     *  the real ringing path: that armed an 11-minute wake lock, and its Snooze
     *  button would arm a genuine alarm and persist snoozeUntil — a "preview"
     *  that goes off ten minutes later. Tapping again stops it. */
    private fun previewAlarm(alarm: Alarm) {
        if (preview.isPlaying()) {
            preview.stop()
            return
        }
        preview.play(alarm.ringtoneUri, alarm.volume)
        Toast.makeText(this, R.string.previewing, Toast.LENGTH_SHORT).show()
    }
}
