package com.example.clock.alarm

import android.content.Intent
import com.example.clock.data.Alarm

/** Serialize an alarm's schedulable fields onto an intent, and read them back.
 *  Shared by the scheduler, receiver, service, preview and ringing activity so
 *  the field list — keys included — lives in exactly one place. */

private const val EXTRA_ALARM_ID = "extra_alarm_id"
private const val EXTRA_ALARM_LABEL = "extra_alarm_label"
private const val EXTRA_HOUR = "extra_hour"
private const val EXTRA_MINUTE = "extra_minute"
private const val EXTRA_REPEAT_DAYS = "extra_repeat_days"
private const val EXTRA_RINGTONE = "extra_ringtone"
private const val EXTRA_VIBRATE = "extra_vibrate"
private const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
private const val EXTRA_VOLUME = "extra_volume"

fun Intent.putAlarm(alarm: Alarm): Intent = apply {
    putExtra(EXTRA_ALARM_ID, alarm.id)
    putExtra(EXTRA_ALARM_LABEL, alarm.label)
    putExtra(EXTRA_HOUR, alarm.hour)
    putExtra(EXTRA_MINUTE, alarm.minute)
    putExtra(EXTRA_REPEAT_DAYS, alarm.repeatDays)
    putExtra(EXTRA_RINGTONE, alarm.ringtoneUri)
    putExtra(EXTRA_VIBRATE, alarm.vibrate)
    putExtra(EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
    putExtra(EXTRA_VOLUME, alarm.volume)
}

fun Intent.readAlarm(): Alarm = Alarm(
    id = getIntExtra(EXTRA_ALARM_ID, -1),
    hour = getIntExtra(EXTRA_HOUR, 0),
    minute = getIntExtra(EXTRA_MINUTE, 0),
    label = getStringExtra(EXTRA_ALARM_LABEL) ?: "",
    repeatDays = getIntExtra(EXTRA_REPEAT_DAYS, 0),
    ringtoneUri = getStringExtra(EXTRA_RINGTONE),
    vibrate = getBooleanExtra(EXTRA_VIBRATE, Alarm.DEFAULT_VIBRATE),
    snoozeMinutes = getIntExtra(EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES),
    volume = getIntExtra(EXTRA_VOLUME, Alarm.DEFAULT_VOLUME)
)
