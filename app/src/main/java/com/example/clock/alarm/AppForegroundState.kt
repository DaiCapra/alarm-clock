package com.example.clock.alarm

/** Whether the app currently has a visible activity. Read by [AlarmService] to
 *  decide between a silent notification (app open — the UI switches to the
 *  ringing view) and a heads-up/full-screen one (home screen or locked). */
object AppForegroundState {
    @Volatile
    var isForeground: Boolean = false
}
