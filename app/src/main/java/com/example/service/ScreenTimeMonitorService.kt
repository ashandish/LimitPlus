package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.LimitPlusApp
import com.example.MainActivity
import com.example.R
import com.example.data.repository.PromptData
import com.example.ui.overlay.OverlayDialogActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenTimeMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null

    // Track active foreground state reliably across event gaps
    private var activeForegroundPackage: String? = null
    private var lastCheckTime: Long = System.currentTimeMillis()
    private var lastEventQueryTime: Long = System.currentTimeMillis() - 30_000L
    private val launcherPackages = mutableSetOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cacheLauncherPackages()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Active: Monitoring daily baselines"))
        observePrompts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (monitorJob == null || monitorJob?.isActive != true) {
            startMonitoringLoop()
        }
        return START_STICKY
    }

    private fun cacheLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                launcherPackages.add(info.activityInfo.packageName)
            }
        } catch (_: Exception) {}
    }

    private fun isLauncherOrSystem(pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.systemui" || pkg == "com.google.android.apps.nexuslauncher") {
            return true
        }
        if (launcherPackages.contains(pkg)) {
            return true
        }
        cacheLauncherPackages()
        return launcherPackages.contains(pkg)
    }

    private fun observePrompts() {
        val repository = LimitPlusApp.instance.repository
        serviceScope.launch {
            repository.currentPrompt.collect { prompt ->
                if (prompt != null) {
                    dispatchPromptToUser(prompt)
                }
            }
        }
    }

    private fun dispatchPromptToUser(prompt: PromptData) {
        // 1. If Overlay permission is available, trigger the floating system overlay window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
                putExtra(FloatingOverlayService.EXTRA_PACKAGE_NAME, prompt.packageName)
                putExtra(FloatingOverlayService.EXTRA_APP_NAME, prompt.appName)
                putExtra(FloatingOverlayService.EXTRA_BASELINE_MINUTES, prompt.baselineMinutes)
                putExtra(FloatingOverlayService.EXTRA_IS_RELAUNCH, prompt.isRelaunchAfterExhaustion)
            }
            startService(overlayIntent)
        } else {
            // 2. Translucent activity prompt fallback
            val dialogIntent = Intent(this, OverlayDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(OverlayDialogActivity.EXTRA_PACKAGE_NAME, prompt.packageName)
                putExtra(OverlayDialogActivity.EXTRA_APP_NAME, prompt.appName)
                putExtra(OverlayDialogActivity.EXTRA_BASELINE_MINUTES, prompt.baselineMinutes)
                putExtra(OverlayDialogActivity.EXTRA_IS_RELAUNCH, prompt.isRelaunchAfterExhaustion)
            }
            startActivity(dialogIntent)
        }

        // 3. High-Priority Heads-up Notification with Fullscreen & Quick Actions
        sendPromptNotification(prompt)
    }

    private fun sendPromptNotification(prompt: PromptData) {
        val addFiveIntent = Intent(this, LimitActionReceiver::class.java).apply {
            action = LimitActionReceiver.ACTION_ADD_FIVE
            putExtra(LimitActionReceiver.EXTRA_PACKAGE, prompt.packageName)
            putExtra(LimitActionReceiver.EXTRA_APP_NAME, prompt.appName)
        }
        val addFivePending = PendingIntent.getBroadcast(
            this,
            1001,
            addFiveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeAppIntent = Intent(this, LimitActionReceiver::class.java).apply {
            action = LimitActionReceiver.ACTION_CLOSE_APP
            putExtra(LimitActionReceiver.EXTRA_PACKAGE, prompt.packageName)
        }
        val closeAppPending = PendingIntent.getBroadcast(
            this,
            1002,
            closeAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dialogIntent = Intent(this, OverlayDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OverlayDialogActivity.EXTRA_PACKAGE_NAME, prompt.packageName)
            putExtra(OverlayDialogActivity.EXTRA_APP_NAME, prompt.appName)
            putExtra(OverlayDialogActivity.EXTRA_BASELINE_MINUTES, prompt.baselineMinutes)
            putExtra(OverlayDialogActivity.EXTRA_IS_RELAUNCH, prompt.isRelaunchAfterExhaustion)
        }
        val fullScreenPending = PendingIntent.getActivity(
            this,
            1003,
            dialogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (prompt.isRelaunchAfterExhaustion) {
            "${prompt.appName} Limit Expired"
        } else {
            "${prompt.appName} Daily Limit Reached"
        }

        val message = if (prompt.isRelaunchAfterExhaustion) {
            "${prompt.appName} daily limit has expired. Open for 5 minutes?"
        } else {
            "You have reached your ${prompt.baselineMinutes}-minute daily limit for ${prompt.appName}. Add 5 more minutes?"
        }

        val notification = NotificationCompat.Builder(this, LimitPlusApp.CHANNEL_LIMIT_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPending, true)
            .setAutoCancel(true)
            .addAction(0, "+5 Minutes", addFivePending)
            .addAction(0, "Close App", closeAppPending)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val repository = LimitPlusApp.instance.repository
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            var syncCounter = 0

            while (isActive) {
                try {
                    val currentForeground = detectForegroundApp(usageStatsManager)
                    val now = System.currentTimeMillis()
                    val elapsedSeconds = ((now - lastCheckTime) / 1000L).coerceIn(1L, 10L)
                    lastCheckTime = now

                    if (currentForeground != activeForegroundPackage) {
                        activeForegroundPackage = currentForeground
                        repository.onForegroundAppChanged(currentForeground)
                        updateForegroundNotification(currentForeground)
                    } else if (currentForeground != null && currentForeground != packageName) {
                        repository.recordUsageTick(currentForeground, elapsedSeconds)
                    }

                    // Periodically sync authoritative system usage stats from Android OS
                    syncCounter++
                    if (syncCounter >= 3) {
                        syncCounter = 0
                        repository.syncWithSystemUsageStats(usageStatsManager)
                    }
                } catch (_: Exception) { }

                delay(1000L) // 1-second check interval for responsive tracking
            }
        }
    }

    /**
     * Dual-Engine Foreground App Detection:
     * 1. Inspects recent UsageEvents for ACTIVITY_RESUMED
     * 2. Inspects UsageStats for recent lastTimeUsed
     * 3. Maintains active package while inside an app (instead of dropping to null)
     * 4. Excludes Home launcher, System UI, and LimitPlus itself
     */
    private fun detectForegroundApp(usageStatsManager: UsageStatsManager?): String? {
        if (usageStatsManager == null) return null
        val now = System.currentTimeMillis()

        // 1. Check UsageEvents stream
        try {
            val queryStart = (now - 15_000L).coerceAtMost(lastEventQueryTime)
            val events = usageStatsManager.queryEvents(queryStart, now)
            val event = UsageEvents.Event()
            var latestResumedPkg: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestResumedPkg = event.packageName
                }
            }
            lastEventQueryTime = now

            if (latestResumedPkg != null) {
                activeForegroundPackage = latestResumedPkg
            }
        } catch (_: Exception) {}

        // 2. Validate with UsageStats INTERVAL_BEST
        try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 10_000L,
                now
            )
            if (!statsList.isNullOrEmpty()) {
                val mostRecent = statsList
                    .filter { it.packageName != packageName }
                    .maxByOrNull { it.lastTimeUsed }

                if (mostRecent != null && (now - mostRecent.lastTimeUsed) < 4_000L) {
                    activeForegroundPackage = mostRecent.packageName
                }
            }
        } catch (_: Exception) {}

        val current = activeForegroundPackage
        if (current == null || current == packageName || isLauncherOrSystem(current)) {
            return null
        }
        return current
    }

    private fun updateForegroundNotification(activePkg: String?) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = if (activePkg != null) {
                val appName = try {
                    val pm = packageManager
                    val info = pm.getApplicationInfo(activePkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    activePkg
                }
                "Active: Monitoring $appName"
            } else {
                "Active: Protecting daily baselines"
            }
            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(text))
        } catch (_: Exception) {}
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, LimitPlusApp.CHANNEL_MONITOR_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LimitPlus Controller")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4001
        const val ALERT_NOTIFICATION_ID = 4002

        fun startService(context: Context) {
            val intent = Intent(context, ScreenTimeMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
