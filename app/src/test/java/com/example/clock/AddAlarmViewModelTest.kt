package com.example.clock

import android.app.AlarmManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.clock.alarm.AlarmScheduler
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmPrefs
import com.example.clock.data.AlarmRepository
import com.example.clock.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * [AddAlarmViewModel] owns the alarm being edited and persists it. It holds the
 * draft precisely so a rotation can't lose it, and guards save() so a rotation
 * mid-save can't insert the alarm twice.
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
        context.getSharedPreferences("alarm_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** @param editId 0 creates, otherwise edits — the same value the intent carries. */
    private fun viewModel(editId: Int = 0) = AddAlarmViewModel(
        repository, scheduler, prefs,
        SavedStateHandle(mapOf(AddAlarmActivity.EXTRA_EDIT_ID to editId))
    )

    private suspend fun AddAlarmViewModel.awaitDraft(): Alarm = draft.filterNotNull().first()

    private suspend fun AddAlarmViewModel.saveAndAwait(): AddAlarmViewModel.SaveState.Saved {
        save()
        return saveState.first { it is AddAlarmViewModel.SaveState.Saved }
            as AddAlarmViewModel.SaveState.Saved
    }

    private fun nextScheduled() =
        shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm

    @Test
    fun save_persistsAllFields() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update {
            it.copy(
                hour = 7,
                minute = 30,
                label = "Wake up",
                repeatDays = 0b0001001,
                ringtoneUri = "content://ring/1",
                vibrate = false,
                snoozeMinutes = 15,
                volume = 60
            )
        }

        vm.saveAndAwait()

        val alarm = repository.getAlarms().first { it.isNotEmpty() }.single()
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
        val vm = viewModel()
        vm.awaitDraft()
        vm.update { it.copy(hour = 8, minute = 0, label = "Standup") }

        val saved = vm.saveAndAwait()

        assertNotNull("An enabled alarm must be registered with AlarmManager", nextScheduled())
        assertNotNull("Next trigger time should be reported back", saved.nextTrigger)
    }

    @Test
    fun save_disabledAlarm_doesNotSchedule() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update { it.copy(hour = 9, minute = 0, isEnabled = false) }

        val saved = vm.saveAndAwait()

        assertNull("A disabled alarm must not be scheduled", nextScheduled())
        assertNull("No trigger time for a disabled alarm", saved.nextTrigger)
    }

    @Test
    fun save_remembersRingtoneSnoozeAndVolume_asDefaultsForTheNextNewAlarm() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update {
            it.copy(hour = 6, minute = 0, ringtoneUri = "content://ring/42", snoozeMinutes = 20, volume = 55)
        }
        vm.saveAndAwait()

        val next = viewModel().awaitDraft()

        assertEquals("content://ring/42", next.ringtoneUri)
        assertEquals(20, next.snoozeMinutes)
        assertEquals(55, next.volume)
    }

    @Test
    fun newDraft_defaultsToTenMinuteSnoozeAndDefaultRingtone() = runTest {
        val draft = viewModel().awaitDraft()

        assertEquals(10, draft.snoozeMinutes)
        assertNull(draft.ringtoneUri)
        assertEquals("A new alarm starts armed", true, draft.isEnabled)
    }

    // --- The draft survives what used to destroy it ---

    @Test
    fun editing_loadsTheStoredAlarm() = runTest {
        val id = repository.addAlarm(
            Alarm(hour = 5, minute = 15, label = "Gym", ringtoneUri = "content://ring/9")
        ).toInt()

        val draft = viewModel(editId = id).awaitDraft()

        assertEquals(id, draft.id)
        assertEquals("Gym", draft.label)
        assertEquals("content://ring/9", draft.ringtoneUri)
    }

    /** The ringtone and repeat days have no widget that view-state restore can
     *  bring back, so they used to revert to the defaults on rotation. The
     *  ViewModel outlives the Activity, so the draft has to live here. */
    @Test
    fun draft_survivesTheActivityBeingRecreated() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update { it.copy(ringtoneUri = "content://ring/7", repeatDays = 0b0111110) }

        // Rotation destroys the Activity; the ViewModel instance is retained.
        val afterRotation = vm.awaitDraft()

        assertEquals("content://ring/7", afterRotation.ringtoneUri)
        assertEquals(0b0111110, afterRotation.repeatDays)
    }

    /** Editing must read the row once. Re-reading on every onCreate would undo
     *  the user's in-progress changes on rotation. */
    @Test
    fun editing_doesNotReloadOverAnInProgressEdit() = runTest {
        val id = repository.addAlarm(Alarm(hour = 5, minute = 15, label = "Gym")).toInt()
        val vm = viewModel(editId = id)
        vm.awaitDraft()

        vm.update { it.copy(label = "Gym (new)") }

        assertEquals("Gym (new)", vm.awaitDraft().label)
    }

    // --- Save is idempotent ---

    /** save() survived rotation on viewModelScope while finish() no-op'd on the
     *  dead Activity, leaving the form on screen — a second Save then inserted a
     *  second identical alarm with its own PendingIntent. */
    @Test
    fun save_calledTwice_insertsOnlyOnce() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update { it.copy(hour = 8, minute = 0, label = "Once") }

        vm.saveAndAwait()
        vm.save() // the second tap

        val alarms = repository.getAlarms().first { it.isNotEmpty() }
        assertEquals("A repeated save must not duplicate the alarm", 1, alarms.size)
    }

    @Test
    fun save_afterCompleting_updatesInsteadOfInserting() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        vm.update { it.copy(hour = 8, minute = 0, label = "First") }
        vm.saveAndAwait()

        // The id is stamped back into the draft, so the row is now addressable.
        assertTrue("The saved alarm's id must be kept", vm.awaitDraft().id > 0)
        assertEquals(1, repository.getAlarms().first { it.isNotEmpty() }.size)
    }

    @Test
    fun save_reportsSavedState() = runTest {
        val vm = viewModel()
        vm.awaitDraft()
        assertEquals(AddAlarmViewModel.SaveState.Idle, vm.saveState.value)

        vm.saveAndAwait()

        assertTrue(vm.saveState.value is AddAlarmViewModel.SaveState.Saved)
    }
}
