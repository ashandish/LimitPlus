package com.limitplus.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.limitplus.app.data.dao.AppLimitConfigDao
import com.limitplus.app.data.dao.DailyUsageDao
import com.limitplus.app.data.dao.ExtensionLogDao
import com.limitplus.app.data.entity.AppLimitConfig
import com.limitplus.app.data.entity.DailyAppUsage
import com.limitplus.app.data.entity.ExtensionSessionLog

@Database(
    entities = [
        AppLimitConfig::class,
        DailyAppUsage::class,
        ExtensionSessionLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LimitPlusDatabase : RoomDatabase() {
    abstract fun appLimitConfigDao(): AppLimitConfigDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun extensionLogDao(): ExtensionLogDao

    companion object {
        @Volatile
        private var INSTANCE: LimitPlusDatabase? = null

        fun getDatabase(context: Context): LimitPlusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LimitPlusDatabase::class.java,
                    "limit_plus_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
