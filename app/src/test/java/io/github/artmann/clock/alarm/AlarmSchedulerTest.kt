package io.github.artmann.clock.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.artmann.clock.ALL_DAYS
import io.github.artmann.clock.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests [AlarmScheduler]: normal scheduling and snooze re-scheduling both
 * register a trigger with the system AlarmManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = AlarmScheduler(context)
    }

    @Test
    fun schedule_registersAlarm() {
        scheduler.schedule(Alarm(id = 1, hour = 7, minute = 30, label = "Wake"))

        val am = context.getSystemService(AlarmManager::class.java)
        assertNotNull(shadowOf(am).nextScheduledAlarm)
    }

    @Test
    fun scheduleSnooze_registersAlarmInFuture() {
        val before = System.currentTimeMillis()
        scheduler.scheduleSnooze(Alarm(id = 1, hour = 7, minute = 30, label = "Wake"), minutes = 5)

        val am = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(am).nextScheduledAlarm
        assertNotNull(scheduled)
        // Fires ~5 minutes out, definitely after "now".
        assertTrue(scheduled!!.triggerAtTime > before)
    }

    @Test
    fun scheduleSnooze_postsCountdownNotification() {
        scheduler.scheduleSnooze(Alarm(id = 1, hour = 7, minute = 30), minutes = 5)

        val nm = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }

    @Test
    fun cancel_unregistersAlarmAndDropsSnoozeNotification() {
        val alarm = Alarm(id = 1, hour = 7, minute = 30)
        scheduler.scheduleSnooze(alarm, minutes = 5)

        scheduler.cancel(alarm)

        val am = context.getSystemService(AlarmManager::class.java)
        assertNull("Cancel must unregister the trigger", shadowOf(am).nextScheduledAlarm)
        val nm = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(nm).allNotifications.size)
    }

    /** Snoozing reuses the alarm's request code, so the snooze trigger replaces
     *  the repeat occurrence armed when the alarm fired — leaving the snooze as
     *  the only pending trigger. AlarmReceiver compensates by re-arming from the
     *  stored alarm when the snooze fires; if that ever regresses, a snoozed
     *  repeating alarm silently stops after its snooze. */
    @Test
    fun scheduleSnooze_replacesThePendingRepeatOccurrence() {
        val repeating = Alarm(id = 1, hour = 7, minute = 30, repeatDays = ALL_DAYS)
        scheduler.schedule(repeating)

        scheduler.scheduleSnooze(repeating, minutes = 5)

        val am = context.getSystemService(AlarmManager::class.java)
        val pending = shadowOf(am).scheduledAlarms
        assertEquals("Snooze shares the alarm's request code, so it replaces", 1, pending.size)
        assertTrue(
            "The surviving trigger is the snooze, not the next repeat occurrence",
            pending.single().triggerAtTime < System.currentTimeMillis() + 6 * 60_000L
        )
    }

    @Test
    fun schedule_withoutExactAlarmPermission_fallsBackToInexact() {
        val am = context.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule(Alarm(id = 1, hour = 7, minute = 30))

        assertNotNull(
            "Alarm must still be registered via the inexact fallback",
            shadowOf(am).nextScheduledAlarm
        )
    }
}
