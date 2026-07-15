package com.example.clock.alarm

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.clock.R
import com.example.clock.alarmTriggerMessage
import com.example.clock.applySystemBarInsetsAsPadding
import com.example.clock.data.Alarm
import com.example.clock.displayLabel
import com.example.clock.hhmmFormatter
import com.example.clock.tickerFlow
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
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

    private lateinit var dismissButton: MaterialButton
    private lateinit var dismissProgress: LinearProgressIndicator
    private var holdAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()
        setContentView(R.layout.activity_alarm)

        // Edge-to-edge is enforced on Android 15+, so keep the buttons clear of
        // the status/navigation bars.
        applySystemBarInsetsAsPadding(findViewById(R.id.alarm_root))

        alarm = intent.readAlarm()

        val time: TextView = findViewById(R.id.alarm_time)
        val snooze: MaterialButton = findViewById(R.id.snooze_button)
        dismissButton = findViewById(R.id.dismiss_button)
        dismissProgress = findViewById(R.id.dismiss_progress)

        showLabel()

        snooze.setOnClickListener { onSnooze() }
        // The click listener stays the actual dismiss trigger — reached via
        // performClick() when the hold completes, and directly by TalkBack's
        // accessibility click action (screen readers skip the hold).
        dismissButton.setOnClickListener { onDismiss() }
        setUpHoldToDismiss()

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

    override fun onStop() {
        super.onStop()
        // A hold in flight when the screen goes away must not dismiss later.
        cancelHold()
    }

    /** Dismiss requires a deliberate press-and-hold; the indicator above the
     *  button fills over the hold and an early release resets it. */
    @SuppressLint("ClickableViewAccessibility") // Click path preserved above.
    private fun setUpHoldToDismiss() {
        dismissButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startHold()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelHold()
                    true
                }
                else -> false
            }
        }
    }

    private fun startHold() {
        dismissButton.isPressed = true
        dismissProgress.visibility = View.VISIBLE
        holdAnimator = ValueAnimator.ofInt(0, dismissProgress.max).apply {
            duration = HOLD_TO_DISMISS_MS
            addUpdateListener { dismissProgress.progress = it.animatedValue as Int }
            doOnEnd { dismissButton.performClick() } // → onDismiss()
            start()
        }
    }

    private fun cancelHold() {
        // Strip the end listener before cancelling — cancel() also fires
        // onAnimationEnd, which must not dismiss.
        holdAnimator?.apply {
            removeAllListeners()
            cancel()
        }
        holdAnimator = null
        dismissButton.isPressed = false
        dismissProgress.progress = 0
        dismissProgress.visibility = View.INVISIBLE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alarm = intent.readAlarm()
        showLabel()
    }

    private fun showLabel() {
        findViewById<TextView>(R.id.alarm_label).text = alarm.displayLabel(this)
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
        startService(
            Intent(this, AlarmService::class.java)
                .apply { action = AlarmService.ACTION_SNOOZE }
                .putAlarm(alarm)
        )
        Toast.makeText(
            this, alarmTriggerMessage(alarm.snoozeMinutes * 60_000L), Toast.LENGTH_SHORT
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

    companion object {
        private const val HOLD_TO_DISMISS_MS = 1000L
    }
}
