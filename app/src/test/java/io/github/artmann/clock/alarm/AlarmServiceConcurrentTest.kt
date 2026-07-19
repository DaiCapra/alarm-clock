package io.github.artmann.clock.alarm

import io.github.artmann.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two alarms firing at once must coalesce into a single ringing session — one
 * sound, cleanly cleared on dismiss (option A).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceConcurrentTest : AlarmServiceTestBase() {

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
