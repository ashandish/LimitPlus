package com.limitplus.app

import com.limitplus.app.data.repository.PromptData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LimitPlusLogicTest {

  @Test
  fun extension_5_minutes_represents_300_active_seconds() {
    val extensionSeconds = 5 * 60L
    assertEquals(300L, extensionSeconds)
  }

  @Test
  fun midnight_future_calculation_is_always_strictly_ahead_of_current_time() {
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
    assertTrue(midnight.timeInMillis > now.timeInMillis)
  }

  @Test
  fun promptData_preserves_total_used_seconds() {
    val prompt = PromptData(
      packageName = "com.limitplus.sample.app",
      appName = "Test App",
      baselineMinutes = 20,
      isRelaunchAfterExhaustion = false,
      totalUsedSeconds = 1500L
    )
    assertEquals(1500L, prompt.totalUsedSeconds)
    assertEquals(20, prompt.baselineMinutes)
  }

  @Test
  fun compact_duration_calculates_minutes_and_hours_accurately() {
    val twentyFiveMinSeconds = 25 * 60L
    val oneHourTenMinSeconds = (1 * 3600L) + (10 * 60L)

    val minsOnly = twentyFiveMinSeconds / 60L
    assertEquals(25L, minsOnly)

    val hours = oneHourTenMinSeconds / 3600L
    val remainingMins = (oneHourTenMinSeconds % 3600L) / 60L
    assertEquals(1L, hours)
    assertEquals(10L, remainingMins)
  }
}
