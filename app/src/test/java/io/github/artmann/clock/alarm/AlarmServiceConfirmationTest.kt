package io.github.artmann.clock.alarm

import io.github.artmann.clock.awaitUntil
import io.github.artmann.clock.data.Alarm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Confirmation vibrations on snooze/dismiss: the alarm vibration is cancelled,
 * then after a short gap a distinct pattern confirms the press registered,
 * and only then does the service stop itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceConfirmationTest : AlarmServiceTestBase() {

    @Test
    fun dismiss_playsLongBuzzConfirmation_thenStops() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0, vibrate = true)), 0, 1)
        assertArrayEquals(longArrayOf(0, 500, 500), shadowVibrator().pattern)

        service.onStartCommand(dismissIntent(), 0, 2)

        awaitUntil(message = "Dismiss confirmation buzz did not play") {
            shadowVibrator().milliseconds == 400L
        }
        awaitUntil(message = "Service did not stop after the confirmation") {
            shadowOf(service).isStoppedBySelf
        }
    }

    @Test
    fun snooze_playsDoublePulseConfirmation_thenStops() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, vibrate = true)
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(snoozeIntent(alarm), 0, 2)

        awaitUntil(message = "Snooze confirmation pulse did not play") {
            shadowVibrator().pattern?.contentEquals(longArrayOf(0, 80, 120, 80)) == true
        }
        awaitUntil(message = "Service did not stop after the confirmation") {
            shadowOf(service).isStoppedBySelf
        }
    }

    @Test
    fun confirmation_firesEvenWhenAlarmVibrateOff() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0, vibrate = false)), 0, 1)
        assertFalse(shadowVibrator().isVibrating)

        service.onStartCommand(dismissIntent(), 0, 2)

        awaitUntil(message = "Confirmation should play regardless of the vibrate setting") {
            shadowVibrator().milliseconds == 400L
        }
    }

    @Test
    fun newAlarmDuringConfirmationWindow_keepsServiceAlive_andAlarmVibration() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0, vibrate = true)), 0, 1)
        service.onStartCommand(dismissIntent(), 0, 2)
        service.onStartCommand(startIntent(Alarm(id = 2, hour = 8, minute = 0, vibrate = true)), 0, 3)

        // Wait past the gap + longest confirmation so a wrongly-fired one-shot
        // or stopSelf would have happened by now.
        Thread.sleep(1_200)

        assertFalse("New session must keep the service alive", shadowOf(service).isStoppedBySelf)
        assertEquals("New alarm must ring again", 2, service.ringtoneStartCount)
        assertArrayEquals(
            "Confirmation must not replace the new alarm's vibration",
            longArrayOf(0, 500, 500), shadowVibrator().pattern
        )
        assertEquals(0L, shadowVibrator().milliseconds)
    }

    @Test
    fun refresh_withNoSession_stopsService() {
        service.onStartCommand(refreshIntent(), 0, 1)

        awaitUntil(message = "A refresh with nothing ringing should stop the service") {
            shadowOf(service).isStoppedBySelf
        }
    }
}
