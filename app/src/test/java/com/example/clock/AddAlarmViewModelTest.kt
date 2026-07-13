package com.example.clock

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmPrefs
import com.example.clock.data.AppDatabase
import com.example.clock.data.AlarmRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests [AddAlarmViewModel.save]: it must persist the alarm through the
 * repository and register an enabled alarm with the scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddAlarmViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var prefs: AlarmPrefs
    private lateinit var viewModel: AddAlarmViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(db.alarmDao())
        scheduler = AlarmScheduler(context)
        prefs = AlarmPrefs(context)
        viewModel = AddAlarmViewModel(repository, scheduler, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Drive save() to completion: onSaved fires only after the DB write and
     *  scheduling finish, so awaiting it removes any cross-thread race. */
    private suspend fun saveAndAwait(alarm: Alarm): Long? {
        val result = CompletableDeferred<Long?>()
        viewModel.save(alarm) { result.complete(it) }
        return result.await()
    }

    @Test
    fun save_persistsAllFields() = runTest {
        saveAndAwait(
            Alarm(
                hour = 7,
                minute = 30,
                label = "Wake up",
                repeatDays = 0b0001001,
                ringtoneUri = "content://ring/1",
                vibrate = false,
                snoozeMinutes = 15,
                volume = 60
            )
        )

        val alarms = repository.getAlarms().first { it.isNotEmpty() }
        assertEquals(1, alarms.size)
        val alarm = alarms.first()
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
        assertEquals("Wake up", alarm.label)
        assertEquals(0b0001001, alarm.repeatDays)
        assertEquals("content://ring/1", alarm.ringtoneUri)
        assertEquals(false, alarm.vibrate)
        assertEquals(15, alarm.snoozeMinutes)
        assertEquals(60, alarm.volume)
    }

    @Test
    fun save_enabledAlarm_schedulesWithAlarmManager() = runTest {
        val reportedTrigger = saveAndAwait(Alarm(hour = 8, minute = 0, label = "Standup"))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertNotNull(
            "Saving an enabled alarm should register it with AlarmManager",
            shadowOf(alarmManager).nextScheduledAlarm
        )
        assertNotNull("Next trigger time should be reported back", reportedTrigger)
    }

    @Test
    fun save_remembersRingtoneAndSnooze_asDefaultsForNewAlarm() = runTest {
        saveAndAwait(
            Alarm(
                hour = 6,
                minute = 0,
                ringtoneUri = "content://ring/42",
                snoozeMinutes = 20,
                volume = 55
            )
        )

        val draft = viewModel.newAlarmDraft()
        assertEquals("content://ring/42", draft.ringtoneUri)
        assertEquals(20, draft.snoozeMinutes)
        assertEquals(55, draft.volume)
        assertEquals("content://ring/42", prefs.defaultRingtoneUri)
        assertEquals(20, prefs.defaultSnoozeMinutes)
        assertEquals(55, prefs.defaultVolume)
    }

    @Test
    fun newAlarmDraft_defaultsToTenMinuteSnooze_whenNothingSaved() {
        assertEquals(10, viewModel.newAlarmDraft().snoozeMinutes)
        assertNull(viewModel.newAlarmDraft().ringtoneUri)
    }

    @Test
    fun save_disabledAlarm_doesNotSchedule() = runTest {
        val reportedTrigger = saveAndAwait(Alarm(hour = 9, minute = 0, isEnabled = false))

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertNull(
            "Disabled alarm should not be scheduled",
            shadowOf(alarmManager).nextScheduledAlarm
        )
        assertNull("No trigger time for a disabled alarm", reportedTrigger)
    }
}
