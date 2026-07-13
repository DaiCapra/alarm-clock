package com.example.clock

import android.content.Context
import com.example.clock.data.Alarm

/** The alarm's label, falling back to the generic "Alarm" when empty. */
fun Alarm.displayLabel(context: Context): String =
    label.ifEmpty { context.getString(R.string.alarm) }
