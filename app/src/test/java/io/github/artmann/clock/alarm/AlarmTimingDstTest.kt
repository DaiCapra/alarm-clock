package io.github.artmann.clock.alarm

import io.github.artmann.clock.data.Alarm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pins how [AlarmTiming] resolves alarms around a daylight-saving transition.
 *
 * These are characterization tests: they record the behaviour that falls out of
 * [Calendar]'s lenient wall-clock arithmetic, because for an alarm clock that
 * behaviour is the desirable one. Ringing an hour late beats not ringing.
 */
class AlarmTimingDstTest {

    private val newYork = TimeZone.getTimeZone("America/New_York")
    private lateinit var systemZone: TimeZone

    @Before
    fun setUp() {
        // Restored in tearDown — a leaked default zone makes every sibling
        // Calendar test silently assert in the wrong offset.
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(newYork)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(systemZone)
    }

    /** US spring-forward 2026: 02:00 EST jumps straight to 03:00 EDT, so 02:30
     *  does not exist that day. The alarm must still ring — an hour late by wall
     *  clock, but exactly one hour after 01:30, which is what the user's night
     *  actually is. */
    @Test
    fun springForward_alarmInTheSkippedHour_ringsAfterTheGap() {
        val now = localMillis(2026, Calendar.MARCH, 8, hour = 1, minute = 0)

        val trigger = AlarmTiming.nextTrigger(
            Alarm(id = 1, hour = 2, minute = 30, repeatDays = 0), now
        )

        assertEquals("Rings 90 minutes after 01:00 EST", now + 90 * 60_000L, trigger)
        assertEquals("Wall clock reads 03:30 — 02:30 never happens", 3, hourOf(trigger))
        assertEquals(30, minuteOf(trigger))
    }

    /** An alarm outside the gap on a spring-forward day is unaffected. */
    @Test
    fun springForward_alarmOutsideTheGap_isUnaffected() {
        val now = localMillis(2026, Calendar.MARCH, 8, hour = 1, minute = 0)

        val trigger = AlarmTiming.nextTrigger(
            Alarm(id = 1, hour = 7, minute = 0, repeatDays = 0), now
        )

        assertEquals(7, hourOf(trigger))
        assertEquals(0, minuteOf(trigger))
    }

    /** US fall-back 2026: 01:00-02:00 EDT repeats as 01:00-02:00 EST, so 01:30
     *  happens twice. Calendar resolves the ambiguity using the standard-time
     *  offset, so the alarm lands on the *second* 01:30. What matters is that it
     *  rings exactly once and the wall clock reads 01:30 either way. */
    @Test
    fun fallBack_ambiguousAlarm_firesOnceAtWallClock0130() {
        val now = localMillis(2026, Calendar.NOVEMBER, 1, hour = 0, minute = 30)

        val trigger = AlarmTiming.nextTrigger(
            Alarm(id = 1, hour = 1, minute = 30, repeatDays = 0), now
        )

        assertEquals("Wall clock reads 01:30", 1, hourOf(trigger))
        assertEquals(30, minuteOf(trigger))
        assertEquals(1, dayOf(trigger))
        assertEquals(
            "Resolves to the second (EST) 01:30, two hours after 00:30 EDT",
            now + 2 * 60 * 60_000L, trigger
        )

        // Rescheduling from just after it must roll to the next day rather than
        // finding another 01:30 today.
        val next = AlarmTiming.nextTrigger(
            Alarm(id = 1, hour = 1, minute = 30, repeatDays = 0), trigger + 1
        )
        assertEquals("Must not re-fire at the repeated 01:30", 2, dayOf(next))
    }

    /** A repeating alarm keeps its wall-clock time across the transition — the
     *  point of a 07:00 daily alarm is 07:00, not "every 24 hours". */
    @Test
    fun repeating_acrossSpringForward_keepsWallClockTime() {
        // Saturday 2026-03-07 08:00, the day before the transition.
        val now = localMillis(2026, Calendar.MARCH, 7, hour = 8, minute = 0)

        val trigger = AlarmTiming.nextTrigger(
            Alarm(id = 1, hour = 7, minute = 0, repeatDays = io.github.artmann.clock.ALL_DAYS), now
        )

        assertEquals("Next day, still 07:00 wall clock despite the lost hour", 8, dayOf(trigger))
        assertEquals(7, hourOf(trigger))
        // Only 22 real hours elapse where 23 normally would: the clock jumped
        // forward, and the alarm follows the wall clock rather than the interval.
        assertEquals(22 * 60 * 60_000L, trigger - now)
    }

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(newYork).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun fieldOf(millis: Long, field: Int): Int =
        Calendar.getInstance(newYork).apply { timeInMillis = millis }.get(field)

    private fun hourOf(millis: Long) = fieldOf(millis, Calendar.HOUR_OF_DAY)
    private fun minuteOf(millis: Long) = fieldOf(millis, Calendar.MINUTE)
    private fun dayOf(millis: Long) = fieldOf(millis, Calendar.DAY_OF_MONTH)
}
