package com.example.clock

import android.content.Context
import android.media.Ringtone
import android.view.View
import com.example.clock.audio.AlarmSounds

/**
 * Plays a short clip of a ringtone at a chosen volume. Owns the stop-after-delay
 * choreography; call [stop] when leaving the screen.
 *
 * Deliberately not the alarm service: a preview must not hold a wake lock, take
 * over [com.example.clock.alarm.RingingState], arm anything with AlarmManager or
 * write to the database. Being bounded and lifecycle-bound is the whole point.
 */
class RingtonePreview(
    private val context: Context,
    private val handlerView: View,
    private val clipMs: Long = EDITOR_CLIP_MS
) {

    private var ringtone: Ringtone? = null
    private val stopClip = Runnable { ringtone?.stop() }

    /** Plays [ringtoneUri], replacing any clip already playing. */
    fun play(ringtoneUri: String?, volumePercent: Int) {
        handlerView.removeCallbacks(stopClip)
        ringtone?.stop()
        ringtone = AlarmSounds.createRingtone(context, ringtoneUri, volumePercent, looping = false)
            ?.apply { play() }
        handlerView.postDelayed(stopClip, clipMs)
    }

    fun isPlaying(): Boolean = ringtone?.isPlaying == true

    fun stop() {
        handlerView.removeCallbacks(stopClip)
        ringtone?.stop()
    }

    companion object {
        /** Just long enough to judge tone and volume while dragging the slider. */
        const val EDITOR_CLIP_MS = 1500L

        /** A "what will this sound like?" sample from the alarm list. */
        const val SAMPLE_CLIP_MS = 10_000L
    }
}
