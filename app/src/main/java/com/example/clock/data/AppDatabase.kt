package com.example.clock.data

import androidx.room.Database
import androidx.room.RoomDatabase

// No published releases yet, so schema changes just recreate the DB
// (see fallbackToDestructiveMigration in DatabaseModule) — no migrations needed.
@Database(entities = [Alarm::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
