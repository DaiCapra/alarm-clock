package com.example.clock.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmRepository
import com.example.clock.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * End-to-end test: add an alarm, schedule it, and let the alarm fire.
 *
 * Uses Robolectric so the AlarmManager can be triggered deterministically instead
 * of waiting for wall-clock time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddAndTriggerAlarmTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(db.alarmDao())
        scheduler = AlarmScheduler(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addAlarm_thenFires_startsAlarmService() = runTest {
        // 1. Add the alarm.
        val newId = repository.addAlarm(Alarm(hour = 7, minute = 30, label = "Wake up"))

        // It is persisted and returned via the repository.
        val stored = repository.getAlarms().first()
        assertEquals(1, stored.size)
        val alarm = stored.first()
        assertEquals("Wake up", alarm.label)
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)

        // 2. Schedule the alarm with the real scheduler.
        scheduler.schedule(alarm.copy(id = newId.toInt()))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(alarmManager).nextScheduledAlarm
        assertNotNull("Alarm should be registered with AlarmManager", scheduled)

        // 3. Let the alarm trigger by firing its pending intent.
        scheduled!!.operation!!.send()
        // Deliver the queued broadcast on the (paused) main looper.
        shadowOf(android.os.Looper.getMainLooper()).idle()
        shadowOf(context as Application).run {
            // AlarmReceiver forwards to AlarmService via startForegroundService.
            val serviceIntent = nextStartedService
            assertNotNull("AlarmService should be started when the alarm fires", serviceIntent)
            assertTrue(
                serviceIntent.component?.className == AlarmService::class.java.name
            )
            assertEquals(
                newId.toInt(),
                serviceIntent.getIntExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
            )
            assertEquals(
                "Wake up",
                serviceIntent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL)
            )
        }

        // AlarmReceiver.onReceive kicked off a goAsync coroutine (clearSnooze on
        // the Hilt-provided DB). Let it finish before Robolectric resets SQLite
        // between tests, or its crash leaks into whichever test runs next.
        Thread.sleep(500)
    }
}
