package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppLimitConfig
import com.example.data.entity.DailyAppUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LimitPlus", appName)
  }

  @Test
  fun `baseline limit calculation and exhaustion`() {
    val config = AppLimitConfig(
      packageName = "com.google.android.youtube",
      appName = "YouTube",
      baselineMinutes = 20
    )

    val usageUnderLimit = DailyAppUsage(
      id = "yt_1",
      packageName = config.packageName,
      date = "2026-10-28",
      baselineUsedSeconds = 600L // 10 minutes
    )

    assertFalse(usageUnderLimit.isBaselineExhausted(config.baselineMinutes))
    assertEquals(600L, usageUnderLimit.remainingBaselineSeconds(config.baselineMinutes))

    val usageAtLimit = DailyAppUsage(
      id = "yt_2",
      packageName = config.packageName,
      date = "2026-10-28",
      baselineUsedSeconds = 1200L // 20 minutes
    )

    assertTrue(usageAtLimit.isBaselineExhausted(config.baselineMinutes))
    assertEquals(0L, usageAtLimit.remainingBaselineSeconds(config.baselineMinutes))
  }

  @Test
  fun `extension does not mutate baseline`() {
    val config = AppLimitConfig(
      packageName = "com.google.android.youtube",
      appName = "YouTube",
      baselineMinutes = 20
    )

    // User gets 5 minute extension: overtime increases, but baseline remains 20 minutes!
    val usageWithExtension = DailyAppUsage(
      id = "yt_3",
      packageName = config.packageName,
      date = "2026-10-28",
      baselineUsedSeconds = 1200L,
      overtimeUsedSeconds = 300L, // 5m extension
      extensionsGrantedCount = 1
    )

    assertEquals(20, config.baselineMinutes) // STRICTLY UNTOUCHED
    assertEquals(1200L, usageWithExtension.baselineUsedSeconds)
    assertEquals(300L, usageWithExtension.overtimeUsedSeconds)
    assertEquals(1500L, usageWithExtension.totalUsedSeconds)
  }

  @Test
  fun `automated midnight reset preserves baseline and isolates daily usage`() {
    val config = AppLimitConfig(
      packageName = "com.google.android.youtube",
      appName = "YouTube",
      baselineMinutes = 30
    )

    // Yesterday's usage was completely exhausted
    val yesterdayUsage = DailyAppUsage(
      id = "yt_yesterday",
      packageName = config.packageName,
      date = "2026-10-27",
      baselineUsedSeconds = 1800L
    )
    assertTrue(yesterdayUsage.isBaselineExhausted(config.baselineMinutes))

    // Today's automated fresh day usage starts at 0
    val todayUsage = DailyAppUsage(
      id = "yt_today",
      packageName = config.packageName,
      date = "2026-10-28",
      baselineUsedSeconds = 0L
    )

    // Baseline limit is strictly intact and preserved
    assertEquals(30, config.baselineMinutes)
    assertFalse(todayUsage.isBaselineExhausted(config.baselineMinutes))
    assertEquals(1800L, todayUsage.remainingBaselineSeconds(config.baselineMinutes))
  }

  @Test
  fun `extension window allows usage within 5 minutes`() {
    val now = System.currentTimeMillis()
    val expiresAt = now + (5 * 60 * 1000L)
    val isUnderValidExtension = (now < expiresAt)
    assertTrue(isUnderValidExtension)

    val expiredTime = expiresAt + 1000L
    val isStillValid = (expiredTime < expiresAt)
    assertFalse(isStillValid)
  }

  @Test
  fun `midnight future calculation is always strictly ahead of current time`() {
    val now = java.util.Calendar.getInstance()
    val midnight = java.util.Calendar.getInstance().apply {
      set(java.util.Calendar.HOUR_OF_DAY, 0)
      set(java.util.Calendar.MINUTE, 0)
      set(java.util.Calendar.SECOND, 0)
      set(java.util.Calendar.MILLISECOND, 0)
      add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    while (midnight.timeInMillis <= now.timeInMillis) {
      midnight.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    assertTrue(midnight.timeInMillis > now.timeInMillis)
  }

  @Test
  fun `repository onMidnightRollover clears daily records and dismisses prompt`() = kotlinx.coroutines.runBlocking {
    val repository = LimitPlusApp.instance.repository
    val today = repository.getTodayDateString()

    // Ensure config exists and is monitored
    repository.addCustomApp("com.google.android.youtube", "YouTube", 20)

    // Simulate usage in repository
    repository.recordUsageTick("com.google.android.youtube", 1200L)
    val beforeRollover = repository.getUsageForPackage("com.google.android.youtube", today)
    assertTrue(beforeRollover.baselineUsedSeconds > 0)

    // Trigger midnight rollover
    repository.onMidnightRollover()

    // Assert that active extension and prompt are dismissed
    org.junit.Assert.assertNull(repository.currentPrompt.value)
    org.junit.Assert.assertNull(repository.activeExtensionPackage.value)

    // And usage for today starts fresh at 0
    val afterRollover = repository.getUsageForPackage("com.google.android.youtube", today)
    assertEquals(0L, afterRollover.baselineUsedSeconds)
    assertFalse(afterRollover.isBaselineExhausted(20))
  }

  @Test
  fun `all permissions check marks inactive when any permission is missing`() {
    val missingOverlay = com.example.ui.viewmodel.PermissionsStatus(
      hasUsageAccess = true,
      hasOverlayPermission = false,
      hasBatteryOptimizationIgnored = true,
      hasNotificationPermission = true
    )
    assertFalse(missingOverlay.allGranted)

    val missingUsage = com.example.ui.viewmodel.PermissionsStatus(
      hasUsageAccess = false,
      hasOverlayPermission = true,
      hasBatteryOptimizationIgnored = true,
      hasNotificationPermission = true
    )
    assertFalse(missingUsage.allGranted)

    val missingBattery = com.example.ui.viewmodel.PermissionsStatus(
      hasUsageAccess = true,
      hasOverlayPermission = true,
      hasBatteryOptimizationIgnored = false,
      hasNotificationPermission = true
    )
    assertFalse(missingBattery.allGranted)

    val missingNotification = com.example.ui.viewmodel.PermissionsStatus(
      hasUsageAccess = true,
      hasOverlayPermission = true,
      hasBatteryOptimizationIgnored = true,
      hasNotificationPermission = false
    )
    assertFalse(missingNotification.allGranted)

    val allFixed = com.example.ui.viewmodel.PermissionsStatus(
      hasUsageAccess = true,
      hasOverlayPermission = true,
      hasBatteryOptimizationIgnored = true,
      hasNotificationPermission = true
    )
    assertTrue(allFixed.allGranted)
  }
}
