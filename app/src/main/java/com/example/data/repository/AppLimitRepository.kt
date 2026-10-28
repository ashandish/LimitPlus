package com.example.data.repository

import android.app.ActivityManager
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
    val isRelaunchAfterExhaustion: Boolean
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

    private val _activeExtensionExpiresAt = MutableStateFlow<Long?>(null)
    val activeExtensionExpiresAt: StateFlow<Long?> = _activeExtensionExpiresAt.asStateFlow()

    private val _currentPrompt = MutableStateFlow<PromptData?>(null)
    val currentPrompt: StateFlow<PromptData?> = _currentPrompt.asStateFlow()

    val allConfigsFlow: Flow<List<AppLimitConfig>> = configDao.getAllFlow()
    val recentLogsFlow: Flow<List<ExtensionSessionLog>> = logDao.getRecentLogsFlow()

    private val _currentDateFlow = MutableStateFlow(getTodayDateString())
    val currentDateFlow: StateFlow<String> = _currentDateFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayUsageFlow: Flow<List<DailyAppUsage>> = _currentDateFlow.flatMapLatest { date ->
        usageDao.getAllForDateFlow(date)
    }

    @Volatile
    private var suppressedPackage: String? = null
    @Volatile
    private var suppressedUntil: Long = 0L

    fun suppressPackage(packageName: String, durationMs: Long = 6000L) {
        suppressedPackage = packageName
        suppressedUntil = System.currentTimeMillis() + durationMs
    }

    fun isPackageSuppressed(packageName: String): Boolean {
        return suppressedPackage == packageName && System.currentTimeMillis() < suppressedUntil
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
     * Synchronizes authoritative daily screen time metrics from Android's UsageStatsManager.
     * This ensures that any time spent in YouTube, Facebook, Instagram, etc. is immediately
     * reflected in LimitPlus, even before or between polling ticks.
     */
    suspend fun syncWithSystemUsageStats(usageStatsManager: UsageStatsManager?) {
        if (usageStatsManager == null) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        try {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                now
            ) ?: return

            if (statsList.isEmpty()) return

            val statsMap = statsList.associateBy { it.packageName }
            val configs = configDao.getAllList().filter { it.isMonitored }
            val today = getTodayDateString()

            for (config in configs) {
                if (isPackageSuppressed(config.packageName)) continue
                val stats = statsMap[config.packageName] ?: continue
                val systemTotalSeconds = stats.totalTimeInForeground / 1000L
                if (systemTotalSeconds <= 0L) continue

                val existing = getUsageForPackage(config.packageName, today)
                val baselineMaxSeconds = config.baselineMinutes * 60L
                val calculatedBaseline = systemTotalSeconds.coerceAtMost(baselineMaxSeconds)
                val calculatedOvertime = (systemTotalSeconds - baselineMaxSeconds).coerceAtLeast(0L)

                if (calculatedBaseline > existing.baselineUsedSeconds || calculatedOvertime > existing.overtimeUsedSeconds) {
                    val updated = existing.copy(
                        baselineUsedSeconds = maxOf(existing.baselineUsedSeconds, calculatedBaseline),
                        overtimeUsedSeconds = maxOf(existing.overtimeUsedSeconds, calculatedOvertime),
                        lastActiveTimestamp = stats.lastTimeUsed
                    )
                    usageDao.insertOrUpdate(updated)

                    // If active in foreground and baseline is exhausted, check if prompt is needed
                    if (updated.isBaselineExhausted(config.baselineMinutes) && _activeForegroundPackage.value == config.packageName) {
                        val activeExt = _activeExtensionPackage.value
                        val expiresAt = _activeExtensionExpiresAt.value
                        val isUnderExtension = (activeExt == config.packageName && expiresAt != null && now < expiresAt)
                        if (!isUnderExtension && _currentPrompt.value == null) {
                            showPrompt(
                                PromptData(
                                    packageName = config.packageName,
                                    appName = config.appName,
                                    baselineMinutes = config.baselineMinutes,
                                    isRelaunchAfterExhaustion = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Core Time Tracking Engine:
     * Evaluates active foreground app and checks whether it has exceeded baseline.
     */
    suspend fun onForegroundAppChanged(newPackage: String?) {
        val previousPackage = _activeForegroundPackage.value
        _activeForegroundPackage.value = newPackage

        val activeExtPkg = _activeExtensionPackage.value
        val extExpiresAt = _activeExtensionExpiresAt.value

        if (newPackage == null || newPackage == context.packageName || isPackageSuppressed(newPackage)) {
            return
        }

        val config = configDao.getByPackage(newPackage) ?: return
        if (!config.isMonitored) return

        val today = getTodayDateString()
        val usage = getUsageForPackage(newPackage, today)

        val isUnderValidExtension = activeExtPkg == newPackage &&
                extExpiresAt != null &&
                System.currentTimeMillis() < extExpiresAt

        if (isUnderValidExtension) {
            // App is allowed under current temporary 5m extension window
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
        if (isPackageSuppressed(packageName)) return

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
                val expiresAt = _activeExtensionExpiresAt.value
                val hasExtension = activeExt == packageName && expiresAt != null && now < expiresAt
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

            // Check if extension is active or expired
            val activeExt = _activeExtensionPackage.value
            val expiresAt = _activeExtensionExpiresAt.value
            if (activeExt == packageName && expiresAt != null && now < expiresAt) {
                // Extension is currently active! Do NOT show prompt.
            } else if (activeExt == packageName && expiresAt != null && now >= expiresAt) {
                // 5m expired! Prompt reappears offering another 5-minute extension
                _activeExtensionPackage.value = null
                _activeExtensionExpiresAt.value = null
                showPrompt(
                    PromptData(
                        packageName = packageName,
                        appName = config.appName,
                        baselineMinutes = config.baselineMinutes,
                        isRelaunchAfterExhaustion = false
                    )
                )
            } else if (activeExt != packageName) {
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
     * Grants a temporary 5-minute extension window.
     * Baseline limit is strictly preserved!
     */
    suspend fun grantFiveMinuteExtension(packageName: String, appName: String) {
        val now = System.currentTimeMillis()
        val durationMs = 5 * 60 * 1000L
        _activeExtensionPackage.value = packageName
        _activeExtensionExpiresAt.value = now + durationMs
        _currentPrompt.value = null

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

    /**
     * Closes the target application and immediately sends the user to the Home launcher.
     * Prevents the app from minimizing to Picture-in-Picture or a corner window.
     * Temporary extension is terminated immediately.
     */
    suspend fun closeTargetApp(packageName: String) {
        _currentPrompt.value = null
        _activeExtensionPackage.value = null
        _activeExtensionExpiresAt.value = null
        _activeForegroundPackage.value = null
        suppressPackage(packageName, 6000L)

        logDao.insert(
            ExtensionSessionLog(
                packageName = packageName,
                appName = configDao.getByPackage(packageName)?.appName ?: packageName,
                status = "TERMINATED_USER_CLOSED"
            )
        )

        // 1. Immediately request transient audio focus to stop media/video playback.
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
                scope.launch {
                    delay(1000L)
                    audioManager?.abandonAudioFocusRequest(focusRequest)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                scope.launch {
                    delay(1000L)
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
            }
        } catch (_: Exception) {}

        // 2. Launch Home launcher with clean flags to bring the device Home immediately
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(homeIntent)

        // 3. Kill background processes immediately and asynchronously
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        try {
            activityManager?.killBackgroundProcesses(packageName)
        } catch (_: Exception) {}

        scope.launch(Dispatchers.IO) {
            try {
                delay(150L)
                activityManager?.killBackgroundProcesses(packageName)
                delay(350L)
                activityManager?.killBackgroundProcesses(packageName)
            } catch (_: Exception) {}
        }
    }

    fun showPrompt(data: PromptData) {
        if (isPackageSuppressed(data.packageName)) {
            return
        }
        val current = _currentPrompt.value
        // If the prompt is already displayed for this target package, do not re-emit.
        // This keeps the modal popup completely still and eliminates jitter/movement.
        if (current != null && current.packageName == data.packageName) {
            return
        }
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
        if (_currentDateFlow.value != newDate) {
            _currentDateFlow.value = newDate
        }
        _activeExtensionPackage.value = null
        _activeExtensionExpiresAt.value = null
        _currentPrompt.value = null
    }

    /**
     * Automated/Testing usage reset:
     * Clears consumed daily usage while keeping all configured baseline limits intact.
     */
    suspend fun resetTodayUsage(date: String = getTodayDateString()) {
        usageDao.clearUsageForDate(date)
        _activeExtensionPackage.value = null
        _activeExtensionExpiresAt.value = null
        _currentPrompt.value = null
    }
}
