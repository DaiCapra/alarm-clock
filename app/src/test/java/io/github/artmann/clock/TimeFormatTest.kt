package io.github.artmann.clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.util.Calendar

/**
 * Clock-time formatting must follow the device's 12/24-hour setting — the app
 * used to hard-code 24-hour and show "19:30" to users whose every other app said
 * "7:30 PM".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimeFormatTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun millisAt(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 7, hour, minute)
        }.timeInMillis

    @Test
    fun hourMinute_is24Hour_whenTheDeviceIs() {
        ShadowSettings.set24HourTimeFormat(true)

        assertEquals("07:05", formatClockTime(context, 7, 5))
        assertEquals("00:00", formatClockTime(context, 0, 0))
        assertEquals("23:59", formatClockTime(context, 23, 59))
    }

    @Test
    fun hourMinute_is12Hour_whenTheDeviceIs() {
        ShadowSettings.set24HourTimeFormat(false)

        // Don't pin the exact separator/spacing — that's the locale's business.
        // What matters is the 12-hour clock and a meridiem marker.
        val evening = formatClockTime(context, 19, 30)
        assertTrue("Expected a 12-hour time, got $evening", evening.startsWith("7:30"))
        assertTrue("Expected a meridiem marker, got $evening", evening.contains("PM", ignoreCase = true))

        val morning = formatClockTime(context, 7, 5)
        assertTrue("Expected a 12-hour time, got $morning", morning.startsWith("7:05"))
        assertTrue("Expected a meridiem marker, got $morning", morning.contains("AM", ignoreCase = true))
    }

    @Test
    fun midnightAndNoon_readAs12_in12HourMode() {
        ShadowSettings.set24HourTimeFormat(false)

        assertTrue(formatClockTime(context, 0, 0).startsWith("12:00"))
        assertTrue(formatClockTime(context, 12, 0).startsWith("12:00"))
    }

    @Test
    fun epochMillis_formatsLocalWallClock() {
        ShadowSettings.set24HourTimeFormat(true)

        assertEquals("09:05", formatClockTime(context, millisAt(9, 5)))
    }

    @Test
    fun bothOverloads_agree() {
        ShadowSettings.set24HourTimeFormat(true)

        assertEquals(formatClockTime(context, millisAt(22, 40)), formatClockTime(context, 22, 40))
    }

    /**
     * Flipping the system setting does not recreate an Activity, so a live clock
     * that cached its formatter at construction would keep the old format for the
     * life of the screen.
     */
    @Test
    fun clockFormatter_picksUpAChangeToTheSetting() {
        ShadowSettings.set24HourTimeFormat(true)
        val formatter = ClockFormatter(context, withSeconds = false)
        assertEquals("19:30", formatter.format(millisAt(19, 30)))

        ShadowSettings.set24HourTimeFormat(false)

        val after = formatter.format(millisAt(19, 30))
        assertTrue("Formatter must follow the setting, got $after", after.startsWith("7:30"))
    }

    @Test
    fun clockFormatter_withSeconds_includesThem() {
        ShadowSettings.set24HourTimeFormat(true)

        val formatted = ClockFormatter(context, withSeconds = true).format(millisAt(9, 5))

        assertEquals("09:05:00", formatted)
    }
}
