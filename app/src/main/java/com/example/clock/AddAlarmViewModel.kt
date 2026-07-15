package com.example.clock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.alarm.AlarmTiming
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmPrefs
import com.example.clock.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the alarm being edited.
 *
 * The draft lives here rather than in the Activity because a rotation destroys
 * the Activity: fields like the chosen ringtone, the repeat days and the id have
 * no view to restore them from, so they used to silently revert to the defaults
 * mid-edit. The ViewModel survives, so the draft does.
 */
@HiltViewModel
class AddAlarmViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val prefs: AlarmPrefs,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        /** [nextTrigger] is null when the saved alarm is disabled — nothing armed. */
        data class Saved(val nextTrigger: Long?) : SaveState
    }

    /** 0 when creating; otherwise the alarm being edited. Read from the intent by
     *  Hilt, and it survives process death with the handle. */
    private val editId: Int = savedStateHandle[AddAlarmActivity.EXTRA_EDIT_ID] ?: 0

    val isEditing: Boolean get() = editId != 0

    private val _draft = MutableStateFlow<Alarm?>(null)
    /** Null until loaded; the screen binds on the first non-null value. */
    val draft: StateFlow<Alarm?> = _draft.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    init {
        // Once, in init — not on every onCreate. Re-reading on rotation would
        // overwrite whatever the user had typed with the stored row again.
        viewModelScope.launch {
            _draft.value = if (isEditing) {
                repository.getAlarmById(editId) ?: newAlarmDraft()
            } else {
                newAlarmDraft()
            }
        }
    }

    /** Apply an edit to the draft. The screen's widgets are inputs; this is the
     *  single source of truth that Save reads. */
    fun update(transform: (Alarm) -> Alarm) {
        _draft.value = _draft.value?.let(transform)
    }

    /** A blank alarm seeded with the user's last-used ringtone and snooze. Reads
     *  SharedPreferences off the main thread — the first read parses the XML. */
    private suspend fun newAlarmDraft(): Alarm = withContext(Dispatchers.IO) {
        Alarm(
            hour = 7,
            minute = 0,
            repeatDays = ALL_DAYS,
            ringtoneUri = prefs.defaultRingtoneUri,
            snoozeMinutes = prefs.defaultSnoozeMinutes,
            volume = prefs.defaultVolume
        )
    }

    /**
     * Insert or update the draft and (re)schedule it. Idempotent: a second call
     * while a save is in flight — or after one completed, which a rotation
     * mid-save used to cause — is ignored rather than inserting a duplicate.
     */
    fun save() {
        if (_saveState.value != SaveState.Idle) return
        val alarm = _draft.value ?: return
        _saveState.value = SaveState.Saving

        viewModelScope.launch {
            val saved = if (alarm.id == 0) {
                alarm.copy(id = repository.addAlarm(alarm).toInt())
            } else {
                repository.updateAlarm(alarm)
                alarm
            }
            // Keep the id so a re-entrant save updates instead of inserting again.
            _draft.value = saved

            withContext(Dispatchers.IO) { prefs.saveDefaults(saved) }

            // Also clears any snooze countdown; the edited alarm is a new alarm.
            scheduler.cancel(saved.id)
            if (saved.isEnabled) {
                scheduler.schedule(saved)
                _saveState.value = SaveState.Saved(AlarmTiming.nextTrigger(saved))
            } else {
                _saveState.value = SaveState.Saved(null)
            }
        }
    }
}
