package com.example.clock.alarm

import android.content.Context
import android.content.Intent
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.example.clock.awaitUntil
import com.example.clock.data.Alarm
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

/** Shared scaffolding for [AlarmService] tests: the Robolectric service
 *  controller lifecycle and the action-intent builders. */
abstract class AlarmServiceTestBase {

    protected lateinit var context: Context
    protected lateinit var service: AlarmService

    private lateinit var controller: ServiceController<AlarmService>

    @Before
    fun setUpService() {
        context = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(AlarmService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDownService() {
        // Cancels the service scope so background coroutines (snooze persist,
        // pending confirmations) can't outlive the test and crash on
        // Robolectric's between-test SQLite reset.
        controller.destroy()
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
