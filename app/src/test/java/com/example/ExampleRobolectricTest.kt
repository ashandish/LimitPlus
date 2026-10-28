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
}
