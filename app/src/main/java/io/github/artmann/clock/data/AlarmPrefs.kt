package io.github.artmann.clock.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last-used ringtone and snooze duration so a freshly created
 * alarm starts from the user's previous choice instead of hard defaults.
 */
@Singleton
class AlarmPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Lazy so that merely constructing this singleton (which Hilt does on the
    // main thread) doesn't kick off the XML load. Callers read it from IO.
    private val prefs by lazy {
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
    }

    val defaultRingtoneUri: String? get() = prefs.getString(KEY_RINGTONE, null)

    val defaultSnoozeMinutes: Int get() = prefs.getInt(KEY_SNOOZE, DEFAULT_SNOOZE)

    val defaultVolume: Int get() = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)

    /** Remember these choices as the starting point for the next new alarm. One
     *  edit, so one disk write — three separate apply()s each queue their own,
     *  which the next onPause then blocks the main thread waiting on. */
    fun saveDefaults(alarm: Alarm) {
        prefs.edit()
            .putString(KEY_RINGTONE, alarm.ringtoneUri)
            .putInt(KEY_SNOOZE, alarm.snoozeMinutes)
            .putInt(KEY_VOLUME, alarm.volume)
            .apply()
    }

    private companion object {
        const val KEY_RINGTONE = "default_ringtone_uri"
        const val KEY_SNOOZE = "default_snooze_minutes"
        const val KEY_VOLUME = "default_volume"
        const val DEFAULT_SNOOZE = 10
        const val DEFAULT_VOLUME = 100
    }
}
