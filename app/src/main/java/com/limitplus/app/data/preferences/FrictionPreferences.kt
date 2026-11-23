package com.limitplus.app.data.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FrictionSettings(
    val isMultiPressEnabled: Boolean = true,
    val isPauseDelayEnabled: Boolean = false,
    val pauseDelaySeconds: Int = DEFAULT_PAUSE_DELAY_SECONDS
) {
    val effectivePauseDelaySeconds: Int
        get() = if (isPauseDelayEnabled) {
            pauseDelaySeconds.coerceIn(MIN_PAUSE_DELAY_SECONDS, MAX_PAUSE_DELAY_SECONDS)
        } else {
            0
        }

    fun activeModeSummary(): String = when {
        isPauseDelayEnabled && isMultiPressEnabled ->
            "${effectivePauseDelaySeconds}s pause, then escalating presses"
        isPauseDelayEnabled ->
            "${effectivePauseDelaySeconds}s pause delay before extending"
        isMultiPressEnabled ->
            "Escalating presses per extension"
        else ->
            "Instant extension (no extra friction)"
    }

    companion object {
        const val MIN_PAUSE_DELAY_SECONDS = 3
        const val MAX_PAUSE_DELAY_SECONDS = 120
        const val DEFAULT_PAUSE_DELAY_SECONDS = 10
        val PRESET_DELAY_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}

class FrictionPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FrictionSettings> = _settings.asStateFlow()

    val currentSettings: FrictionSettings
        get() = _settings.value

    private fun loadSettings(): FrictionSettings {
        val multiPress = prefs.getBoolean(KEY_MULTI_PRESS_ENABLED, true)
        val pauseDelay = prefs.getBoolean(KEY_PAUSE_DELAY_ENABLED, false)
        val delaySeconds = prefs.getInt(
            KEY_PAUSE_DELAY_SECONDS,
            FrictionSettings.DEFAULT_PAUSE_DELAY_SECONDS
        ).coerceIn(
            FrictionSettings.MIN_PAUSE_DELAY_SECONDS,
            FrictionSettings.MAX_PAUSE_DELAY_SECONDS
        )
        return FrictionSettings(
            isMultiPressEnabled = multiPress,
            isPauseDelayEnabled = pauseDelay,
            pauseDelaySeconds = delaySeconds
        )
    }

    fun setMultiPressEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MULTI_PRESS_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(isMultiPressEnabled = enabled)
    }

    fun setPauseDelayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_DELAY_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(isPauseDelayEnabled = enabled)
    }

    fun setPauseDelaySeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            FrictionSettings.MIN_PAUSE_DELAY_SECONDS,
            FrictionSettings.MAX_PAUSE_DELAY_SECONDS
        )
        prefs.edit().putInt(KEY_PAUSE_DELAY_SECONDS, clamped).apply()
        _settings.value = _settings.value.copy(pauseDelaySeconds = clamped)
    }

    companion object {
        private const val PREFS_NAME = "limitplus_friction_prefs"
        private const val KEY_MULTI_PRESS_ENABLED = "multi_press_enabled"
        private const val KEY_PAUSE_DELAY_ENABLED = "pause_delay_enabled"
        private const val KEY_PAUSE_DELAY_SECONDS = "pause_delay_seconds"
    }
}
