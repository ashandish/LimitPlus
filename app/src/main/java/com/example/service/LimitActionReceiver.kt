package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.LimitPlusApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LimitActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val repository = LimitPlusApp.instance.repository

        // Dismiss alert notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ScreenTimeMonitorService.ALERT_NOTIFICATION_ID)

        when (intent.action) {
            ACTION_ADD_FIVE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.grantFiveMinuteExtension(packageName, appName)
                }
            }
            ACTION_CLOSE_APP -> {
                CoroutineScope(Dispatchers.IO).launch {
                    repository.closeTargetApp(packageName)
                }
            }
        }
    }

    companion object {
        const val ACTION_ADD_FIVE = "com.example.limitplus.ACTION_ADD_FIVE"
        const val ACTION_CLOSE_APP = "com.example.limitplus.ACTION_CLOSE_APP"
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_APP_NAME = "extra_app_name"
    }
}
