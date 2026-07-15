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
import android.os.PowerManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private var wakeLock: PowerManager.WakeLock? = null

    // Ids of alarms currently ringing. The first one owns the single sound +
    // vibration; later concurrent alarms are coalesced into the same session.
    private val activeAlarmIds = LinkedHashSet<Int>()
    private var primaryAlarm: Alarm? = null

    // Bumped whenever a new ringing session starts, so a pending confirmation
    // vibration knows a new alarm has taken over the (shared) system vibrator.
    @Volatile
    private var sessionGeneration = 0

    // stopSelf(id) only stops the service when id is the newest delivered start
    // command, so every stop goes through the latest id — no handler needs to
    // reason about which ids are still outstanding.
    @Volatile
    private var latestStartId = 0

    @Volatile
    private var confirmationPending = false

    @VisibleForTesting
    var ringtoneStartCount = 0
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_DISMISS -> stopRinging()
            ACTION_SNOOZE -> snoozeAndStop(intent.readAlarm())
            ACTION_REFRESH -> {
                // The ringing screen is now showing (app foregrounded), so re-post
                // the notification silently — drops the heads-up popup.
                val primary = primaryAlarm
                if (primary != null) {
                    startForeground(NOTIFICATION_ID, buildNotification(primary, activeAlarmIds.size))
                } else if (!confirmationPending) {
                    // Refresh raced a stop with nothing left to do; a pending
                    // confirmation will otherwise issue the stop itself.
                    stopSelf(latestStartId)
                }
            }
            else -> intent?.let { startRinging(it.readAlarm()) }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarm: Alarm) {
        val firstAlarm = activeAlarmIds.isEmpty()
        activeAlarmIds.add(alarm.id)

        if (firstAlarm) {
            sessionGeneration++
            confirmationPending = false
            primaryAlarm = alarm
            acquireWakeLock()
            ringingState.setRinging(alarm)
            startForeground(NOTIFICATION_ID, buildNotification(alarm, activeAlarmIds.size))
            playRingtone(alarm.ringtoneUri, alarm.volume)
            if (alarm.vibrate) startVibration()
            scheduleAutoSilence()
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
        releaseWakeLock()
    }

    /** The alarm broadcast's wake lock only spans onReceive, and a foreground
     *  service doesn't keep the CPU awake by itself — so hold one for as long as
     *  the alarm rings, or the ringtone can stutter or die on a sleeping device
     *  whose screen never came on. Released in onDestroy (not on teardown) so the
     *  confirmation vibration that plays after the session ends still runs. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                // Backstop: an alarm left ringing (user asleep, missed dismiss)
                // must not hold the CPU awake indefinitely.
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
    }

    private fun releaseWakeLock() {
        // The timeout may have already released it.
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun clearSession() {
        activeAlarmIds.clear()
        primaryAlarm = null
        ringingState.clear()
    }

    /** Ends the ringing session and immediately silences it. The vibrator field
     *  is nulled so onDestroy can't cancel a confirmation that plays afterwards;
     *  the notification is removed right away for perceived responsiveness. */
    private fun tearDownSession() {
        clearSession()
        ringtone?.stop()
        vibrator?.cancel()
        vibrator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** Silence gap → confirmation vibration → wait for it to finish → stop.
     *  The gap makes the confirmation distinguishable from the alarm vibration
     *  that was just cancelled. Skipped if a new alarm session started in the
     *  meantime (it owns the shared system vibrator now). */
    private fun confirmThenStop(awaitBeforeStop: Job? = null, play: (Vibrator) -> Long) {
        val generation = sessionGeneration
        confirmationPending = true
        scope.launch {
            delay(CONFIRMATION_GAP_MS)
            val duration = if (generation == sessionGeneration) {
                getSystemService(Vibrator::class.java)?.let(play) ?: 0L
            } else 0L
            delay(duration + 50)
            awaitBeforeStop?.join()
            if (generation == sessionGeneration) {
                confirmationPending = false
                stopSelf(latestStartId)
            }
            // Else a new session owns the service — stopping with the latest
            // startId would kill it; its own stop path will run.
        }
    }

    /** Safety valve: an alarm nobody dismisses (owner not home, phone under a
     *  pillow) must not ring — sound, vibration, CPU held awake — indefinitely.
     *  Silences the session after [AUTO_SILENCE_MS], like stock clock apps.
     *  Runs on Main because it mutates the same session state as the intent
     *  handlers; the generation check makes it a no-op if this session already
     *  ended or a new one took over. */
    private fun scheduleAutoSilence() {
        val generation = sessionGeneration
        scope.launch(Dispatchers.Main) {
            delay(AUTO_SILENCE_MS)
            if (generation == sessionGeneration && activeAlarmIds.isNotEmpty()) {
                tearDownSession()
                stopSelf(latestStartId)
            }
        }
    }

    private fun stopRinging() {
        val hadSession = activeAlarmIds.isNotEmpty()
        tearDownSession()
        if (!hadSession) {
            // Nothing was ringing (e.g. dismiss raced an earlier stop) — no
            // confirmation to give.
            stopSelf(latestStartId)
            return
        }
        confirmThenStop { AlarmSounds.playDismissConfirmation(it) }
    }

    /** Snooze the ringing alarm and stop the service (shared by the notification
     *  action and the ringing screen). */
    private fun snoozeAndStop(alarm: Alarm) {
        val triggerAt = scheduler.scheduleSnooze(alarm, alarm.snoozeMinutes)
        tearDownSession()
        // Persist ahead of the scope being cancelled on destroy.
        val persist = scope.launch { repository.setSnoozeUntil(alarm.id, triggerAt) }
        confirmThenStop(awaitBeforeStop = persist) {
            AlarmSounds.playSnoozeConfirmation(it)
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
        private const val CONFIRMATION_GAP_MS = 250L
        private const val WAKE_LOCK_TAG = "Clock:AlarmService"
        private const val AUTO_SILENCE_MS = 10 * 60 * 1000L
        // Outlives auto-silence by a minute so teardown and the confirmation
        // vibration never run on a lapsed lock.
        private const val WAKE_LOCK_TIMEOUT_MS = AUTO_SILENCE_MS + 60 * 1000L
    }
}
