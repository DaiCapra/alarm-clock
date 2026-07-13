package com.example.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clock.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlarmManager alarms do not survive a reboot, so re-register every enabled
 * alarm once the device finishes booting.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: AlarmRepository

    @Inject
    lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // DB access is async; keep the broadcast alive until rescheduling finishes.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                repository.getEnabledAlarms().forEach { scheduler.schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
