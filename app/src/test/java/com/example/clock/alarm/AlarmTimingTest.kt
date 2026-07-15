package com.example.clock.alarm

import com.example.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Core scheduling logic: one-shot rollover and repeat-day selection in
 * [AlarmTiming.nextTrigger]. Snooze precedence is covered separately in
 * [AlarmTimingSnoozeTest].
 *
 * Reference instant: Wednesday 2026-01-07 (local time), so
 * Sun=Jan 4, Mon=Jan 5 .. Sat=Jan 10.
 */
class AlarmTimingTest {

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, day, hour, minute, second)
        }.timeInMillis

    private val wednesday7am = at(day = 7, hour = 7, minute = 0)

    // --- One-shot (repeatDays = 0) ---

    @Test
    fun oneShot_timeStillAhead_firesToday() {
        val alarm = Alarm(hour = 8, minute = 30)
        assertEquals(at(day = 7, hour = 8, minute = 30), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun oneShot_timeAlreadyPassed_firesTomorrow() {
        val alarm = Alarm(hour = 6, minute = 0)
        assertEquals(at(day = 8, hour = 6, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun oneShot_exactlyNow_firesTomorrow() {
        val alarm = Alarm(hour = 7, minute = 0)
        assertEquals(at(day = 8, hour = 7, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun oneShot_secondsAreHonored() {
        val alarm = Alarm(hour = 8, minute = 0, second = 30)
        assertEquals(
            at(day = 7, hour = 8, minute = 0, second = 30),
            AlarmTiming.nextTrigger(alarm, wednesday7am)
        )
    }

    // --- Repeating (bit 0 = Sunday .. bit 6 = Saturday) ---

    @Test
    fun repeat_sameDayTimeAhead_firesToday() {
        val alarm = Alarm(hour = 8, minute = 0, repeatDays = 0b0001000) // Wed only
        assertEquals(at(day = 7, hour = 8, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_singleDayAlreadyPassed_firesNextWeek() {
        val alarm = Alarm(hour = 6, minute = 0, repeatDays = 0b0001000) // Wed only
        assertEquals(at(day = 14, hour = 6, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_exactlyNow_firesNextWeek() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0b0001000) // Wed only
        assertEquals(at(day = 14, hour = 7, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_picksNextMatchingWeekday() {
        // Mon + Fri; now Wednesday -> Friday Jan 9.
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0b0100010)
        assertEquals(at(day = 9, hour = 7, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_wrapsIntoNextWeek() {
        // Sunday only; now Wednesday -> Sunday Jan 11.
        val alarm = Alarm(hour = 7, minute = 0, repeatDays = 0b0000001)
        assertEquals(at(day = 11, hour = 7, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_everyDay_behavesLikeDaily() {
        val early = Alarm(hour = 6, minute = 0, repeatDays = 0b1111111)
        val late = Alarm(hour = 8, minute = 0, repeatDays = 0b1111111)
        assertEquals(at(day = 8, hour = 6, minute = 0), AlarmTiming.nextTrigger(early, wednesday7am))
        assertEquals(at(day = 7, hour = 8, minute = 0), AlarmTiming.nextTrigger(late, wednesday7am))
    }

    // --- Garbage masks (the DB is cloud-backed-up, so rows this UI never wrote
    //     can come back from a restore) ---

    @Test
    fun repeat_highBitsOnly_treatedAsOneShot() {
        // Bit 7+ with no weekday bit set. Before masking this fell through the
        // weekday loop and armed *today* — a time already past, which fires
        // immediately, reschedules to the same past instant, and loops forever.
        val alarm = Alarm(hour = 6, minute = 0, repeatDays = 128)
        assertEquals(at(day = 8, hour = 6, minute = 0), AlarmTiming.nextTrigger(alarm, wednesday7am))
    }

    @Test
    fun repeat_garbageMasks_neverReturnATimeInThePast() {
        val masks = listOf(128, 256, -128, Int.MIN_VALUE, -1, Int.MAX_VALUE)
        for (mask in masks) {
            val trigger = AlarmTiming.nextTrigger(
                Alarm(hour = 6, minute = 0, repeatDays = mask), wednesday7am
            )
            assertTrue(
                "repeatDays=$mask armed $trigger, at or before now ($wednesday7am)",
                trigger > wednesday7am
            )
        }
    }
}
