package io.github.artmann.clock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator

/** Shared ringtone and vibration construction for the alarm service and the
 *  editor's volume/vibrate previews. */
object AlarmSounds {

    /** [ringtoneUri] parsed, or the system default alarm sound. */
    fun resolveRingtoneUri(ringtoneUri: String?): Uri =
        ringtoneUri?.let(Uri::parse) ?: defaultUri()

    /** The system default alarm sound. */
    private fun defaultUri(): Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    /** A ringtone configured with alarm audio attributes and per-alarm volume.
     *  Falls back to the system default alarm sound if [ringtoneUri] can't be
     *  loaded (deleted file, revoked SD card, unreadable provider) — a silent
     *  alarm is worse than the wrong tone. Not started — callers call play()
     *  themselves. */
    fun createRingtone(
        context: Context,
        ringtoneUri: String?,
        volumePercent: Int,
        looping: Boolean
    ): Ringtone? {
        val chosen = resolveRingtoneUri(ringtoneUri)
        val ringtone = RingtoneManager.getRingtone(context, chosen)
            ?: defaultUri().takeIf { it != chosen }
                ?.let { RingtoneManager.getRingtone(context, it) }
            ?: return null

        return ringtone.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = looping
            volume = volumePercent.coerceIn(0, 100) / 100f
        }
    }

    /** One-shot buzz used by the editor's vibrate-switch preview. */
    fun vibrateOnce(context: Context, millis: Long = 200) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Repeating on/off vibration used while an alarm rings; the caller keeps
     *  [vibrator] to cancel() it later. */
    fun startAlarmVibration(vibrator: Vibrator) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
    }

    /** Gentle double-pulse confirming a snooze registered. Returns the pattern
     *  length in ms so the caller knows how long to stay alive. */
    fun playSnoozeConfirmation(vibrator: Vibrator): Long {
        val timings = longArrayOf(0, 80, 120, 80)
        val amplitudes = intArrayOf(0, 140, 0, 140)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        return timings.sum()
    }

    /** Single long strong buzz confirming a dismiss registered. Returns the
     *  pattern length in ms so the caller knows how long to stay alive. */
    fun playDismissConfirmation(vibrator: Vibrator): Long {
        val millis = 400L
        vibrator.vibrate(VibrationEffect.createOneShot(millis, 255))
        return millis
    }
}
