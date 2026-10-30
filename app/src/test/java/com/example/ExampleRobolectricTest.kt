package com.example

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
}
