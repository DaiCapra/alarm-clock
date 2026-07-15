package com.example.clock.alarm

import android.app.AlarmManager
import com.example.clock.awaitUntil
import com.example.clock.data.Alarm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Snoozing arms AlarmManager and posts a countdown notification immediately, but
 * persists `snoozeUntil` on a coroutine. If that write is lost the app ends up
 * lying: the alarm really will ring in ten minutes, while the list counts down
 * to tomorrow morning and never shows "Snoozed".
 *
 * The write therefore has to outlive the service that issued it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceSnoozePersistTest : AlarmServiceTestBase() {

    private fun store(alarm: Alarm): Alarm = runBlocking {
        val id = service.repository.addAlarm(alarm).toInt()
        service.repository.getAlarmById(id)!!
    }

    private fun reload(id: Int): Alarm = runBlocking { service.repository.getAlarmById(id)!! }

    @Test
    fun snooze_persistsSnoozeUntil() {
        val alarm = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        service.onStartCommand(startIntent(alarm), 0, 1)

        sendSnoozeAndAwaitStop(alarm, startId = 2)

        awaitUntil(message = "Snooze must persist snoozeUntil") { reload(alarm.id).snoozeUntil > 0 }
    }

    /**
     * The race that used to lose the write: snooze starts a ~250ms confirmation
     * window, and a dismiss landing inside it destroyed the service — cancelling
     * the persist, which ran on the service's own scope.
     */
    @Test
    fun snoozeThenImmediateDismiss_stillPersistsSnoozeUntil() {
        val alarm = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(snoozeIntent(alarm), 0, 2)
        // Inside the confirmation gap, before the snooze path stops the service.
        service.onStartCommand(dismissIntent(), 0, 3)
        // The dismiss stops the service; play out the destroy that follows,
        // which is what used to cancel the write mid-flight.
        destroyService()

        awaitUntil(message = "A dismiss racing the snooze must not lose the write") {
            reload(alarm.id).snoozeUntil > 0
        }

        val scheduled = shadowOf(service.getSystemService(AlarmManager::class.java)).nextScheduledAlarm
        assertTrue("The snooze really is armed, so the DB must agree", scheduled != null)
    }

    /** The same loss, reached the plain way: the service being destroyed at all
     *  must not take the snooze write with it. */
    @Test
    fun snooze_thenServiceDestroyed_stillPersistsSnoozeUntil() {
        val alarm = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(snoozeIntent(alarm), 0, 2)
        destroyService()

        awaitUntil(message = "Destroying the service must not lose the snooze write") {
            reload(alarm.id).snoozeUntil > 0
        }
    }

    /** Two alarms ring as one session. Snooze silences both, so it must re-arm
     *  both — re-arming only the tapped one drops the other for good. */
    @Test
    fun snooze_reArmsEveryAlarmInTheSession() {
        val first = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        val second = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        service.onStartCommand(startIntent(first), 0, 1)
        service.onStartCommand(startIntent(second), 0, 2)

        sendSnoozeAndAwaitStop(first, startId = 3)

        awaitUntil(message = "The tapped alarm must be snoozed") { reload(first.id).snoozeUntil > 0 }
        awaitUntil(message = "The coalesced alarm must be snoozed too, not dropped") {
            reload(second.id).snoozeUntil > 0
        }
    }

    @Test
    fun snooze_eachAlarmKeepsItsOwnSnoozeLength() {
        val short = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 5))
        val long = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 30))
        service.onStartCommand(startIntent(short), 0, 1)
        service.onStartCommand(startIntent(long), 0, 2)

        val before = System.currentTimeMillis()
        sendSnoozeAndAwaitStop(short, startId = 3)

        awaitUntil(message = "Both alarms must be snoozed") {
            reload(short.id).snoozeUntil > 0 && reload(long.id).snoozeUntil > 0
        }
        val shortDelay = reload(short.id).snoozeUntil - before
        val longDelay = reload(long.id).snoozeUntil - before
        assertTrue("5-minute snooze, got ${shortDelay}ms", shortDelay in 4 * 60_000L..6 * 60_000L)
        assertTrue("30-minute snooze, got ${longDelay}ms", longDelay in 29 * 60_000L..31 * 60_000L)
    }

    @Test
    fun snooze_doesNotDisableTheAlarm() {
        val alarm = store(Alarm(hour = 7, minute = 0, snoozeMinutes = 10))
        service.onStartCommand(startIntent(alarm), 0, 1)

        sendSnoozeAndAwaitStop(alarm, startId = 2)

        assertEquals("A snoozed alarm is still pending", true, reload(alarm.id).isEnabled)
    }
}
