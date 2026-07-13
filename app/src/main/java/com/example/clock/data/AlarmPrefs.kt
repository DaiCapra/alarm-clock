package com.example.clock.data

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
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)

    var defaultRingtoneUri: String?
        get() = prefs.getString(KEY_RINGTONE, null)
        set(value) = prefs.edit().putString(KEY_RINGTONE, value).apply()

    var defaultSnoozeMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE, DEFAULT_SNOOZE)
        set(value) = prefs.edit().putInt(KEY_SNOOZE, value).apply()

    var defaultVolume: Int
        get() = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)
        set(value) = prefs.edit().putInt(KEY_VOLUME, value).apply()

    private companion object {
        const val KEY_RINGTONE = "default_ringtone_uri"
        const val KEY_SNOOZE = "default_snooze_minutes"
        const val KEY_VOLUME = "default_volume"
        const val DEFAULT_SNOOZE = 10
        const val DEFAULT_VOLUME = 100
    }
}
