package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AppLimitConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitConfigDao {
    @Query("SELECT * FROM app_limit_configs ORDER BY isMonitored DESC, appName ASC")
    fun getAllFlow(): Flow<List<AppLimitConfig>>

    @Query("SELECT * FROM app_limit_configs")
    suspend fun getAllList(): List<AppLimitConfig>

    @Query("SELECT * FROM app_limit_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AppLimitConfig?

    @Query("SELECT * FROM app_limit_configs WHERE packageName = :packageName LIMIT 1")
    fun getByPackageFlow(packageName: String): Flow<AppLimitConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: AppLimitConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<AppLimitConfig>)

    @Query("UPDATE app_limit_configs SET isMonitored = :isMonitored WHERE packageName = :packageName")
    suspend fun setMonitored(packageName: String, isMonitored: Boolean)

    @Query("UPDATE app_limit_configs SET baselineMinutes = :baselineMinutes WHERE packageName = :packageName")
    suspend fun updateBaseline(packageName: String, baselineMinutes: Int)

    @Query("DELETE FROM app_limit_configs WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
