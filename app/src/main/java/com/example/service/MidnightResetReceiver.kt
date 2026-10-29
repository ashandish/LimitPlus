package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.LimitPlusApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Automates daily midnight reset:
 * Receives scheduled midnight alarm triggers and system date/time change broadcasts.
 * Automatically initiates the midnight rollover in the repository, dismissing previous-day
 * exhaustion prompts/extensions, and re-schedules the next midnight alarm.
 * Users are not allowed to reset on their own.
 */
class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("MidnightResetReceiver", "Triggered with action: $action")

        val pendingResult = goAsync()
        val repository = LimitPlusApp.instance.repository
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.onMidnightRollover()
            } catch (e: Exception) {
                Log.e("MidnightResetReceiver", "Error during midnight rollover", e)
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }

        // Re-schedule alarm for the next midnight
        scheduleNextMidnightAlarm(context)
    }

    companion object {
        const val ACTION_MIDNIGHT_ALARM = "com.aistudio.limitplus.ACTION_MIDNIGHT_ALARM"
        private const val REQUEST_CODE = 8001

        fun scheduleNextMidnightAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

                val now = System.currentTimeMillis()
                val midnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, 1)
                }

                while (midnight.timeInMillis <= now) {
                    midnight.add(Calendar.DAY_OF_YEAR, 1)
                }

                val intent = Intent(context, MidnightResetReceiver::class.java).apply {
                    action = ACTION_MIDNIGHT_ALARM
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            midnight.timeInMillis,
                            pendingIntent
                        )
                    } catch (_: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            midnight.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        midnight.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("MidnightResetReceiver", "Scheduled next midnight alarm for ${midnight.time}")
            } catch (e: Exception) {
                Log.w("MidnightResetReceiver", "Could not schedule midnight alarm", e)
            }
        }
    }
}
