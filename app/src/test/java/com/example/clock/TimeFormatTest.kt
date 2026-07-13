package com.example.clock

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Shared HH:mm clock-time formatting. */
class TimeFormatTest {

    @Test
    fun hourMinute_isZeroPadded() {
        assertEquals("07:05", formatClockTime(7, 5))
        assertEquals("00:00", formatClockTime(0, 0))
        assertEquals("23:59", formatClockTime(23, 59))
    }

    @Test
    fun epochMillis_formatsLocalWallClock() {
        val millis = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 7, 9, 5)
        }.timeInMillis
        assertEquals("09:05", formatClockTime(millis))
    }

    @Test
    fun formatterAndMillisOverload_agree() {
        val millis = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 7, 22, 40)
        }.timeInMillis
        assertEquals(formatClockTime(millis), hhmmFormatter().format(java.util.Date(millis)))
    }
}
