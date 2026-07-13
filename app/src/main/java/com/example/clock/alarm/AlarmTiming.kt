package com.example.clock.alarm

import com.example.clock.data.Alarm
import java.util.Calendar

/** Single source of truth for when an alarm next fires. Used by the scheduler,
 *  the countdown label and the "will trigger in…" toasts. */
object AlarmTiming {

    /** Next epoch-millis this alarm should fire, honoring [Alarm.repeatDays]
     *  (0 = one-shot: today if still ahead, else tomorrow). A pending snooze
     *  ([Alarm.snoozeUntil]) fires before the regular schedule. */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        if (alarm.snoozeUntil > now) return alarm.snoozeUntil

        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, alarm.second)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays == 0) {
            if (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
            return base.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = base.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            val bit = candidate.get(Calendar.DAY_OF_WEEK) - 1 // Sunday=0 .. Saturday=6
            if ((alarm.repeatDays shr bit) and 1 == 1 && candidate.timeInMillis > now) {
                return candidate.timeInMillis
            }
        }
        return base.timeInMillis
    }
}
