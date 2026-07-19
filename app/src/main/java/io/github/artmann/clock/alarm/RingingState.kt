package io.github.artmann.clock.alarm

import io.github.artmann.clock.data.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped record of the alarm currently ringing (if any). Lets the UI open
 * the ringing screen on demand and lets disabling a ringing alarm stop it.
 */
@Singleton
class RingingState @Inject constructor() {
    private val _current = MutableStateFlow<Alarm?>(null)
    val current: StateFlow<Alarm?> = _current.asStateFlow()

    fun setRinging(alarm: Alarm) { _current.value = alarm }
    fun clear() { _current.value = null }
}
