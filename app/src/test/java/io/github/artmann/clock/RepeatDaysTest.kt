package io.github.artmann.clock

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the weekday summary shown under each alarm in the main list. */
class RepeatDaysTest {

    @Test
    fun once_whenNoDaysSet() {
        assertEquals("Once", formatRepeatDays(0))
    }

    @Test
    fun everyDay_whenAllSet() {
        assertEquals("Every day", formatRepeatDays(0b1111111))
    }

    @Test
    fun weekdays_monToFri() {
        // bits 1..5 = Mon..Fri
        assertEquals("Weekdays", formatRepeatDays(0b0111110))
    }

    @Test
    fun weekends_satAndSun() {
        // bit 0 = Sun, bit 6 = Sat
        assertEquals("Weekends", formatRepeatDays(0b1000001))
    }

    @Test
    fun subset_listedInWeekOrder() {
        // Sun (bit0) + Wed (bit3) + Fri (bit5)
        assertEquals("Sun, Wed, Fri", formatRepeatDays(0b0101001))
    }

    @Test
    fun singleDay() {
        // Mon = bit 1
        assertEquals("Mon", formatRepeatDays(0b0000010))
    }
}
