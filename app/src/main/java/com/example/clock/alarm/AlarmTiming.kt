package com.example.clock.alarm

import com.example.clock.data.Alarm
import com.example.clock.repeatMask
import java.util.Calendar

/** Single source of truth for when an alarm next fires. Used by the scheduler,
 *  the countdown label and the "will trigger in…" toasts. */
object AlarmTiming {

    /** Next epoch-millis this alarm should fire, honoring [Alarm.repeatDays]
     *  (0 = one-shot: today if still ahead, else tomorrow). A pending snooze
     *  ([Alarm.snoozeUntil]) fires before the regular schedule. */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        if (alarm.snoozeUntil > now) return alarm.snoozeUntil

        // On a spring-forward day an alarm set inside the skipped hour (e.g.
        // 02:30 where 02:00-03:00 does not exist) resolves via the pre-transition
        // offset and fires an hour later by wall clock — 03:30. Ringing late
        // beats not ringing at all; AlarmTimingDstTest pins this.
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, alarm.second)
            set(Calendar.MILLISECOND, 0)
        }

        val repeat = alarm.repeatMask
        if (repeat == 0) {
            if (base.timeInMillis <= now) base.add(Calendar.DAY_OF_YEAR, 1)
            return base.timeInMillis
        }

        // 0..7 so that today's weekday is reconsidered a week out: today's bit
        // may be set while today's time has already passed.
        for (offset in 0..7) {
            val candidate = base.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            val bit = candidate.get(Calendar.DAY_OF_WEEK) - 1 // Sunday=0 .. Saturday=6
            if ((repeat shr bit) and 1 == 1 && candidate.timeInMillis > now) {
                return candidate.timeInMillis
            }
        }
        // Unreachable: repeat != 0 means some weekday bit is set, and 8 offsets
        // cover every weekday with only offset 0 excluded by the `> now` guard.
        error("No trigger found for repeat mask $repeat")
    }
}
