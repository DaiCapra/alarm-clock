package com.example.clock

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "07:05" from hour/minute values. */
fun formatClockTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** "07:05" for an epoch-millis instant. */
fun formatClockTime(millis: Long): String = hhmmFormatter().format(Date(millis))

/** A fresh HH:mm formatter — cache the result when formatting every second. */
fun hhmmFormatter(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
