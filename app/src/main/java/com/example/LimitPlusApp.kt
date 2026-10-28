package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.db.LimitPlusDatabase
import com.example.data.repository.AppLimitRepository

class LimitPlusApp : Application() {

    val database: LimitPlusDatabase by lazy {
        LimitPlusDatabase.getDatabase(this)
    }

    val repository: AppLimitRepository by lazy {
        AppLimitRepository(this, database)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        com.example.service.MidnightResetReceiver.scheduleNextMidnightAlarm(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel for Foreground Monitoring Service
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_SERVICE,
                "Screen Time Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background app limit monitoring status"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(monitorChannel)
        }
    }

    companion object {
        const val CHANNEL_MONITOR_SERVICE = "channel_monitor_service"

        lateinit var instance: LimitPlusApp
            private set
    }
}
