package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily Usage Record tracking baseline time consumed vs. temporary overtime (+5m).
 * Cleared on daily midnight reset, while AppLimitConfig baseline remains strictly intact.
 */
@Entity(tableName = "daily_app_usage")
data class DailyAppUsage(
    @PrimaryKey
    val id: String, // e.g. "com.google.android.youtube_2026-10-28"
    val packageName: String,
    val date: String, // "yyyy-MM-dd"
    val baselineUsedSeconds: Long = 0L,
    val overtimeUsedSeconds: Long = 0L,
    val extensionsGrantedCount: Int = 0,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
) {
    val totalUsedSeconds: Long
        get() = baselineUsedSeconds + overtimeUsedSeconds

    fun isBaselineExhausted(baselineMinutes: Int): Boolean {
        return baselineUsedSeconds >= (baselineMinutes * 60L)
    }

    fun remainingBaselineSeconds(baselineMinutes: Int): Long {
        val maxBaseline = baselineMinutes * 60L
        return (maxBaseline - baselineUsedSeconds).coerceAtLeast(0L)
    }
}
