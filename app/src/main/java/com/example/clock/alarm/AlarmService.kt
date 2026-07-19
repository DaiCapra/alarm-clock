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
import com.example.clock.di.ApplicationScope
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

    /** Outlives this service, for writes that must survive its destruction. */
    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Alarms currently ringing, in arrival order. The first one owns the single
    // sound + vibration; later concurrent alarms are coalesced into the same
    // session. Snooze and dismiss then apply to every alarm in it — silencing
    // the session while re-arming only the first would drop the others.
    // Main thread only.
    private val activeAlarms = LinkedHashMap<Int, Alarm>()

    private val primaryAlarm: Alarm? get() = activeAlarms.values.firstOrNull()

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

    /** Overridable so tests don't have to wait out the real minute. */
    @VisibleForTesting
    var autoSilenceMs = AUTO_SILENCE_MS

    /** Outlives [autoSilenceMs] so teardown and the confirmation vibration never
     *  run on a lapsed lock. Tracks the override above. */
    private val wakeLockTimeoutMs: Long get() = autoSilenceMs + WAKE_LOCK_GRACE_MS

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
                    startForeground(NOTIFICATION_ID, buildNotification(primary, activeAlarms.size))
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
        val firstAlarm = activeAlarms.isEmpty()
        activeAlarms[alarm.id] = alarm

        if (firstAlarm) {
            sessionGeneration++
            confirmationPending = false
            acquireWakeLock()
            ringingState.setRinging(alarm)
            startForeground(NOTIFICATION_ID, buildNotification(alarm, activeAlarms.size))
            playRingtone(alarm.ringtoneUri, alarm.volume)
            if (alarm.vibrate) startVibration()
            scheduleAutoSilence()
        } else {
            // Already ringing: don't stack a second sound — just refresh the
            // notification to reflect the number of alarms going off.
            startForeground(NOTIFICATION_ID, buildNotification(primaryAlarm ?: alarm, activeAlarms.size))
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
        val lock = wakeLock
            ?: getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                ?.apply { setReferenceCounted(false) }
                ?.also { wakeLock = it }
            ?: return
        // Always re-acquire rather than skipping when already held: a session
        // starting inside the previous one's confirmation window would otherwise
        // inherit its remaining timeout instead of a fresh one. acquire() on a
        // non-counted lock resets the timeout, so a single release still frees it.
        // The timeout is a backstop for an alarm nobody ever dismisses.
        lock.acquire(wakeLockTimeoutMs)
    }

    private fun releaseWakeLock() {
        // The timeout may have already released it.
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun clearSession() {
        activeAlarms.clear()
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
     *  Silences the session after [AUTO_SILENCE_MS], like stock clock apps, and
     *  otherwise behaves exactly like a dismiss: same retirement, same
     *  confirmation, and clearing [ringingState] closes the ringing screen.
     *  What it adds is a missed-alarm notification, the only trace left that
     *  the alarm ever fired.
     *  Runs on Main because it mutates the same session state as the intent
     *  handlers; the generation check makes it a no-op if this session already
     *  ended or a new one took over. */
    private fun scheduleAutoSilence() {
        val generation = sessionGeneration
        scope.launch(Dispatchers.Main) {
            delay(autoSilenceMs)
            if (generation != sessionGeneration || activeAlarms.isEmpty()) return@launch
            // Both of these read activeAlarms, which tearDownSession clears.
            showMissedNotifications()
            retireOneShots()
            tearDownSession()
            confirmThenStop { AlarmSounds.playDismissConfirmation(it) }
        }
    }

    /** One notification per one-shot alarm that rang out. Repeating alarms are
     *  left alone — they ring again on their next day, so a missed notice would
     *  be noise. */
    private fun showMissedNotifications() {
        val missed = activeAlarms.values.filter { it.repeatDays == 0 }
        if (missed.isEmpty()) return
        createMissedChannel()
        val notificationManager = getSystemService(NotificationManager::class.java)
        missed.forEach { alarm ->
            val notification = NotificationCompat.Builder(this, CHANNEL_ID_MISSED)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.alarm_missed))
                .setContentText(getString(R.string.alarm_missed_text, alarm.displayLabel(this)))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(MISSED_NOTIFICATION_BASE + alarm.id, notification)
        }
    }

    private fun stopRinging() {
        val hadSession = activeAlarms.isNotEmpty()
        retireOneShots()
        tearDownSession()
        if (!hadSession) {
            // Nothing was ringing (e.g. dismiss raced an earlier stop) — no
            // confirmation to give. A confirmation already in flight owns the
            // stop, and cutting it short would cancel its snooze write too.
            if (!confirmationPending) stopSelf(latestStartId)
            return
        }
        confirmThenStop { AlarmSounds.playDismissConfirmation(it) }
    }

    /** A dismissed or auto-silenced one-shot has done its job and must not be
     *  left armed-looking: it would show a countdown to a trigger that no longer
     *  exists, and BootReceiver would resurrect it on the next reboot. Repeating
     *  alarms are spared — the decision is made in SQL against the stored row,
     *  because a repeating alarm's snooze re-fire reaches us claiming
     *  repeatDays = 0. Snooze deliberately does not call this: a snoozed alarm
     *  is still pending. */
    private fun retireOneShots() {
        val ids = activeAlarms.keys.filter { it > 0 } // snapshot: main thread only
        if (ids.isEmpty()) return
        appScope.launch { ids.forEach { repository.disableIfOneShot(it) } }
    }

    /** Snooze every alarm in the session and stop the service (shared by the
     *  notification action and the ringing screen). Each alarm keeps its own
     *  snooze length; the confirmation reports the primary's. */
    private fun snoozeAndStop(alarm: Alarm) {
        // Coalesced alarms are silenced together, so they must be re-armed
        // together — re-arming only the tapped one silently drops the rest.
        val toSnooze = activeAlarms.values.toList().ifEmpty { listOf(alarm) }
        val triggers = toSnooze.map { it to scheduler.scheduleSnooze(it, it.snoozeMinutes) }
        tearDownSession()
        // The service is about to stop, so this write cannot live on its scope.
        val persist = appScope.launch {
            triggers.forEach { (snoozed, triggerAt) ->
                if (snoozed.id > 0) repository.setSnoozeUntil(snoozed.id, triggerAt)
            }
        }
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
        val time = formatClockTime(this, alarm.hour, alarm.minute)
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

    /** Created lazily — most sessions end in a dismiss and never post here. */
    private fun createMissedChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_MISSED,
            "Missed alarms",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shown when an alarm rang out with no response"
            setSound(null, null)
            enableVibration(false)
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
        private const val AUTO_SILENCE_MS = 60 * 1000L
        private const val WAKE_LOCK_GRACE_MS = 60 * 1000L

        @VisibleForTesting
        const val CHANNEL_ID_MISSED = "missed_channel"

        /** Above [NOTIFICATION_ID] and [AlarmScheduler]'s snooze base, so a
         *  missed notification never overwrites either. */
        @VisibleForTesting
        const val MISSED_NOTIFICATION_BASE = 3000
    }
}
