package com.example.clock.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * TimeChangeReceiver must act only on TIMEZONE_CHANGED / TIME_SET. (The happy
 * path — rescheduling enabled alarms — needs a Hilt test harness and is
 * exercised on-device.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimeChangeReceiverTest {

    @Test
    fun ignoresUnrelatedBroadcasts() {
        val context: Context = ApplicationProvider.getApplicationContext()

        TimeChangeReceiver().onReceive(context, Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))

        val am = context.getSystemService(AlarmManager::class.java)
        assertNull("Nothing should be scheduled for an unrelated action", shadowOf(am).nextScheduledAlarm)
    }
}
