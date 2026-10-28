package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.DailyAppUsage
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_app_usage WHERE date = :date")
    fun getAllForDateFlow(date: String): Flow<List<DailyAppUsage>>

    @Query("SELECT * FROM daily_app_usage WHERE date = :date")
    suspend fun getAllForDate(date: String): List<DailyAppUsage>

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName AND date = :date LIMIT 1")
    suspend fun getUsage(packageName: String, date: String): DailyAppUsage?

    @Query("SELECT * FROM daily_app_usage WHERE packageName = :packageName AND date = :date LIMIT 1")
    fun getUsageFlow(packageName: String, date: String): Flow<DailyAppUsage?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(usage: DailyAppUsage)

    @Query("UPDATE daily_app_usage SET baselineUsedSeconds = :seconds, lastActiveTimestamp = :timestamp WHERE packageName = :packageName AND date = :date")
    suspend fun updateBaselineUsed(packageName: String, date: String, seconds: Long, timestamp: Long)

    @Query("UPDATE daily_app_usage SET overtimeUsedSeconds = overtimeUsedSeconds + :addSeconds, lastActiveTimestamp = :timestamp WHERE packageName = :packageName AND date = :date")
    suspend fun addOvertimeUsed(packageName: String, date: String, addSeconds: Long, timestamp: Long)

    @Query("UPDATE daily_app_usage SET extensionsGrantedCount = extensionsGrantedCount + 1 WHERE packageName = :packageName AND date = :date")
    suspend fun incrementExtensionCount(packageName: String, date: String)

    @Query("DELETE FROM daily_app_usage WHERE date = :date")
    suspend fun clearUsageForDate(date: String)

    @Query("DELETE FROM daily_app_usage")
    suspend fun clearAll()
}
