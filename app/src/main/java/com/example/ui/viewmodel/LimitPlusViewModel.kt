package com.example.ui.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.LimitPlusApp
import com.example.data.entity.AppLimitConfig
import com.example.data.entity.DailyAppUsage
import com.example.data.entity.ExtensionSessionLog
import com.example.data.repository.AppLimitRepository
import com.example.data.repository.InstalledAppItem
import com.example.data.repository.PromptData
import com.example.service.ScreenTimeMonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

data class PermissionsStatus(
    val hasUsageAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasBatteryOptimizationIgnored: Boolean = false,
    val hasNotificationPermission: Boolean = false
) {
    val allEssentialGranted: Boolean
        get() = hasUsageAccess && hasOverlayPermission

    val allGranted: Boolean
        get() = hasUsageAccess && hasOverlayPermission && hasBatteryOptimizationIgnored && hasNotificationPermission
}

data class ActiveSessionUiState(
    val foregroundPackage: String? = null,
    val foregroundAppName: String? = null,
    val isMonitored: Boolean = false,
    val baselineMinutes: Int = 0,
    val baselineUsedSeconds: Long = 0L,
    val overtimeUsedSeconds: Long = 0L,
    val isBaselineExhausted: Boolean = false,
    val isUnderActiveExtension: Boolean = false,
    val extensionRemainingSeconds: Long = 0L,
    val promptData: PromptData? = null
)

