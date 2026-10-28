package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit log of every +5 minute temporary extension granted.
 */
@Entity(tableName = "extension_logs")
data class ExtensionSessionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val extensionMinutes: Int = 5,
    val status: String = "GRANTED" // GRANTED, COMPLETED_5M, TERMINATED_ON_EXIT
)
