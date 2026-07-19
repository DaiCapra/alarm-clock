package io.github.artmann.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alarms store naive local hour/minute but are scheduled as absolute epoch
 * triggers. If the device timezone (or clock) changes after an alarm is
 * armed, the pending trigger no longer matches the intended local time, so
 * every enabled alarm must be recomputed and re-registered.
 */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rescheduler: AlarmRescheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                rescheduler.restoreAll()
            } finally {
                pending.finish()
            }
        }
    }
}
