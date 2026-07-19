package io.github.artmann.clock.alarm

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
 * BootReceiver must act only on BOOT_COMPLETED. Everything it does on a matching
 * action lives in [AlarmRescheduler] and is covered by [AlarmReschedulerTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {

    @Test
    fun ignoresNonBootBroadcasts() {
        val context: Context = ApplicationProvider.getApplicationContext()

        BootReceiver().onReceive(context, Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED))

        val am = context.getSystemService(AlarmManager::class.java)
        assertNull("Nothing should be scheduled for a non-boot action", shadowOf(am).nextScheduledAlarm)
    }
}
