package com.example.clock

import com.example.clock.data.Alarm

/**
 * Pure mapping from the Add/Edit alarm form inputs onto an [Alarm], kept out of
 * the Activity so it can be unit-tested without Android/Hilt.
 */
object AlarmForm {

    /** @param dayChecked size-7 list, index 0 = Sunday .. index 6 = Saturday.
     *  The ringtone is carried over from [base] (the picker mutates the draft). */
    fun buildAlarm(
        base: Alarm,
        hour: Int,
        minute: Int,
        label: String,
        dayChecked: List<Boolean>,
        vibrate: Boolean,
        snoozeMinutes: Int,
        volume: Int
    ): Alarm {
        var days = 0
        dayChecked.forEachIndexed { index, on -> if (on) days = days or (1 shl index) }
        return base.copy(
            hour = hour,
            minute = minute,
            label = label.trim(),
            repeatDays = days,
            vibrate = vibrate,
            snoozeMinutes = snoozeMinutes,
            volume = volume.coerceIn(0, 100),
            snoozeUntil = 0,
            isEnabled = true
        )
    }

    /** Extract the leading integer from a "10 min" dropdown value, else [default]. */
    fun parseSnoozeMinutes(text: String, default: Int): Int =
        text.filter { it.isDigit() }.toIntOrNull() ?: default
}
