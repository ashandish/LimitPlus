package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable Baseline Configuration for an app.
 * The daily baseline represents the actual daily quota and must NEVER
 * be permanently overwritten or increased by temporary extensions.
 */
@Entity(tableName = "app_limit_configs")
data class AppLimitConfig(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isMonitored: Boolean = true,
    val baselineMinutes: Int = 20, // Immutable baseline quota
    val isCustomOrPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
