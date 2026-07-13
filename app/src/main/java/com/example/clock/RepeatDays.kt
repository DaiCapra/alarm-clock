package com.example.clock

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
