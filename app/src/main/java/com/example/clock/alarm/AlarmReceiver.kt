package com.example.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.clock.data.AlarmRepository
import com.example.clock.isRepeating
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: AlarmScheduler

    @Inject
    lateinit var repository: AlarmRepository

    override fun onReceive(context: Context, intent: Intent) {
        val alarm = intent.readAlarm()

        val serviceIntent = Intent(context, AlarmService::class.java).putAlarm(alarm)
        ContextCompat.startForegroundService(context, serviceIntent)

        // Ringing UI is driven by the service's full-screen intent: it opens
        // over the lock screen when locked, and shows a heads-up notification
        // (with Snooze/Dismiss) when the device is unlocked/on the home screen.

        // This alarm is now ringing — drop any snooze countdown/state for it.
        scheduler.cancelSnoozeNotification(alarm.id)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // id 0 is the unsaved sentinel; Room's autoGenerate starts at 1.
                if (alarm.id > 0) {
                    repository.clearSnooze(alarm.id)
                    // Queue a repeating alarm's next occurrence from the stored
                    // record, not the intent copy: a snooze re-fire carries
                    // repeatDays = 0 (snoozes are armed as one-shots), and its
                    // PendingIntent shares the alarm's request code — so the
                    // next occurrence armed at the original fire was replaced
                    // and must be re-armed here.
                    repository.getAlarmById(alarm.id)
                        ?.takeIf { it.isEnabled && it.isRepeating }
                        ?.let { scheduler.schedule(it.copy(snoozeUntil = 0)) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
