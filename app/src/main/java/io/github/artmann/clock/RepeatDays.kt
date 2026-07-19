package io.github.artmann.clock

import io.github.artmann.clock.data.Alarm

/**
 * The weekday bits of an alarm's repeat mask, with any stray high bits dropped.
 *
 * Every reader of [Alarm.repeatDays] must go through this: the scheduler and the
 * receiver deciding "is this alarm repeating?" differently is worse than either
 * answer alone — a value like 128 would re-arm forever on one side while the
 * other treated it as a spent one-shot. The DB is cloud-backed-up
 * (`allowBackup="true"`), so a restored row is not necessarily one this UI wrote.
 */
val Alarm.repeatMask: Int get() = repeatDays and ALL_DAYS

/** Whether this alarm repeats, as opposed to firing once. */
val Alarm.isRepeating: Boolean get() = repeatMask != 0

/**
 * Human-readable summary of an alarm's repeat bitmask (bit 0 = Sunday .. bit 6 =
 * Saturday). Shown under each alarm in the main list.
 */
fun formatRepeatDays(repeatDays: Int): String {
    return when (repeatDays and ALL_DAYS) {
        0 -> "Once"
        ALL_DAYS -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> (0..6)
            .filter { (repeatDays shr it) and 1 == 1 }
            .joinToString(", ") { DAY_ABBREVIATIONS[it] }
    }
}

/** All seven weekday bits set (bit 0 = Sunday .. bit 6 = Saturday). */
const val ALL_DAYS = 0b1111111

// Monday..Friday = bits 1..5; Saturday+Sunday = bits 6 and 0.
private const val WEEKDAYS = 0b0111110
private const val WEEKENDS = 0b1000001
private val DAY_ABBREVIATIONS =
    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
