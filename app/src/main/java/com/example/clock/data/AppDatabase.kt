package com.example.clock.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Schema changes need three things in the same commit: a bumped [VERSION], the
 * regenerated `app/schemas/…/<version>.json`, and a migration in
 * `DatabaseModule.MIGRATIONS`. There is no destructive fallback — a missing
 * migration fails at open instead of deleting the user's alarms — and
 * `MigrationTest` fails the build if any of the three is forgotten.
 */
@Database(entities = [Alarm::class], version = AppDatabase.VERSION, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        /** Kept as a constant so tests can assert against it; Room's @Database
         *  annotation is not visible to runtime reflection. */
        const val VERSION = 1
    }
}
