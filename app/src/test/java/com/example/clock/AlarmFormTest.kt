package com.example.clock

import com.example.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Add/Edit alarm form mapping ([AlarmForm]) — verifies the
 * screen turns its inputs (label, days, ringtone, vibration, snooze) into the
 * right [Alarm]. Pure JUnit, no Android/Hilt needed.
 */
class AlarmFormTest {

    private val noDays = List(7) { false }
    private val allDays = List(7) { true }
    private val base = Alarm(hour = 7, minute = 0)

    private fun build(
        base: Alarm = this.base,
        label: String = "",
        dayChecked: List<Boolean> = allDays,
        vibrate: Boolean = true,
        snoozeMinutes: Int = 10,
        volume: Int = 100
    ) = AlarmForm.buildAlarm(
        base = base,
        hour = 6,
        minute = 45,
        label = label,
        dayChecked = dayChecked,
        vibrate = vibrate,
        snoozeMinutes = snoozeMinutes,
        volume = volume
    )

    @Test
    fun label_isTrimmedAndSet() {
        assertEquals("Wake up", build(label = "  Wake up  ").label)
    }

    @Test
    fun label_canBeEmpty() {
        assertEquals("", build(label = "").label)
    }

    @Test
    fun vibration_true_isCarried() {
        assertTrue(build(vibrate = true).vibrate)
    }

    @Test
    fun vibration_false_isCarried() {
        assertFalse(build(vibrate = false).vibrate)
    }

    @Test
    fun ringtone_uri_isCarriedFromBase() {
        val uri = "content://media/internal/audio/media/42"
        assertEquals(uri, build(base = base.copy(ringtoneUri = uri)).ringtoneUri)
    }

    @Test
    fun ringtone_null_meansDefault() {
        assertNull(build(base = base.copy(ringtoneUri = null)).ringtoneUri)
    }

    @Test
    fun snoozeDuration_isCarried() {
        assertEquals(20, build(snoozeMinutes = 20).snoozeMinutes)
    }

    @Test
    fun volume_isCarried() {
        assertEquals(40, build(volume = 40).volume)
    }

    @Test
    fun volume_isClampedTo0_100() {
        assertEquals(100, build(volume = 150).volume)
        assertEquals(0, build(volume = -5).volume)
    }

    @Test
    fun days_areEncodedAsBitmask() {
        // Sunday (bit 0) + Wednesday (bit 3) only.
        val checked = listOf(true, false, false, true, false, false, false)
        assertEquals(0b0001001, build(dayChecked = checked).repeatDays)
    }

    @Test
    fun days_allSelected_is127() {
        assertEquals(0b1111111, build(dayChecked = allDays).repeatDays)
    }

    @Test
    fun days_noneSelected_isZero() {
        assertEquals(0, build(dayChecked = noDays).repeatDays)
    }

    @Test
    fun savedAlarm_isAlwaysEnabled() {
        assertTrue(build().isEnabled)
    }

    @Test
    fun parseSnooze_extractsLeadingInteger() {
        assertEquals(15, AlarmForm.parseSnoozeMinutes("15 min", default = 10))
    }

    @Test
    fun parseSnooze_fallsBackToDefault_whenNoDigits() {
        assertEquals(10, AlarmForm.parseSnoozeMinutes("none", default = 10))
    }

    @Test
    fun parseSnooze_fallsBackToDefault_whenEmpty() {
        assertEquals(10, AlarmForm.parseSnoozeMinutes("", default = 10))
    }

    @Test
    fun parseSnooze_concatenatesAllDigits() {
        // filter { isDigit } keeps every digit, not just the leading run.
        assertEquals(15, AlarmForm.parseSnoozeMinutes("1a5 min", default = 10))
    }

    @Test
    fun buildAlarm_clearsPendingSnooze() {
        val rebuilt = build(base = base.copy(snoozeUntil = 12345L))
        assertEquals("Editing an alarm must drop its stale snooze", 0L, rebuilt.snoozeUntil)
    }

    @Test
    fun defaultSnoozeDuration_isTenMinutes() {
        assertEquals(10, Alarm(hour = 0, minute = 0).snoozeMinutes)
    }
}
