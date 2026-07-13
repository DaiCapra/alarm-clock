package com.example.clock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AlarmDao.getEnabled] backs the boot-time rescheduling: only enabled alarms
 * must come back after a reboot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmDaoEnabledTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alarmDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun getEnabled_returnsOnlyEnabledAlarms() = runTest {
        dao.insert(Alarm(hour = 6, minute = 0, label = "on", isEnabled = true))
        dao.insert(Alarm(hour = 7, minute = 0, label = "off", isEnabled = false))
        dao.insert(Alarm(hour = 8, minute = 0, label = "on2", isEnabled = true))

        val enabled = dao.getEnabled()

        assertEquals(setOf("on", "on2"), enabled.map { it.label }.toSet())
    }
}
