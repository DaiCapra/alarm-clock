package com.example.clock.alarm

import com.example.clock.data.AlarmRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-arms every enabled alarm with AlarmManager.
 *
 * Needed after a reboot (pending alarms do not survive one) and after a clock or
 * timezone change (alarms store a local hour/minute but are armed as absolute
 * instants, so the pending trigger no longer matches the intended local time).
 *
 * Lives outside the receivers so it can be tested with a plain constructor —
 * the receivers themselves shrink to an action check plus a call to this.
 */
@Singleton
class AlarmRescheduler @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler
) {
    suspend fun restoreAll() {
        repository.getEnabledAlarms().forEach { scheduler.restore(it) }
    }
}
