package io.github.artmann.clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val label: String = "",
    val isEnabled: Boolean = true,
    /** Bitmask of active weekdays; bit 0 = Sunday .. bit 6 = Saturday. 0 = one-shot. */
    val repeatDays: Int = 0,
    /** Ringtone content URI, or null for the system default alarm sound. */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = DEFAULT_VIBRATE,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Playback volume as a percentage, 0..100. */
    val volume: Int = DEFAULT_VOLUME,
    /** Epoch-millis the alarm will re-fire due to snooze, or 0 if not snoozed. */
    val snoozeUntil: Long = 0
) {
    companion object {
        const val DEFAULT_VIBRATE = true
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_VOLUME = 100
    }
}
