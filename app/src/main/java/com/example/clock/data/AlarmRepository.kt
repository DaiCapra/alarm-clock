package com.example.clock.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao
) {
    fun getAlarms(): Flow<List<Alarm>> = dao.getAll()

    suspend fun getAlarmById(id: Int): Alarm? = dao.getById(id)

    suspend fun getEnabledAlarms(): List<Alarm> = dao.getEnabled()

    suspend fun addAlarm(alarm: Alarm): Long = dao.insert(alarm)

    suspend fun updateAlarm(alarm: Alarm) = dao.update(alarm)

    suspend fun deleteAlarm(alarm: Alarm) = dao.delete(alarm)

    suspend fun setAlarmEnabled(id: Int, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun setSnoozeUntil(id: Int, until: Long) = dao.setSnoozeUntil(id, until)

    suspend fun clearSnooze(id: Int) = dao.setSnoozeUntil(id, 0)
}
