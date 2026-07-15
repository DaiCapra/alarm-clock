package com.example.clock

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Clock-time formatting that follows the device's 12/24-hour setting.
 *
 * Patterns come from [DateFormat.getBestDateTimePattern] rather than a literal
 * like "h:mm a": it places AM/PM and orders the fields the way the user's locale
 * expects, which a hand-written pattern gets wrong outside en-US.
 */

/** The user's clock time for an hour/minute, e.g. "07:05" or "7:05 AM". */
fun formatClockTime(context: Context, hour: Int, minute: Int): String {
    val millis = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return clockFormatter(context).format(Date(millis))
}

/** The user's clock time for an epoch-millis instant. */
fun formatClockTime(context: Context, millis: Long): String =
    clockFormatter(context).format(Date(millis))

/** A fresh hour:minute formatter — cache the result when formatting every
 *  second, and see [ClockFormatter] for keeping it fresh. */
fun clockFormatter(context: Context): SimpleDateFormat =
    SimpleDateFormat(bestPattern(context, withSeconds = false), Locale.getDefault())

private fun bestPattern(context: Context, withSeconds: Boolean): String {
    val skeleton = when {
        DateFormat.is24HourFormat(context) && withSeconds -> "Hms"
        DateFormat.is24HourFormat(context) -> "Hm"
        withSeconds -> "hms"
        else -> "hm"
    }
    return DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
}

/**
 * A formatter for a live clock, rebuilt only when the 12/24-hour setting flips.
 *
 * Changing that setting does not recreate an Activity, so a formatter built once
 * at construction would keep the old format for the life of the screen. Checking
 * per tick costs one settings read; rebuilding per tick would allocate a
 * [SimpleDateFormat] every second.
 */
class ClockFormatter(private val context: Context, private val withSeconds: Boolean) {

    private var is24Hour: Boolean? = null
    private var formatter: SimpleDateFormat? = null

    fun format(millis: Long): String {
        val current = DateFormat.is24HourFormat(context)
        if (current != is24Hour || formatter == null) {
            is24Hour = current
            formatter = SimpleDateFormat(bestPattern(context, withSeconds), Locale.getDefault())
        }
        return formatter!!.format(Date(millis))
    }
}
