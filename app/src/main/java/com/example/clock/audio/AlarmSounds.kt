package com.example.clock.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Shared ringtone and vibration construction for the alarm service and the
 *  editor's volume/vibrate previews. */
object AlarmSounds {

    /** [ringtoneUri] parsed, or the system default alarm sound. */
    fun resolveRingtoneUri(ringtoneUri: String?): Uri =
        ringtoneUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    /** A ringtone configured with alarm audio attributes and per-alarm volume.
     *  Per-alarm volume needs Ringtone.setVolume (API 28+); on older devices it
     *  plays at the system alarm-stream volume. Not started — callers call
     *  play() themselves. */
    fun createRingtone(
        context: Context,
        ringtoneUri: String?,
        volumePercent: Int,
        looping: Boolean
    ): Ringtone? =
        RingtoneManager.getRingtone(context, resolveRingtoneUri(ringtoneUri))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // setLooping and setVolume both need API 28; older devices play the
            // ringtone once at the system alarm-stream volume.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (looping) isLooping = true
                volume = volumePercent.coerceIn(0, 100) / 100f
            }
        }

    /** One-shot buzz used by the editor's vibrate-switch preview. */
    fun vibrateOnce(context: Context, millis: Long = 200) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
    }

    /** Repeating on/off vibration used while an alarm rings; the caller keeps
     *  [vibrator] to cancel() it later. */
    fun startAlarmVibration(vibrator: Vibrator) {
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }
}
