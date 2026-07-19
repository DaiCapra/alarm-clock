package io.github.artmann.clock

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.artmann.clock.alarm.AlarmScheduler
import io.github.artmann.clock.alarm.AlarmService
import io.github.artmann.clock.alarm.AlarmTiming
import io.github.artmann.clock.alarm.RingingState
import io.github.artmann.clock.data.Alarm
import io.github.artmann.clock.data.AppDatabase
import io.github.artmann.clock.data.AlarmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * Main-screen behavior: enabling/disabling alarms, deletion, and the
 * next-alarm countdown.
 *
 * setEnabled/deleteAlarm run on viewModelScope and hop to Room's executor, so
 * the tests await the observable outcome (DB row, AlarmManager registration,
 * started service) instead of asserting immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AlarmRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var ringingState: RingingState
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AlarmRepository(db.alarmDao())
        scheduler = AlarmScheduler(context)
        ringingState = RingingState()
        viewModel = MainViewModel(context, repository, scheduler, ringingState)
    }

    @After
    fun tearDown() {
        // Stop the alarms stateIn subscription (WhileSubscribed keeps the Room
        // flow alive past the test) before closing the DB, or its crash leaks
        // into the next test as an uncaught background exception.
        viewModel.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun insert(alarm: Alarm): Alarm =
        alarm.copy(id = repository.addAlarm(alarm).toInt())

    private fun storedAlarm(id: Int): Alarm? = runBlocking { db.alarmDao().getById(id) }

    @Test
    fun setEnabled_true_schedulesAndPersists() = runTest {
        val alarm = insert(Alarm(hour = 7, minute = 0, isEnabled = false))
        val am = context.getSystemService(AlarmManager::class.java)

        viewModel.setEnabled(alarm, true)

        awaitUntil { shadowOf(am).nextScheduledAlarm != null }
        awaitUntil { storedAlarm(alarm.id)?.isEnabled == true }
    }

    @Test
    fun setEnabled_true_emitsCountdownMessage() = runTest {
        val alarm = insert(Alarm(hour = 7, minute = 0, isEnabled = false))
        val messages = Collections.synchronizedList(mutableListOf<String>())
        val job = launch(Dispatchers.Unconfined) {
            viewModel.scheduledMessage.collect { messages.add(it) }
        }

        viewModel.setEnabled(alarm, true)

        awaitUntil { messages.isNotEmpty() }
        assertTrue(
            "Message should be a countdown toast, was '${messages.first()}'",
            messages.first().startsWith("Alarm will trigger in ")
        )
        job.cancel()
    }

    @Test
    fun setEnabled_false_cancelsAndClearsSnooze() = runTest {
        val alarm = insert(
            Alarm(hour = 7, minute = 0, snoozeUntil = System.currentTimeMillis() + 60_000)
        )
        scheduler.schedule(alarm)

        viewModel.setEnabled(alarm, false)

        awaitUntil {
            storedAlarm(alarm.id)?.let { !it.isEnabled && it.snoozeUntil == 0L } == true
        }
    }

    /**
     * The list row's toggle listener captures the Alarm from its last bind, so it
     * can hold a snooze that has since been cleared. Arming from that stale value
     * would schedule the snooze instant while the database says there is no
     * snooze — the alarm fires at a time the countdown never showed.
     */
    @Test
    fun setEnabled_true_ignoresAStaleSnoozeOnTheCapturedAlarm() = runTest {
        val alarm = insert(Alarm(hour = 7, minute = 0, isEnabled = false))
        val stale = alarm.copy(snoozeUntil = System.currentTimeMillis() + 3 * 60_000)
        val am = context.getSystemService(AlarmManager::class.java)

        viewModel.setEnabled(stale, true)

        // scheduledAlarms, not nextScheduledAlarm — the latter consumes.
        awaitUntil { shadowOf(am).scheduledAlarms.isNotEmpty() }
        val armed = shadowOf(am).scheduledAlarms.last().triggerAtTime
        val expected = AlarmTiming.nextTrigger(alarm.copy(isEnabled = true, snoozeUntil = 0))
        assertEquals("Must arm the alarm's real time, not the stale snooze", expected, armed)
        awaitUntil { storedAlarm(alarm.id)?.snoozeUntil == 0L }
    }

    @Test
    fun setEnabled_false_whileRinging_dismissesTheService() = runTest {
        val alarm = insert(Alarm(hour = 7, minute = 0))
        ringingState.setRinging(alarm)

        viewModel.setEnabled(alarm, false)

        var started: Intent? = null
        awaitUntil {
            started = shadowOf(context as Application).nextStartedService
            started != null
        }
        assertEquals(AlarmService::class.java.name, started!!.component?.className)
        assertEquals(AlarmService.ACTION_DISMISS, started!!.action)
    }

    @Test
    fun deleteAlarm_removesFromDatabase() = runTest {
        val alarm = insert(Alarm(hour = 7, minute = 0))

        viewModel.deleteAlarm(alarm)

        awaitUntil { storedAlarm(alarm.id) == null }
        assertTrue(repository.getAlarms().first().isEmpty())
    }

    @Test
    fun nextAlarm_isNull_whenNoEnabledAlarms() = runTest {
        insert(Alarm(hour = 7, minute = 0, isEnabled = false))
        viewModel.alarms.first { it.isNotEmpty() } // wait for the DB emission

        assertNull(viewModel.nextAlarm.first())
    }

    @Test
    fun nextAlarm_countsDownToSoonestEnabledAlarm() = runTest {
        insert(Alarm(hour = 7, minute = 0, isEnabled = false))
        insert(Alarm(hour = 8, minute = 0))
        viewModel.alarms.first { it.size == 2 }

        val next = viewModel.nextAlarm.first()

        assertNotNull(next)
        assertTrue(
            "Countdown should be H:MM:SS, was '${next!!.countdown}'",
            next.countdown.matches(Regex("""\d+:\d{2}:\d{2}"""))
        )
        assertFalse(next.isSnooze)
    }

    @Test
    fun nextAlarm_flagsPendingSnooze() = runTest {
        insert(
            Alarm(hour = 7, minute = 0, snoozeUntil = System.currentTimeMillis() + 2 * 60_000)
        )
        viewModel.alarms.first { it.isNotEmpty() }

        val next = viewModel.nextAlarm.first()

        assertNotNull(next)
        assertTrue("Soonest trigger is a snooze re-fire", next!!.isSnooze)
    }
}
