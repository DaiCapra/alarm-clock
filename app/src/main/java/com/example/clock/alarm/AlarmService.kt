package com.example.clock.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Typeface
import android.media.Ringtone
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.os.IBinder
import android.os.Vibrator
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.example.clock.R
import com.example.clock.audio.AlarmSounds
import com.example.clock.displayLabel
import com.example.clock.formatClockTime
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var scheduler: AlarmScheduler

    @Inject
    lateinit var repository: AlarmRepository

    @Inject
    lateinit var ringingState: RingingState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    // Ids of alarms currently ringing. The first one owns the single sound +
    // vibration; later concurrent alarms are coalesced into the same session.
    private val activeAlarmIds = LinkedHashSet<Int>()
    private var primaryAlarm: Alarm? = null

    @VisibleForTesting
    var ringtoneStartCount = 0
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> snoozeAndStop(intent.readAlarm())
            ACTION_REFRESH ->
                // The ringing screen is now showing (app foregrounded), so re-post
                // the notification silently — drops the heads-up popup.
                primaryAlarm?.let {
                    startForeground(NOTIFICATION_ID, buildNotification(it, activeAlarmIds.size))
                }
            else -> intent?.let { startRinging(it.readAlarm()) }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarm: Alarm) {
        val firstAlarm = activeAlarmIds.isEmpty()
        activeAlarmIds.add(alarm.id)

        if (firstAlarm) {
            primaryAlarm = alarm
            ringingState.setRinging(alarm)
            startForeground(NOTIFICATION_ID, buildNotification(alarm, activeAlarmIds.size))
            playRingtone(alarm.ringtoneUri, alarm.volume)
            if (alarm.vibrate) startVibration()
        } else {
            // Already ringing: don't stack a second sound — just refresh the
            // notification to reflect the number of alarms going off.
            val primary = primaryAlarm ?: alarm
            startForeground(NOTIFICATION_ID, buildNotification(primary, activeAlarmIds.size))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ringtone?.stop()
        vibrator?.cancel()
        scope.cancel()
    }

    private fun clearSession() {
        activeAlarmIds.clear()
        primaryAlarm = null
        ringingState.clear()
    }

    private fun stopRinging() {
        clearSession()
        stopSelf()
    }

    /** Snooze the ringing alarm and stop the service (shared by the notification
     *  action and the ringing screen). */
    private fun snoozeAndStop(alarm: Alarm) {
        val triggerAt = scheduler.scheduleSnooze(alarm, alarm.snoozeMinutes)
        clearSession()
        ringtone?.stop()
        vibrator?.cancel()
        // Persist ahead of the scope being cancelled, then stop.
        scope.launch {
            repository.setSnoozeUntil(alarm.id, triggerAt)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playRingtone(ringtoneUri: String?, volumePercent: Int) {
        ringtone = AlarmSounds.createRingtone(this, ringtoneUri, volumePercent, looping = true)
            ?.apply { play() }
        ringtoneStartCount++
    }

    private fun startVibration() {
        val vib = getSystemService(Vibrator::class.java) ?: return
        vibrator = vib
        AlarmSounds.startAlarmVibration(vib)
    }

    private fun buildNotification(alarm: Alarm, activeCount: Int): Notification {
        val dismissPendingIntent = PendingIntent.getService(
            this, alarm.id,
            Intent(this, AlarmService::class.java).apply { action = ACTION_DISMISS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Different request code so this doesn't collide with the dismiss intent.
        val snoozePendingIntent = PendingIntent.getService(
            this, alarm.id + SNOOZE_REQUEST_OFFSET,
            Intent(this, AlarmService::class.java).apply { action = ACTION_SNOOZE }.putAlarm(alarm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AlarmActivity::class.java)
            .putAlarm(alarm)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarm.id, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (activeCount > 1) "$activeCount alarms" else alarm.displayLabel(this)
        val time = formatClockTime(alarm.hour, alarm.minute)
        val timeText = SpannableString(time).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.3f), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // App open → the UI switches to the ringing view itself, so keep the
        // required foreground-service notification silent (no heads-up, no
        // full-screen intent). Home/locked → alert loudly and open full-screen.
        val appOpen = AppForegroundState.isForeground
        val builder = NotificationCompat.Builder(
            this, if (appOpen) CHANNEL_ID_SILENT else CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(timeText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, getString(R.string.snooze), snoozePendingIntent)
            .addAction(0, getString(R.string.dismiss), dismissPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)

        if (!appOpen) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPendingIntent, true)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        // Channels exist from API 26; pre-26 notifications ignore the channel id.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val silent = NotificationChannel(
            CHANNEL_ID_SILENT,
            "Alarm (app open)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent alarm notification shown while the app is open"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(silent)

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm notifications"
            setSound(null, null) // sound handled by Ringtone
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_DISMISS = "action_dismiss"
        const val ACTION_SNOOZE = "action_snooze"
        const val ACTION_REFRESH = "action_refresh"
        private const val CHANNEL_ID = "alarm_channel"
        private const val CHANNEL_ID_SILENT = "alarm_channel_silent"
        private const val NOTIFICATION_ID = 1001
        private const val SNOOZE_REQUEST_OFFSET = 1_000_000
    }
}
