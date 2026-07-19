package io.github.artmann.clock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.artmann.clock.alarm.AppForegroundState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                AppForegroundState.isForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) AppForegroundState.isForeground = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
