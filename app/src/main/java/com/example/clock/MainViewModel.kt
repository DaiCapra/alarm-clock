package com.example.clock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.alarm.AlarmTiming
import com.example.clock.alarm.RingingState
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val ringingState: RingingState
) : ViewModel() {

    val alarms: StateFlow<List<Alarm>> = repository.getAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The alarm currently ringing, or null. Used to jump to the ringing screen. */
    val ringingAlarm: StateFlow<Alarm?> = ringingState.current

    private val timeFormat = ClockFormatter(context, withSeconds = true)

    val currentTime: Flow<String> = tickerFlow().map { timeFormat.format(System.currentTimeMillis()) }

    /** Countdown to the soonest enabled alarm (H:MM:SS) and whether that soonest
     *  trigger is a pending snooze. Null when nothing is scheduled. */
    data class NextAlarm(val countdown: String, val isSnooze: Boolean)

    val nextAlarm: Flow<NextAlarm?> = combine(alarms, tickerFlow()) { list, _ ->
        val now = System.currentTimeMillis()
        val (soonest, triggerAt) = list.filter { it.isEnabled }
            .map { it to AlarmTiming.nextTrigger(it, now) }
            .minByOrNull { it.second }
            ?: return@combine null
        NextAlarm(
            countdown = formatCountdown(triggerAt - now),
            isSnooze = soonest.snoozeUntil > now
        )
    }

    private fun formatCountdown(millis: Long): String {
        val totalSeconds = (millis / 1_000L).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%d:%02d:%02d".format(hours, minutes, seconds)
    }

    private val _scheduledMessage = MutableSharedFlow<String>()
    /** Emitted when an alarm becomes active, with a human-readable countdown. */
    val scheduledMessage: SharedFlow<String> = _scheduledMessage.asSharedFlow()

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            // setAlarmEnabled also zeroes snoozeUntil, so mirror that here: the
            // captured `alarm` can carry a snooze from an earlier bind, and
            // nextTrigger would then arm the stale snooze instead of the real
            // time — persisting one instant while scheduling another.
            repository.setAlarmEnabled(alarm.id, enabled)
            val updated = alarm.copy(isEnabled = enabled, snoozeUntil = 0)
            if (enabled) {
                scheduler.schedule(updated)
                val remaining = AlarmTiming.nextTrigger(updated) - System.currentTimeMillis()
                _scheduledMessage.emit(alarmTriggerMessage(remaining))
            } else {
                scheduler.cancel(alarm)
                // If this alarm is ringing right now, stop it too.
                if (ringingState.current.value?.id == alarm.id) scheduler.dismissRinging()
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            scheduler.cancel(alarm)
            repository.deleteAlarm(alarm)
            // If this alarm is ringing right now, stop it too.
            if (ringingState.current.value?.id == alarm.id) scheduler.dismissRinging()
        }
    }
}
