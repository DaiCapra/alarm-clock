package com.example.clock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the Room [AlarmDao] backing the alarm list.
 *
 * Uses an in-memory database so each test starts from an empty table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alarmDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_thenGetAll_returnsInsertedAlarm() = runTest {
        val id = dao.insert(Alarm(hour = 6, minute = 15, label = "Gym"))

        val all = dao.getAll().first()

        assertEquals(1, all.size)
        val stored = all.first()
        assertEquals(id.toInt(), stored.id)
        assertEquals(6, stored.hour)
        assertEquals(15, stored.minute)
        assertEquals("Gym", stored.label)
        assertTrue(stored.isEnabled)
    }

    @Test
    fun insertMultiple_getAll_ordersByHourThenMinute() = runTest {
        dao.insert(Alarm(hour = 9, minute = 0, label = "Late"))
        dao.insert(Alarm(hour = 6, minute = 45, label = "Early"))
        dao.insert(Alarm(hour = 6, minute = 30, label = "Earlier"))

        val all = dao.getAll().first()

        assertEquals(listOf("Earlier", "Early", "Late"), all.map { it.label })
    }

    @Test
    fun getById_returnsMatchingAlarm_orNull() = runTest {
        val id = dao.insert(Alarm(hour = 8, minute = 0, label = "Work"))

        val found = dao.getById(id.toInt())
        assertEquals("Work", found?.label)

        assertNull(dao.getById(9999))
    }

    @Test
    fun update_changesStoredValues() = runTest {
        val id = dao.insert(Alarm(hour = 7, minute = 0, label = "Old"))
        val stored = dao.getById(id.toInt())!!

        dao.update(stored.copy(hour = 10, minute = 5, label = "New"))

        val updated = dao.getById(id.toInt())!!
        assertEquals(10, updated.hour)
        assertEquals(5, updated.minute)
        assertEquals("New", updated.label)
    }

    @Test
    fun setEnabled_togglesFlag() = runTest {
        val id = dao.insert(Alarm(hour = 7, minute = 0, isEnabled = true))

        dao.setEnabled(id.toInt(), false)

        assertFalse(dao.getById(id.toInt())!!.isEnabled)
    }

    @Test
    fun delete_removesAlarm() = runTest {
        val id = dao.insert(Alarm(hour = 7, minute = 0, label = "Temp"))
        val stored = dao.getById(id.toInt())!!

        dao.delete(stored)

        assertTrue(dao.getAll().first().isEmpty())
        assertNull(dao.getById(id.toInt()))
    }
}
