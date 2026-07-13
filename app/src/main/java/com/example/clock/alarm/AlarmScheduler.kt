package com.example.clock.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.example.clock.MainActivity
import com.example.clock.R
import com.example.clock.data.Alarm
import com.example.clock.displayLabel
import com.example.clock.formatClockTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Schedule (or reschedule) [alarm] for its next occurrence. */
    fun schedule(alarm: Alarm) {
        setAlarm(alarm, AlarmTiming.nextTrigger(alarm))
    }

    /** Re-fire [alarm] once after [minutes], keeping its ringtone/vibrate settings
     *  but not repeating (the daily schedule already holds the next occurrence).
     *  Also posts a lock-screen notification counting down to the re-fire. */
    fun scheduleSnooze(alarm: Alarm, minutes: Int): Long {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(alarm.copy(repeatDays = 0), triggerAt)
        showSnoozeNotification(alarm, triggerAt)
        return triggerAt
    }

    fun cancel(alarm: Alarm) = cancel(alarm.id)

    fun cancel(alarmId: Int) {
        alarmManager.cancel(pendingIntent(alarmId, Intent(context, AlarmReceiver::class.java)))
        cancelSnoozeNotification(alarmId)
    }

    /** Remove the snooze countdown (called when the snoozed alarm re-fires). */
    fun cancelSnoozeNotification(alarmId: Int) {
        notificationManager.cancel(snoozeNotificationId(alarmId))
    }

    /** Stop a currently-ringing alarm (used when its alarm is disabled). */
    fun dismissRinging() {
        context.startService(
            Intent(context, AlarmService::class.java).apply { action = AlarmService.ACTION_DISMISS }
        )
    }

    private fun showSnoozeNotification(alarm: Alarm, triggerAt: Long) {
        createSnoozeChannel()
        val title = "${context.getString(R.string.snoozed)} · ${alarm.displayLabel(context)}"
        val ringsAt = formatClockTime(triggerAt)
        val openApp = PendingIntent.getActivity(
            context, alarm.id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Custom view with a live count-down Chronometer in the body, so the
        // remaining time stays visible even when the notification is collapsed.
        // Chronometer runs on the elapsed-realtime clock, so convert the wall
        // clock target into that timebase.
        val chronoBase = SystemClock.elapsedRealtime() + (triggerAt - System.currentTimeMillis())
        val content = RemoteViews(context.packageName, R.layout.notif_snooze).apply {
            setTextViewText(R.id.snooze_title, title)
            setChronometer(R.id.snooze_chrono, chronoBase, null, true)
            setChronometerCountDown(R.id.snooze_chrono, true)
        }

        val notification = NotificationCompat.Builder(context, SNOOZE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.rings_at, ringsAt))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .build()
        notificationManager.notify(snoozeNotificationId(alarm.id), notification)
    }

    private fun createSnoozeChannel() {
        // Channels exist from API 26; pre-26 notifications ignore the channel id.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            SNOOZE_CHANNEL_ID,
            "Snoozed alarms",
            // DEFAULT (not LOW) so One UI shows it on the lock screen instead of
            // hiding it under "silent". Sound is still suppressed below.
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Countdown shown while an alarm is snoozed"
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private fun snoozeNotificationId(alarmId: Int) = SNOOZE_NOTIFICATION_BASE + alarmId

    private fun setAlarm(alarm: Alarm, triggerAtMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).putAlarm(alarm)
        val pendingIntent = pendingIntent(alarm.id, intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fall back to inexact alarm if exact permission not granted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent),
                pendingIntent
            )
        }
    }

    private fun pendingIntent(alarmId: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val SNOOZE_CHANNEL_ID = "snooze_channel_v2"
        const val SNOOZE_NOTIFICATION_BASE = 2000
    }
}