class LimitPlusViewModel(
    private val repository: AppLimitRepository = LimitPlusApp.instance.repository
) : ViewModel() {

    private val context: Context get() = LimitPlusApp.instance

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _permissions = MutableStateFlow(checkPermissions())
    val permissions: StateFlow<PermissionsStatus> = _permissions.asStateFlow()

    private val _timeUntilMidnight = MutableStateFlow(calculateTimeUntilMidnight())
    val timeUntilMidnight: StateFlow<String> = _timeUntilMidnight.asStateFlow()

    private val _extensionRemainingSeconds = MutableStateFlow(0L)
    val extensionRemainingSeconds: StateFlow<Long> = _extensionRemainingSeconds.asStateFlow()

    val currentPrompt: StateFlow<PromptData?> = repository.currentPrompt

    val recentLogs: StateFlow<List<ExtensionSessionLog>> = repository.recentLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combine configs, daily usages, foreground app, active extension, and search query into list
    val appsList: StateFlow<List<InstalledAppItem>> = combine(
        repository.allConfigsFlow,
        repository.todayUsageFlow,
        repository.activeForegroundPackage,
        repository.activeExtensionPackage,
        _searchQuery
    ) { configs, usages, activePkg, extPkg, query ->
        val usageMap = usages.associateBy { it.packageName }
        configs.map { config ->
            val usage = usageMap[config.packageName] ?: DailyAppUsage(
                id = "${config.packageName}_${repository.getTodayDateString()}",
                packageName = config.packageName,
                date = repository.getTodayDateString()
            )
            val isExhausted = usage.isBaselineExhausted(config.baselineMinutes)
            val isActive = (config.packageName == activePkg)
            val isExt = (config.packageName == extPkg)

            InstalledAppItem(
                packageName = config.packageName,
                appName = config.appName,
                isMonitored = config.isMonitored,
                baselineMinutes = config.baselineMinutes,
                todayBaselineUsedSeconds = usage.baselineUsedSeconds,
                todayOvertimeUsedSeconds = usage.overtimeUsedSeconds,
                extensionsCountToday = usage.extensionsGrantedCount,
                isBaselineExhausted = isExhausted,
                isCurrentlyActive = isActive,
                isUnderActiveExtension = isExt
            )
        }.filter { item ->
            query.isBlank() ||
                    item.appName.contains(query, ignoreCase = true) ||
                    item.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessionState: StateFlow<ActiveSessionUiState> = combine(
        appsList,
        repository.activeForegroundPackage,
        repository.activeExtensionPackage,
        _extensionRemainingSeconds,
        repository.currentPrompt
    ) { apps, fgPkg, extPkg, remainingSec, prompt ->
        val matchedApp = apps.find { it.packageName == fgPkg }
        ActiveSessionUiState(
            foregroundPackage = fgPkg,
            foregroundAppName = matchedApp?.appName ?: fgPkg,
            isMonitored = matchedApp?.isMonitored ?: false,
            baselineMinutes = matchedApp?.baselineMinutes ?: 0,
            baselineUsedSeconds = matchedApp?.todayBaselineUsedSeconds ?: 0L,
            overtimeUsedSeconds = matchedApp?.todayOvertimeUsedSeconds ?: 0L,
            isBaselineExhausted = matchedApp?.isBaselineExhausted ?: false,
            isUnderActiveExtension = (matchedApp?.packageName == extPkg && remainingSec > 0),
            extensionRemainingSeconds = remainingSec,
            promptData = prompt
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActiveSessionUiState())

    init {
        // Observe remaining active extension seconds directly from repository
        viewModelScope.launch {
            repository.activeExtensionRemainingSeconds.collect { remSec ->
                _extensionRemainingSeconds.value = remSec ?: 0L
            }
        }

        // Ticking loop for midnight timer and permissions
        viewModelScope.launch {
            while (isActive) {
                // Automated rollover check if calendar day changed
                val todayDate = repository.getTodayDateString()
                if (todayDate != repository.currentDateFlow.value) {
                    repository.onMidnightRollover()
                }

                _permissions.value = checkPermissions()
                _timeUntilMidnight.value = calculateTimeUntilMidnight()

                delay(1000L)
            }
        }

        // Start Foreground monitoring service
        ScreenTimeMonitorService.startService(context)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleMonitored(packageName: String, isMonitored: Boolean) {
        viewModelScope.launch {
            repository.setMonitored(packageName, isMonitored)
        }
    }

    fun updateBaseline(packageName: String, baselineMinutes: Int) {
        viewModelScope.launch {
            repository.updateBaseline(packageName, baselineMinutes)
        }
    }

    fun grantExtension(packageName: String, appName: String) {
        repository.grantFiveMinuteExtension(packageName, appName)
    }

    fun closeTargetApp(packageName: String) {
        repository.closeTargetApp(packageName)
    }

    fun dismissPrompt() {
        repository.dismissPrompt()
    }

    fun addCustomApp(packageName: String, appName: String, baselineMinutes: Int) {
        viewModelScope.launch {
            repository.addCustomApp(packageName, appName, baselineMinutes)
        }
    }

    fun triggerMidnightRollover() {
        viewModelScope.launch {
            repository.onMidnightRollover()
        }
    }

    fun refreshPermissions() {
        _permissions.value = checkPermissions()
    }

    fun checkPermissions(): PermissionsStatus {
        val hasUsage = checkUsageAccess()
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Settings.canDrawOverlays(context)
            } catch (_: Exception) {
                false
            }
        } else true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val hasBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Exception) {
                false
            }
        } else true

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        return PermissionsStatus(
            hasUsageAccess = hasUsage,
            hasOverlayPermission = hasOverlay,
            hasBatteryOptimizationIgnored = hasBattery,
            hasNotificationPermission = hasNotification
        )
    }

    private fun checkUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun calculateTimeUntilMidnight(): String {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        while (midnight.timeInMillis <= now.timeInMillis) {
            midnight.add(Calendar.DAY_OF_YEAR, 1)
        }
        val diffMs = (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        val hours = diffMs / (1000 * 60 * 60)
        val minutes = (diffMs / (1000 * 60)) % 60
        val seconds = (diffMs / 1000) % 60
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds)
    }

    // --- Simulator & Testing Helpers ---
    fun simulateForegroundApp(packageName: String) {
        viewModelScope.launch {
            repository.onForegroundAppChanged(packageName)
        }
    }

    fun simulateAddUsageSeconds(packageName: String, seconds: Long) {
        viewModelScope.launch {
            repository.recordUsageTick(packageName, seconds)
        }
    }

    fun simulateExitToHome() {
        viewModelScope.launch {
            repository.onForegroundAppChanged(null)
        }
    }

    fun triggerTestPrompt(packageName: String, appName: String, baselineMinutes: Int, isRelaunch: Boolean) {
        repository.showPrompt(
            PromptData(
                packageName = packageName,
                appName = appName,
                baselineMinutes = baselineMinutes,
                isRelaunchAfterExhaustion = isRelaunch
            )
        )
    }
}
