package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ExtensionSessionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionLogDao {
    @Query("SELECT * FROM extension_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogsFlow(): Flow<List<ExtensionSessionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExtensionSessionLog)

    @Query("DELETE FROM extension_logs")
    suspend fun clearLogs()
}
