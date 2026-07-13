package com.example.clock.alarm

import android.content.Intent
import com.example.clock.data.Alarm

/** Serialize an alarm's schedulable fields onto an intent, and read them back.
 *  Shared by the scheduler, receiver, service, preview and ringing activity so
 *  the field list lives in exactly one place. */

fun Intent.putAlarm(alarm: Alarm): Intent = apply {
    putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
    putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
    putExtra(AlarmReceiver.EXTRA_HOUR, alarm.hour)
    putExtra(AlarmReceiver.EXTRA_MINUTE, alarm.minute)
    putExtra(AlarmReceiver.EXTRA_REPEAT_DAYS, alarm.repeatDays)
    putExtra(AlarmReceiver.EXTRA_RINGTONE, alarm.ringtoneUri)
    putExtra(AlarmReceiver.EXTRA_VIBRATE, alarm.vibrate)
    putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
    putExtra(AlarmReceiver.EXTRA_VOLUME, alarm.volume)
}

fun Intent.readAlarm(): Alarm = Alarm(
    id = getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1),
    hour = getIntExtra(AlarmReceiver.EXTRA_HOUR, 0),
    minute = getIntExtra(AlarmReceiver.EXTRA_MINUTE, 0),
    label = getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: "",
    repeatDays = getIntExtra(AlarmReceiver.EXTRA_REPEAT_DAYS, 0),
    ringtoneUri = getStringExtra(AlarmReceiver.EXTRA_RINGTONE),
    vibrate = getBooleanExtra(AlarmReceiver.EXTRA_VIBRATE, Alarm.DEFAULT_VIBRATE),
    snoozeMinutes = getIntExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES),
    volume = getIntExtra(AlarmReceiver.EXTRA_VOLUME, Alarm.DEFAULT_VOLUME)
)
