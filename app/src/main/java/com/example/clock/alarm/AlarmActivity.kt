package com.example.clock.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.clock.R
import com.example.clock.applySystemBarInsetsAsPadding
import com.example.clock.data.Alarm
import com.example.clock.hhmmFormatter
import com.example.clock.humanDuration
import com.example.clock.tickerFlow
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Full-screen alarm view shown when an alarm fires. Displays the live time and
 * offers a big Snooze and a big Dismiss button. Launched over the lock screen
 * via the [AlarmService] full-screen notification intent.
 */
@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    private lateinit var alarm: Alarm

    private val timeFormat = hhmmFormatter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        setContentView(R.layout.activity_alarm)

        // Edge-to-edge is enforced on Android 15+, so keep the buttons clear of
        // the status/navigation bars.
        applySystemBarInsetsAsPadding(findViewById(R.id.alarm_root))

        alarm = intent.readAlarm()

        val time: TextView = findViewById(R.id.alarm_time)
        val labelView: TextView = findViewById(R.id.alarm_label)
        val snooze: MaterialButton = findViewById(R.id.snooze_button)
        val dismiss: MaterialButton = findViewById(R.id.dismiss_button)

        labelView.text = alarm.label.ifEmpty { getString(R.string.alarm) }

        snooze.setOnClickListener { onSnooze() }
        dismiss.setOnClickListener { onDismiss() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                tickerFlow().collect { time.text = timeFormat.format(Date()) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ringing view is visible — ask the service to silence its notification
        // (removes the heads-up popup while the app is open).
        startService(
            Intent(this, AlarmService::class.java).apply { action = AlarmService.ACTION_REFRESH }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alarm = intent.readAlarm()
        findViewById<TextView>(R.id.alarm_label).text =
            alarm.label.ifEmpty { getString(R.string.alarm) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                onSnooze()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun onSnooze() {
        // Route through the service so the snooze schedule + persisted snoozeUntil
        // happen in one place (see AlarmService.ACTION_SNOOZE).
        val minutes = alarm.snoozeMinutes
        startService(
            Intent(this, AlarmService::class.java)
                .apply { action = AlarmService.ACTION_SNOOZE }
                .putAlarm(alarm)
        )
        Toast.makeText(
            this, "Alarm will trigger in ${humanDuration(minutes * 60_000L)}", Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private fun onDismiss() {
        startService(
            Intent(this, AlarmService::class.java).apply { action = AlarmService.ACTION_DISMISS }
        )
        finish()
    }

    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
