package com.example.clock.alarm

import android.os.Looper
import com.example.clock.ALL_DAYS
import com.example.clock.awaitUntil
import com.example.clock.data.Alarm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * A one-shot alarm that has finished ringing must stop counting as armed. Left
 * enabled it shows a countdown to a trigger that no longer exists, and — worse —
 * BootReceiver re-arms every enabled alarm, so it comes back from the dead on
 * the next reboot.
 *
 * Repeating alarms must survive the same path untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceOneShotTest : AlarmServiceTestBase() {

    private fun store(alarm: Alarm): Alarm = runBlocking {
        val id = service.repository.addAlarm(alarm).toInt()
        service.repository.getAlarmById(id)!!
    }

    private fun reload(id: Int): Alarm = runBlocking { service.repository.getAlarmById(id)!! }

    private fun awaitEnabled(id: Int, expected: Boolean, message: String) =
        awaitUntil(message = message) { reload(id).isEnabled == expected }

    @Test
    fun dismiss_retiresAFiredOneShot() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(dismissIntent(), 0, 2)

        awaitEnabled(alarm.id, false, "A dismissed one-shot must not stay enabled")
    }

    @Test
    fun dismiss_leavesARepeatingAlarmEnabled() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS))
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(dismissIntent(), 0, 2)

        // Give the retirement write the same chance to land as above.
        awaitUntil(message = "Service did not stop") { true }
        Thread.sleep(300)
        assertTrue("A daily alarm must survive being dismissed", reload(alarm.id).isEnabled)
    }

    /**
     * The trap. `scheduleSnooze` arms the re-fire with `repeatDays = 0` so it
     * fires once, which means a *repeating* alarm's snooze re-fire reaches the
     * service claiming to be a one-shot. Deciding from the in-flight alarm would
     * permanently disable it; the stored row is the only source of truth.
     */
    @Test
    fun dismissingTheSnoozeRefireOfARepeatingAlarm_doesNotDisableIt() {
        val stored = store(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS))
        // Exactly what AlarmReceiver delivers for a snooze re-fire.
        val asRefired = stored.copy(repeatDays = 0)

        service.onStartCommand(startIntent(asRefired), 0, 1)
        service.onStartCommand(dismissIntent(), 0, 2)

        Thread.sleep(300)
        assertTrue(
            "A repeating alarm dismissed via its snooze re-fire must stay enabled",
            reload(stored.id).isEnabled
        )
    }

    /** Snooze is not a terminal event — the alarm is still pending, so retiring
     *  it here would throw away the snooze that was just armed. */
    @Test
    fun snooze_doesNotRetireAOneShot() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.onStartCommand(startIntent(alarm), 0, 1)

        sendSnoozeAndAwaitStop(alarm, startId = 2)

        assertTrue("A snoozed one-shot is still pending", reload(alarm.id).isEnabled)
    }

    /** Auto-silence is posted to the main looper (it touches session state), and
     *  Robolectric's looper is paused — so drive it rather than waiting. */
    private fun elapse(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun autoSilence_retiresAFiredOneShot() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.autoSilenceMs = 50
        service.onStartCommand(startIntent(alarm), 0, 1)

        elapse(60)

        awaitEnabled(alarm.id, false, "An auto-silenced one-shot must not stay enabled")
    }

    @Test
    fun autoSilence_silencesTheSession() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.autoSilenceMs = 50
        service.onStartCommand(startIntent(alarm), 0, 1)
        assertEquals("Ringing before auto-silence", alarm.id, service.ringingState.current.value?.id)

        elapse(60)

        assertEquals(
            "Auto-silence should clear the ringing state",
            null, service.ringingState.current.value
        )
    }

    @Test
    fun autoSilence_doesNotFireEarly() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.autoSilenceMs = 10_000
        service.onStartCommand(startIntent(alarm), 0, 1)

        elapse(5_000)

        assertEquals(
            "Still ringing halfway to auto-silence",
            alarm.id, service.ringingState.current.value?.id
        )
    }

    /** Two alarms coalesce into one session; both are one-shots, so dismissing
     *  the session must retire both — not just the one whose notification the
     *  user happened to tap. */
    @Test
    fun dismiss_retiresEveryOneShotInTheSession() {
        val first = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        val second = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        service.onStartCommand(startIntent(first), 0, 1)
        service.onStartCommand(startIntent(second), 0, 2)

        service.onStartCommand(dismissIntent(), 0, 3)

        awaitEnabled(first.id, false, "First alarm of the session must be retired")
        awaitEnabled(second.id, false, "Coalesced second alarm must be retired too")
    }

    @Test
    fun retirementClearsSnoozeToo() {
        val alarm = store(Alarm(hour = 7, minute = 0, repeatDays = 0))
        runBlocking { service.repository.setSnoozeUntil(alarm.id, System.currentTimeMillis() + 60_000) }
        service.onStartCommand(startIntent(alarm), 0, 1)

        service.onStartCommand(dismissIntent(), 0, 2)

        awaitEnabled(alarm.id, false, "Dismissed one-shot must be retired")
        assertEquals("A retired alarm must not keep a pending snooze", 0L, reload(alarm.id).snoozeUntil)
        assertFalse(reload(alarm.id).isEnabled)
    }
}
