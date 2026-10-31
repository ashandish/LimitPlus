package com.example.data.repository

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.data.db.LimitPlusDatabase
import com.example.data.entity.AppLimitConfig
import com.example.data.entity.DailyAppUsage
import com.example.data.entity.ExtensionSessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PromptData(
    val packageName: String,
    val appName: String,
    val baselineMinutes: Int,
    val isRelaunchAfterExhaustion: Boolean,
    val promptId: Long = System.currentTimeMillis()
)

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean,
    val baselineMinutes: Int,
    val todayBaselineUsedSeconds: Long,
    val todayOvertimeUsedSeconds: Long,
    val extensionsCountToday: Int,
    val isBaselineExhausted: Boolean,
    val isCurrentlyActive: Boolean,
    val isUnderActiveExtension: Boolean
)

class AppLimitRepository(
    private val context: Context,
    private val database: LimitPlusDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val configDao = database.appLimitConfigDao()
    private val usageDao = database.dailyUsageDao()
    private val logDao = database.extensionLogDao()

    // Active in-memory session tracking
    private val _activeForegroundPackage = MutableStateFlow<String?>(null)
    val activeForegroundPackage: StateFlow<String?> = _activeForegroundPackage.asStateFlow()

    private val _activeExtensionPackage = MutableStateFlow<String?>(null)
    val activeExtensionPackage: StateFlow<String?> = _activeExtensionPackage.asStateFlow()

    private val _activeExtensionRemainingSeconds = MutableStateFlow<Long?>(null)
    val activeExtensionRemainingSeconds: StateFlow<Long?> = _activeExtensionRemainingSeconds.asStateFlow()

    private val _activeExtensionExpiresAt = MutableStateFlow<Long?>(null)
    val activeExtensionExpiresAt: StateFlow<Long?> = _activeExtensionExpiresAt.asStateFlow()

    private val _currentPrompt = MutableStateFlow<PromptData?>(null)
    val currentPrompt: StateFlow<PromptData?> = _currentPrompt.asStateFlow()

    fun getStartOfTodayTimestamp(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    val allConfigsFlow: Flow<List<AppLimitConfig>> = configDao.getAllFlow()

    private val _currentDateFlow = MutableStateFlow(getTodayDateString())
    val currentDateFlow: StateFlow<String> = _currentDateFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayUsageFlow: Flow<List<DailyAppUsage>> = _currentDateFlow.flatMapLatest { date ->
        usageDao.getAllForDateFlow(date)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val recentLogsFlow: Flow<List<ExtensionSessionLog>> = _currentDateFlow.flatMapLatest {
        val startOfDay = getStartOfTodayTimestamp()
        logDao.getLogsSinceFlow(startOfDay)
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    init {
        scope.launch {
            seedDefaultAndInstalledApps()
        }
    }

    /**
     * Discovers all launchable applications physically installed on the phone.
     * Automatically purges uninstalled packages so phantom presets never appear.
     */
    suspend fun seedDefaultAndInstalledApps() {
        val pm = context.packageManager

        // One-time purge of any phantom/inflated usage from previous flawed system sync
        val prefs = context.getSharedPreferences("limitplus_sync_fix", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("corrupted_usage_purged_v27", false)) {
            usageDao.clearUsageForDate(getTodayDateString())
            _currentPrompt.value = null
            _activeExtensionPackage.value = null
            _activeExtensionRemainingSeconds.value = null
            prefs.edit().putBoolean("corrupted_usage_purged_v27", true).apply()
        }

        // Purge any past day extension logs so the Monitor tab shows only the running day
        try {
            logDao.deleteLogsBefore(getStartOfTodayTimestamp())
        } catch (_: Exception) {}

        // 1. Purge any database records for apps that are not installed on the user's phone
        val existing = configDao.getAllList()
        for (item in existing) {
            if (!isAppActuallyInstalled(pm, item.packageName)) {
                configDao.delete(item.packageName)
            }
        }

        // 2. Discover all installed launcher applications
        val updatedExisting = configDao.getAllList().associateBy { it.packageName }
        val toInsert = mutableListOf<AppLimitConfig>()

        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue // Skip LimitPlus itself
                if (updatedExisting.containsKey(pkg) || toInsert.any { it.packageName == pkg }) continue

                val label = info.loadLabel(pm).toString()
                val shouldMonitorByDefault = when (pkg) {
                    "com.google.android.youtube",
                    "com.facebook.katana",
                    "com.instagram.android" -> true
                    else -> false
                }
                val defaultMinutes = when (pkg) {
                    "com.google.android.youtube", "com.facebook.katana" -> 20
                    "com.instagram.android" -> 15
                    else -> 20
                }

                toInsert.add(
                    AppLimitConfig(
                        packageName = pkg,
                        appName = label,
                        isMonitored = shouldMonitorByDefault,
                        baselineMinutes = defaultMinutes,
                        isCustomOrPreset = false
                    )
                )
            }
        } catch (_: Exception) { }

        if (toInsert.isNotEmpty()) {
            configDao.insertAll(toInsert)
        }
    }

    private fun isAppActuallyInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun setMonitored(packageName: String, isMonitored: Boolean) {
        configDao.setMonitored(packageName, isMonitored)
    }

    suspend fun updateBaseline(packageName: String, baselineMinutes: Int) {
        configDao.updateBaseline(packageName, baselineMinutes.coerceAtLeast(1))
    }

    suspend fun addCustomApp(packageName: String, appName: String, baselineMinutes: Int) {
        configDao.insertOrUpdate(
            AppLimitConfig(
                packageName = packageName.trim(),
                appName = appName.trim(),
                isMonitored = true,
                baselineMinutes = baselineMinutes.coerceAtLeast(1),
                isCustomOrPreset = true
            )
        )
    }

    fun getUsageForDateFlow(date: String = getTodayDateString()): Flow<List<DailyAppUsage>> {
        return usageDao.getAllForDateFlow(date)
    }

    suspend fun getUsageForPackage(packageName: String, date: String = getTodayDateString()): DailyAppUsage {
        val existing = usageDao.getUsage(packageName, date)
        if (existing != null) return existing
        val newUsage = DailyAppUsage(
            id = "${packageName}_$date",
            packageName = packageName,
            date = date
        )
        usageDao.insertOrUpdate(newUsage)
        return newUsage
    }



    /**
     * Core Time Tracking Engine:
     * Evaluates active foreground app and checks whether it has exceeded baseline.
     */
    suspend fun onForegroundAppChanged(newPackage: String?) {
        val previousPackage = _activeForegroundPackage.value
        _activeForegroundPackage.value = newPackage

        val activeExtPkg = _activeExtensionPackage.value
        val extRemainingSec = _activeExtensionRemainingSeconds.value

        // If user navigated away from the app with the prompt, dismiss it immediately
        val currentPromptData = _currentPrompt.value
        if (currentPromptData != null && currentPromptData.packageName != newPackage) {
            dismissPrompt()
        }

        if (newPackage == null || newPackage == context.packageName) {
            return
        }

        val config = configDao.getByPackage(newPackage) ?: return
        if (!config.isMonitored) return

        val today = getTodayDateString()
        val usage = getUsageForPackage(newPackage, today)

        val isUnderValidExtension = activeExtPkg == newPackage &&
                extRemainingSec != null &&
                extRemainingSec > 0L

        if (isUnderValidExtension) {
            // App is allowed under current temporary 5m extension window.
            // Timer is paused while user is away, and only counts down active foreground usage.
            return
        }

        // Check if baseline is exhausted
        if (usage.isBaselineExhausted(config.baselineMinutes)) {
            // App reached limit or user re-opened exhausted app!
            val isRelaunch = (previousPackage != newPackage)
            showPrompt(
                PromptData(
                    packageName = newPackage,
                    appName = config.appName,
                    baselineMinutes = config.baselineMinutes,
                    isRelaunchAfterExhaustion = isRelaunch
                )
            )
        }
    }

    /**
     * Called periodically during monitoring to accumulate elapsed time
     * while the user is actively using an app.
     */
    suspend fun recordUsageTick(packageName: String, elapsedSeconds: Long) {
        val config = configDao.getByPackage(packageName) ?: return
        if (!config.isMonitored) return

        val today = getTodayDateString()
        val usage = getUsageForPackage(packageName, today)
        val baselineMaxSeconds = config.baselineMinutes * 60L

        val currentBaseline = usage.baselineUsedSeconds
        val now = System.currentTimeMillis()

        if (currentBaseline < baselineMaxSeconds) {
            val spaceInBaseline = baselineMaxSeconds - currentBaseline
            val toAddBaseline = elapsedSeconds.coerceAtMost(spaceInBaseline)
            val spillover = elapsedSeconds - toAddBaseline

            val updatedBaseline = currentBaseline + toAddBaseline
            val updatedOvertime = usage.overtimeUsedSeconds + spillover.coerceAtLeast(0L)

            val updatedUsage = usage.copy(
                baselineUsedSeconds = updatedBaseline,
                overtimeUsedSeconds = updatedOvertime,
                lastActiveTimestamp = now
            )
            usageDao.insertOrUpdate(updatedUsage)

            // Did we just hit baseline limit?
            if (updatedBaseline >= baselineMaxSeconds) {
                val activeExt = _activeExtensionPackage.value
                val remSec = _activeExtensionRemainingSeconds.value
                val hasExtension = activeExt == packageName && remSec != null && remSec > 0L
                if (!hasExtension) {
                    showPrompt(
                        PromptData(
                            packageName = packageName,
                            appName = config.appName,
                            baselineMinutes = config.baselineMinutes,
                            isRelaunchAfterExhaustion = false
                        )
                    )
                }
            }
        } else {
            // Baseline already exhausted, active usage counts toward overtime
            val updatedUsage = usage.copy(
                overtimeUsedSeconds = usage.overtimeUsedSeconds + elapsedSeconds,
                lastActiveTimestamp = now
            )
            usageDao.insertOrUpdate(updatedUsage)

            // Check if extension is active
            val activeExt = _activeExtensionPackage.value
            if (activeExt == packageName) {
                val currentRemSec = _activeExtensionRemainingSeconds.value ?: 0L
                val newRemSec = currentRemSec - elapsedSeconds
                if (newRemSec > 0L) {
                    // Extension timer actively counting down while user is in this app
                    _activeExtensionRemainingSeconds.value = newRemSec
                    _activeExtensionExpiresAt.value = now + (newRemSec * 1000L)
                } else {
                    // 5m active usage expired! Prompt reappears offering another 5-minute extension
                    _activeExtensionPackage.value = null
                    _activeExtensionRemainingSeconds.value = null
                    _activeExtensionExpiresAt.value = null
                    showPrompt(
                        PromptData(
                            packageName = packageName,
                            appName = config.appName,
                            baselineMinutes = config.baselineMinutes,
                            isRelaunchAfterExhaustion = false
                        )
                    )
                }
            } else {
                // Exhausted app with no active extension, show prompt
                showPrompt(
                    PromptData(
                        packageName = packageName,
                        appName = config.appName,
                        baselineMinutes = config.baselineMinutes,
                        isRelaunchAfterExhaustion = true
                    )
                )
            }
        }
    }

    /**
     * User taps "Add +5 Minutes":
     * Grants a temporary 5-minute extension window of active usage.
     * Baseline limit is strictly preserved!
     */
    fun grantFiveMinuteExtension(packageName: String, appName: String) {
        val now = System.currentTimeMillis()
        val extensionDurationSec = 5 * 60L // 300 seconds
        _activeExtensionPackage.value = packageName
        _activeExtensionRemainingSeconds.value = extensionDurationSec
        _activeExtensionExpiresAt.value = now + (extensionDurationSec * 1000L)
        _currentPrompt.value = null

        scope.launch(Dispatchers.IO) {
            val today = getTodayDateString()
            usageDao.incrementExtensionCount(packageName, today)
            logDao.insert(
                ExtensionSessionLog(
                    packageName = packageName,
                    appName = appName,
                    timestamp = now,
                    extensionMinutes = 5,
                    status = "GRANTED"
                )
            )
        }
    }

    /**
     * Closes the target application and immediately sends the user to the Home launcher.
     * Prevents the app from minimizing to Picture-in-Picture or a corner window.
     * Temporary extension is terminated immediately.
     */
    fun closeTargetApp(packageName: String) {
        _currentPrompt.value = null
        _activeExtensionPackage.value = null
        _activeExtensionRemainingSeconds.value = null
        _activeExtensionExpiresAt.value = null
        _activeForegroundPackage.value = null

        // 1. Immediately request audio focus to halt playback in YouTube/media apps and stop PiP
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
                scope.launch {
                    delay(1500L)
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                scope.launch {
                    delay(1500L)
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            }
        } catch (_: Exception) {}

        // 2. Bring user directly to the Home screen immediately
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            context.startActivity(homeIntent)
        } catch (_: Exception) {}
        try {
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
        } catch (_: Exception) {}

        // 3. Kill background processes immediately
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        try {
            activityManager?.killBackgroundProcesses(packageName)
        } catch (_: Exception) {}

        // 4. Repeat process termination asynchronously in application scope
        scope.launch(Dispatchers.IO) {
            try {
                logDao.insert(
                    ExtensionSessionLog(
                        packageName = packageName,
                        appName = configDao.getByPackage(packageName)?.appName ?: packageName,
                        status = "TERMINATED_USER_CLOSED"
                    )
                )
                delay(200L)
                activityManager?.killBackgroundProcesses(packageName)
                delay(400L)
                activityManager?.killBackgroundProcesses(packageName)
            } catch (_: Exception) {}
        }
    }

    fun showPrompt(data: PromptData) {
        _currentPrompt.value = data
    }

    fun dismissPrompt() {
        _currentPrompt.value = null
    }

    /**
     * Automated Daily Midnight Reset:
     * Triggers automatically when passing midnight into a new calendar day.
     * All user-configured baseline limits in AppLimitConfig stay strictly intact.
     * Daily usage automatically restarts fresh from 0 for the new day.
     * Any previous day extension or prompt is cleared.
     * Manual resets are forbidden and removed from user control.
     */
    suspend fun onMidnightRollover() {
        val newDate = getTodayDateString()
        val startOfNewDay = getStartOfTodayTimestamp()
        try {
            // Clear any consumed usage in database for the new date so all limits reset to 0 fresh
            usageDao.clearUsageForDate(newDate)
            // Purge previous day extension logs from storage so only the running day's extensions are kept
            logDao.deleteLogsBefore(startOfNewDay)
        } catch (e: Exception) {
            Log.w("AppLimitRepository", "Failed clearing usage for new date $newDate", e)
        }
        _currentDateFlow.value = newDate
        _activeExtensionPackage.value = null
        _activeExtensionRemainingSeconds.value = null
        _activeExtensionExpiresAt.value = null
        _currentPrompt.value = null
        _activeForegroundPackage.value = null
        Log.i("AppLimitRepository", "Automated Midnight rollover completed for $newDate. Daily limits reset.")
    }

    /**
     * Automated/Testing usage reset:
     * Clears consumed daily usage while keeping all configured baseline limits intact.
     */
    suspend fun resetTodayUsage(date: String = getTodayDateString()) {
        usageDao.clearUsageForDate(date)
        try {
            logDao.deleteLogsBefore(getStartOfTodayTimestamp())
        } catch (_: Exception) {}
        _activeExtensionPackage.value = null
        _activeExtensionRemainingSeconds.value = null
        _activeExtensionExpiresAt.value = null
        _currentPrompt.value = null
    }
}
