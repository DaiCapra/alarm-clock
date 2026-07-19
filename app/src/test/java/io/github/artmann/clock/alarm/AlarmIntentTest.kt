package io.github.artmann.clock.alarm

import android.content.Intent
import io.github.artmann.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * putAlarm/readAlarm is the wire format between scheduler, receiver, service
 * and ringing screen — a dropped field here silently loses a setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmIntentTest {

    @Test
    fun roundTrip_preservesAllSerializedFields() {
        val alarm = Alarm(
            id = 5,
            hour = 6,
            minute = 45,
            label = "Run",
            repeatDays = 0b0100010,
            ringtoneUri = "content://ring/7",
            vibrate = false,
            snoozeMinutes = 15,
            volume = 40
        )

        val read = Intent().putAlarm(alarm).readAlarm()

        assertEquals(alarm.id, read.id)
        assertEquals(alarm.hour, read.hour)
        assertEquals(alarm.minute, read.minute)
        assertEquals(alarm.label, read.label)
        assertEquals(alarm.repeatDays, read.repeatDays)
        assertEquals(alarm.ringtoneUri, read.ringtoneUri)
        assertEquals(alarm.vibrate, read.vibrate)
        assertEquals(alarm.snoozeMinutes, read.snoozeMinutes)
        assertEquals(alarm.volume, read.volume)
    }

    @Test
    fun emptyIntent_fallsBackToEntityDefaults() {
        val read = Intent().readAlarm()

        assertEquals(-1, read.id)
        assertEquals(0, read.hour)
        assertEquals(0, read.minute)
        assertEquals("", read.label)
        assertEquals(0, read.repeatDays)
        assertNull(read.ringtoneUri)
        // Must match the Alarm entity defaults — single source, no drift.
        assertEquals(Alarm.DEFAULT_VIBRATE, read.vibrate)
        assertEquals(Alarm.DEFAULT_SNOOZE_MINUTES, read.snoozeMinutes)
        assertEquals(Alarm.DEFAULT_VOLUME, read.volume)
    }
}
