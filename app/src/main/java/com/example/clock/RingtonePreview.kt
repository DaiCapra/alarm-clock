package com.example.clock

import android.content.Context
import android.media.Ringtone
import android.view.View
import com.example.clock.audio.AlarmSounds

/** Plays a short clip of the draft ringtone at the chosen volume. Owns the
 *  stop-after-delay choreography; call [stop] when leaving the screen. */
class RingtonePreview(private val context: Context, private val handlerView: View) {

    private var ringtone: Ringtone? = null
    private val stopClip = Runnable { ringtone?.stop() }

    fun play(ringtoneUri: String?, volumePercent: Int) {
        handlerView.removeCallbacks(stopClip)
        ringtone?.stop()
        ringtone = AlarmSounds.createRingtone(context, ringtoneUri, volumePercent, looping = false)
            ?.apply { play() }
        handlerView.postDelayed(stopClip, CLIP_MS)
    }

    fun stop() {
        handlerView.removeCallbacks(stopClip)
        ringtone?.stop()
    }

    private companion object {
        const val CLIP_MS = 1500L
    }
}
