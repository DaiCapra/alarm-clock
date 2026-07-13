package com.example.clock.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.example.clock.awaitUntil
import com.example.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Service actions beyond plain ringing: snooze re-scheduling, the vibrate
 * setting, and the silent notification refresh.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceSnoozeTest {

    private lateinit var context: Context
    private lateinit var service: AlarmService

    private lateinit var controller: org.robolectric.android.controller.ServiceController<AlarmService>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(AlarmService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        // Cancels the service scope so the snooze-persist coroutine can't outlive
        // the test and crash on Robolectric's between-test SQLite reset.
        controller.destroy()
    }

    private fun startIntent(alarm: Alarm) =
        Intent(context, AlarmService::class.java).putAlarm(alarm)

    private fun snoozeIntent(alarm: Alarm) =
        Intent(context, AlarmService::class.java)
            .apply { action = AlarmService.ACTION_SNOOZE }
            .putAlarm(alarm)

    private fun refreshIntent() =
        Intent(context, AlarmService::class.java).apply { action = AlarmService.ACTION_REFRESH }

    /** Snooze persists snoozeUntil on a background coroutine, then stopSelf().
     *  Await that stop so the coroutine can't outlive the test and crash on
     *  Robolectric's between-test SQLite reset. */
    private fun sendSnoozeAndAwaitStop(alarm: Alarm, startId: Int) {
        service.onStartCommand(snoozeIntent(alarm), 0, startId)
        awaitUntil(message = "Service did not stop itself after snooze") {
            shadowOf(service).isStoppedBySelf
        }
    }

    @Test
    fun snooze_registersReFireWithAlarmManager() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0, snoozeMinutes = 10)
        service.onStartCommand(startIntent(alarm), 0, 1)

        val before = System.currentTimeMillis()
        sendSnoozeAndAwaitStop(alarm, startId = 2)

        val am = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(am).nextScheduledAlarm
        assertNotNull("Snooze must register a re-fire", scheduled)
        // Fires ~10 minutes out; allow slack but rule out "now" or the past.
        assertTrue(scheduled!!.triggerAtTime >= before + 9 * 60_000L)
    }

    @Test
    fun snooze_endsSession_soNextAlarmRingsAgain() {
        val alarm = Alarm(id = 1, hour = 7, minute = 0)
        service.onStartCommand(startIntent(alarm), 0, 1)
        sendSnoozeAndAwaitStop(alarm, startId = 2)

        service.onStartCommand(startIntent(Alarm(id = 2, hour = 8, minute = 0)), 0, 3)

        assertEquals("A fresh alarm after snooze should ring again", 2, service.ringtoneStartCount)
    }

    @Test
    fun vibrateEnabled_startsVibration() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0, vibrate = true)), 0, 1)

        val vibrator = context.getSystemService(Vibrator::class.java)
        assertTrue(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun vibrateDisabled_doesNotVibrate() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0, vibrate = false)), 0, 1)

        val vibrator = context.getSystemService(Vibrator::class.java)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test
    fun refresh_doesNotStartASecondSound() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0)), 0, 1)
        service.onStartCommand(refreshIntent(), 0, 2)

        assertEquals(1, service.ringtoneStartCount)
    }
}
