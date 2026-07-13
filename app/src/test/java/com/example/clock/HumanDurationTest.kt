package com.example.clock

import org.junit.Assert.assertEquals
import org.junit.Test

/** Wording of the "Alarm will trigger in …" toasts. */
class HumanDurationTest {

    @Test
    fun underAMinute() {
        assertEquals("less than a minute", humanDuration(30_000))
        assertEquals("less than a minute", humanDuration(0))
    }

    @Test
    fun negative_isTreatedAsZero() {
        assertEquals("less than a minute", humanDuration(-5_000))
    }

    @Test
    fun singularMinute() {
        assertEquals("1 minute", humanDuration(60_000))
    }

    @Test
    fun pluralMinutes() {
        assertEquals("5 minutes", humanDuration(5 * 60_000L))
    }

    @Test
    fun singularHour() {
        assertEquals("1 hour", humanDuration(60 * 60_000L))
    }

    @Test
    fun pluralHours() {
        assertEquals("3 hours", humanDuration(3 * 60 * 60_000L))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals("2 hours and 5 minutes", humanDuration((2 * 60 + 5) * 60_000L))
        assertEquals("1 hour and 1 minute", humanDuration(61 * 60_000L))
    }

    @Test
    fun secondsAreTruncated_notRounded() {
        assertEquals("1 minute", humanDuration(119_000)) // 1m59s -> "1 minute"
    }
}
