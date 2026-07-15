package com.example.clock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun getAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Int): Alarm?

    @Query("SELECT * FROM alarms WHERE isEnabled = 1")
    suspend fun getEnabled(): List<Alarm>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    /** Toggling an alarm always drops any pending snooze — one write, so the
     *  list never renders a "Snoozed" row whose toggle is already off. */
    @Query("UPDATE alarms SET isEnabled = :enabled, snoozeUntil = 0 WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)

    @Query("UPDATE alarms SET snoozeUntil = :until WHERE id = :id")
    suspend fun setSnoozeUntil(id: Int, until: Long)

    /**
     * Retire a one-shot alarm that has finished ringing.
     *
     * The repeat test lives in SQL against the *stored* row on purpose. A
     * repeating alarm's snooze re-fire arrives carrying `repeatDays = 0` (see
     * [com.example.clock.alarm.AlarmScheduler.scheduleSnooze]), so deciding from
     * the in-flight alarm would permanently disable repeating alarms. `& 127`
     * mirrors `ALL_DAYS`, keeping this in step with `Alarm.repeatMask`.
     */
    @Query("UPDATE alarms SET isEnabled = 0, snoozeUntil = 0 WHERE id = :id AND (repeatDays & 127) = 0")
    suspend fun disableIfOneShot(id: Int)
}
