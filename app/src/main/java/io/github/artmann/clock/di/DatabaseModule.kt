package io.github.artmann.clock.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import io.github.artmann.clock.data.AlarmDao
import io.github.artmann.clock.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No destructive fallback: a schema change without a matching migration must
     * fail loudly at open rather than silently deleting every alarm the user set.
     *
     * To change the schema: bump `AppDatabase.version`, add a [Migration] to
     * [MIGRATIONS], and commit the regenerated `app/schemas/…/<version>.json`.
     * `MigrationTest` walks the chain against those files.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "clock.db")
            .addMigrations(*MIGRATIONS)
            .build()

    /** Ordered v(n) -> v(n+1) steps. Empty while the schema is still at v1. */
    val MIGRATIONS: Array<Migration> = emptyArray()

    @Provides
    fun provideAlarmDao(db: AppDatabase): AlarmDao = db.alarmDao()
}
