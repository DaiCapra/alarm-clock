package com.example.clock.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two alarms firing at once must coalesce into a single ringing session — one
 * sound, cleanly cleared on dismiss (option A).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceConcurrentTest {

    private lateinit var context: Context
    private lateinit var service: AlarmService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Robolectric.buildService(AlarmService::class.java).create().get()
    }

    private fun startIntent(alarm: Alarm) =
        Intent(context, AlarmService::class.java).putAlarm(alarm)

    private fun dismissIntent() =
        Intent(context, AlarmService::class.java).apply { action = AlarmService.ACTION_DISMISS }

    @Test
    fun twoAlarmsAtSameTime_startSoundOnlyOnce() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0)), 0, 1)
        service.onStartCommand(startIntent(Alarm(id = 2, hour = 7, minute = 0)), 0, 2)

        assertEquals("Second concurrent alarm must not start a second sound", 1, service.ringtoneStartCount)
    }

    @Test
    fun dismiss_thenNewAlarm_startsSoundAgain() {
        service.onStartCommand(startIntent(Alarm(id = 1, hour = 7, minute = 0)), 0, 1)
        service.onStartCommand(dismissIntent(), 0, 2)
        service.onStartCommand(startIntent(Alarm(id = 3, hour = 8, minute = 0)), 0, 3)

        assertEquals("A fresh session after dismiss should ring again", 2, service.ringtoneStartCount)
    }
}
