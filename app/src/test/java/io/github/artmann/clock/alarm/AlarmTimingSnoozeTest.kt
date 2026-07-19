package io.github.artmann.clock.alarm

import io.github.artmann.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A pending snooze fires before the alarm's regular schedule. */
class AlarmTimingSnoozeTest {

    private val now = 1_700_000_000_000L // fixed reference instant

    @Test
    fun pendingSnooze_isReturnedAsNextTrigger() {
        val snoozeAt = now + 5 * 60_000L
        val alarm = Alarm(hour = 7, minute = 0, snoozeUntil = snoozeAt)
        assertEquals(snoozeAt, AlarmTiming.nextTrigger(alarm, now))
    }

    @Test
    fun expiredSnooze_isIgnored() {
        val alarm = Alarm(hour = 7, minute = 0, snoozeUntil = now - 1_000L)
        assertNotEquals(alarm.snoozeUntil, AlarmTiming.nextTrigger(alarm, now))
    }
}
