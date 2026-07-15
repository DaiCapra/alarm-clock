package com.example.clock.alarm

import android.content.Context
import android.content.Intent
import android.os.Vibrator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.clock.awaitUntil
import com.example.clock.data.Alarm
import com.example.clock.data.AlarmRepository
import com.example.clock.data.AppDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/** Shared scaffolding for [AlarmService] tests: the Robolectric service
 *  controller lifecycle, a per-test database, and the action-intent builders. */
abstract class AlarmServiceTestBase {

    protected lateinit var context: Context
    protected lateinit var service: AlarmService

    private lateinit var controller: ServiceController<AlarmService>
    private lateinit var db: AppDatabase
    private var destroyed = false

    @Before
    fun setUpService() {
        context = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(AlarmService::class.java).create()
        service = controller.get()
        destroyed = false

        // Swap Hilt's file-backed singleton for a database this test owns and
        // closes. Dismiss and auto-silence now write (retiring one-shots), and
        // an unowned database keeps its invalidation tracker running past the
        // test — those threads then die on "Illegal connection pointer" when
        // Robolectric resets SQLite, failing whichever test runs next.
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service.repository = AlarmRepository(db.alarmDao())
    }

    /** Robolectric's stopSelf only records a flag — it never runs onDestroy. Call
     *  this to play out what the system actually does after the service asks to
     *  stop, which is what makes teardown-time cancellation observable. */
    protected fun destroyService() {
        if (destroyed) return
        destroyed = true
        controller.destroy()
    }

    @After
    fun tearDownService() {
        // Cancels the service scope so background coroutines (pending
        // confirmations) can't outlive the test and crash on Robolectric's
        // between-test SQLite reset.
        destroyService()

        // The snooze persist and one-shot retirement deliberately run on the
        // application scope precisely so destroying the service can't cancel
        // them — so destroy() is no longer enough on its own. Cancelling isn't
        // either: a write already inside SQLite ignores the cancel and would
        // then be writing to a closed database. Let them finish first, then
        // clear the scope so the next test starts from a quiet one.
        val appJob = service.appScope.coroutineContext[Job]
        runBlocking {
            withTimeoutOrNull(DRAIN_TIMEOUT_MS) {
                appJob?.children?.toList()?.forEach { it.join() }
            }
        }
        appJob?.cancelChildren()
        db.close()
    }

    protected fun startIntent(alarm: Alarm) =
        Intent(context, AlarmService::class.java).putAlarm(alarm)

    protected fun snoozeIntent(alarm: Alarm) =
        Intent(context, AlarmService::class.java)
            .apply { action = AlarmService.ACTION_SNOOZE }
            .putAlarm(alarm)

    protected fun dismissIntent() =
        Intent(context, AlarmService::class.java).apply { action = AlarmService.ACTION_DISMISS }

    protected fun refreshIntent() =
        Intent(context, AlarmService::class.java).apply { action = AlarmService.ACTION_REFRESH }

    private companion object {
        const val DRAIN_TIMEOUT_MS = 5_000L
    }

    protected fun shadowVibrator() = shadowOf(context.getSystemService(Vibrator::class.java))

    /** Snooze persists snoozeUntil on a background coroutine, then stopSelf().
     *  Await that stop so the coroutine can't outlive the test. */
    protected fun sendSnoozeAndAwaitStop(alarm: Alarm, startId: Int) {
        service.onStartCommand(snoozeIntent(alarm), 0, startId)
        awaitUntil(message = "Service did not stop itself after snooze") {
            shadowOf(service).isStoppedBySelf
        }
    }
}
