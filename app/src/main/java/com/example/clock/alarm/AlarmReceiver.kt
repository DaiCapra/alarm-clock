package com.example.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.clock.data.AlarmRepository
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
                if (alarm.id >= 0) repository.clearSnooze(alarm.id)
            } finally {
                pending.finish()
            }
        }

        // Repeating alarm: queue the next occurrence.
        if (alarm.repeatDays != 0) {
            scheduler.schedule(alarm)
        }
    }
}
