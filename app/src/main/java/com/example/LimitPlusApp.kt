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

            // High-priority Channel for +5 Minute Prompts & Limit Alerts
            val alertChannel = NotificationChannel(
                CHANNEL_LIMIT_ALERTS,
                "App Limit & Extension Prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when an app reaches its daily baseline and needs +5m extension"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val CHANNEL_MONITOR_SERVICE = "channel_monitor_service"
        const val CHANNEL_LIMIT_ALERTS = "channel_limit_alerts"

        lateinit var instance: LimitPlusApp
            private set
    }
}
