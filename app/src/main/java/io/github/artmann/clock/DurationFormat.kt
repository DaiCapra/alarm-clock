package io.github.artmann.clock

/** The toast shown whenever an alarm gets (re)scheduled — one wording everywhere. */
fun alarmTriggerMessage(remainingMillis: Long): String =
    "Alarm will trigger in ${humanDuration(remainingMillis)}"

/** "5 hours and 3 minutes" for a positive duration; used by the scheduling toasts. */
fun humanDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val parts = buildList {
        if (hours > 0) add(if (hours == 1) "1 hour" else "$hours hours")
        if (minutes > 0) add(if (minutes == 1) "1 minute" else "$minutes minutes")
    }
    return if (parts.isEmpty()) "less than a minute" else parts.joinToString(" and ")
}
