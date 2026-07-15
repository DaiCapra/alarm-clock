package com.example.clock.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.clock.ALL_DAYS
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmRepository
import com.example.clock.data.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
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
 * The boot / clock-change restore path. Extracted from the receivers so it can be
 * driven with a plain constructor — the receivers are now just an action check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmReschedulerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var rescheduler: AlarmRescheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(db.alarmDao())
        rescheduler = AlarmRescheduler(repository, AlarmScheduler(context))
    }

    @After
    fun tearDown() = db.close()

    private fun scheduledAlarms() =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms

    private fun notifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun restoresEnabledAlarms() = runTest {
        repository.addAlarm(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS))

        rescheduler.restoreAll()

        assertEquals("An enabled alarm must come back after a reboot", 1, scheduledAlarms().size)
    }

    @Test
    fun skipsDisabledAlarms() = runTest {
        repository.addAlarm(Alarm(hour = 7, minute = 0, isEnabled = false))

        rescheduler.restoreAll()

        assertTrue("A disabled alarm must not be armed", scheduledAlarms().isEmpty())
    }

    /** A dismissed one-shot is disabled, which is precisely what stops a reboot
     *  from resurrecting it. */
    @Test
    fun skipsARetiredOneShot() = runTest {
        val id = repository.addAlarm(Alarm(hour = 7, minute = 0, repeatDays = 0)).toInt()
        repository.disableIfOneShot(id)

        rescheduler.restoreAll()

        assertTrue("A spent one-shot must stay dead across a reboot", scheduledAlarms().isEmpty())
    }

    /**
     * The re-anchoring trap: a snooze is an absolute instant. Restoring it via
     * scheduleSnooze would recompute `now + snoozeMinutes`, pushing it back by a
     * full snooze length on every reboot — an alarm that never arrives.
     */
    @Test
    fun restoresASnoozeAtItsOriginalInstant() = runTest {
        val snoozeUntil = System.currentTimeMillis() + 3 * 60_000L
        val id = repository.addAlarm(
            Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS, snoozeMinutes = 30)
        ).toInt()
        repository.setSnoozeUntil(id, snoozeUntil)

        rescheduler.restoreAll()

        val scheduled = scheduledAlarms().single()
        assertEquals(
            "The snooze must be re-armed at its stored instant, not pushed 30 minutes out",
            snoozeUntil, scheduled.triggerAtTime
        )
    }

    /** The countdown notification dies with the process; the trigger does not.
     *  Without re-posting it, a user who reboots mid-snooze sees nothing pending. */
    @Test
    fun repostsTheSnoozeCountdownNotification() = runTest {
        val id = repository.addAlarm(Alarm(hour = 7, minute = 0, snoozeMinutes = 10)).toInt()
        repository.setSnoozeUntil(id, System.currentTimeMillis() + 5 * 60_000L)

        rescheduler.restoreAll()

        assertEquals("A pending snooze must show its countdown again", 1, notifications().size)
    }

    @Test
    fun doesNotPostACountdownForAnUnsnoozedAlarm() = runTest {
        repository.addAlarm(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS))

        rescheduler.restoreAll()

        assertTrue("Nothing is snoozed, so no countdown", notifications().isEmpty())
    }

    /** An expired snooze is just an ordinary alarm again. */
    @Test
    fun ignoresAnExpiredSnooze() = runTest {
        val id = repository.addAlarm(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS)).toInt()
        repository.setSnoozeUntil(id, System.currentTimeMillis() - 60_000L)

        rescheduler.restoreAll()

        assertNotNull("Still armed for its regular time", scheduledAlarms().singleOrNull())
        assertTrue("A snooze in the past must not show a countdown", notifications().isEmpty())
    }

    @Test
    fun restoresEveryEnabledAlarm() = runTest {
        repository.addAlarm(Alarm(hour = 6, minute = 0, repeatDays = ALL_DAYS))
        repository.addAlarm(Alarm(hour = 7, minute = 0, repeatDays = ALL_DAYS))
        repository.addAlarm(Alarm(hour = 8, minute = 0, isEnabled = false))

        rescheduler.restoreAll()

        assertEquals(2, scheduledAlarms().size)
    }
}
