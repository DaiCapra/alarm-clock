package io.github.artmann.clock

import io.github.artmann.clock.data.Alarm

/**
 * Pure mapping from the Add/Edit alarm form inputs onto an [Alarm], kept out of
 * the Activity so it can be unit-tested without Android/Hilt.
 */
object AlarmForm {

    /**
     * @param dayChecked size-7 list, index 0 = Sunday .. index 6 = Saturday.
     * @param isEnabled the editor's own switch — a save must never double as an
     *  enable, so this is passed in rather than forced true. The ringtone is
     *  carried over from [base] (the picker mutates the draft).
     *
     * Any pending snooze is dropped: the edited alarm is a different alarm, and
     * the caller cancels the snooze trigger alongside this.
     */
    fun buildAlarm(
        base: Alarm,
        hour: Int,
        minute: Int,
        label: String,
        dayChecked: List<Boolean>,
        vibrate: Boolean,
        snoozeMinutes: Int,
        volume: Int,
        isEnabled: Boolean = base.isEnabled
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
            isEnabled = isEnabled
        )
    }

    /** The digits of a "10 min" dropdown value, else [default]. Coerced to a sane
     *  range: a 0-minute snooze would re-fire instantly, forever. */
    fun parseSnoozeMinutes(text: String, default: Int): Int =
        text.filter { it.isDigit() }.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.coerceAtMost(MAX_SNOOZE_MINUTES)
            ?: default

    /** An hour is already far past useful; beyond this it's a corrupt value. */
    private const val MAX_SNOOZE_MINUTES = 60
}
