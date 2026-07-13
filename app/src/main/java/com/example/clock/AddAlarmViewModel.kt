package com.example.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.alarm.AlarmTiming
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmPrefs
import com.example.clock.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val prefs: AlarmPrefs
) : ViewModel() {

    suspend fun load(id: Int): Alarm? = repository.getAlarmById(id)

    /** A blank alarm seeded with the user's last-used ringtone and snooze. */
    fun newAlarmDraft(): Alarm = Alarm(
        hour = 7,
        minute = 0,
        repeatDays = ALL_DAYS,
        ringtoneUri = prefs.defaultRingtoneUri,
        snoozeMinutes = prefs.defaultSnoozeMinutes,
        volume = prefs.defaultVolume
    )

    /**
     * Insert or update [alarm], (re)schedule it when enabled, and report the
     * next trigger time so the caller can surface a countdown. Returns null via
     * [onSaved] when the alarm is disabled (nothing scheduled).
     */
    fun save(alarm: Alarm, onSaved: (nextTrigger: Long?) -> Unit) {
        viewModelScope.launch {
            val saved = if (alarm.id == 0) {
                alarm.copy(id = repository.addAlarm(alarm).toInt())
            } else {
                repository.updateAlarm(alarm)
                alarm
            }
            // Remember these choices as defaults for the next new alarm.
            prefs.defaultRingtoneUri = saved.ringtoneUri
            prefs.defaultSnoozeMinutes = saved.snoozeMinutes
            prefs.defaultVolume = saved.volume

            scheduler.cancel(saved.id)
            if (saved.isEnabled) {
                scheduler.schedule(saved)
                onSaved(AlarmTiming.nextTrigger(saved))
            } else {
                onSaved(null)
            }
        }
    }
}
