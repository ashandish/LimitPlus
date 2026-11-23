package com.limitplus.app.ui.overlay

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.limitplus.app.LimitPlusApp
import com.limitplus.app.ui.theme.LimitPlusTheme
import kotlinx.coroutines.launch

class OverlayDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finishAndRemoveTask()
            return
        }
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val baselineMinutes = intent.getIntExtra(EXTRA_BASELINE_MINUTES, 20)
        val isRelaunch = intent.getBooleanExtra(EXTRA_IS_RELAUNCH, false)
        val totalUsedSeconds = intent.getLongExtra(EXTRA_TOTAL_USED_SECONDS, baselineMinutes * 60L)
        val extensionsGrantedToday = intent.getIntExtra(EXTRA_EXTENSIONS_GRANTED_TODAY, 0)
        val multiPressEnabled = intent.getBooleanExtra(EXTRA_MULTI_PRESS_ENABLED, true)
        val pauseDelaySeconds = intent.getIntExtra(EXTRA_PAUSE_DELAY_SECONDS, 0)
        val promptId = intent.getLongExtra(EXTRA_PROMPT_ID, 0L)

        // Auto-dismiss if prompt is dismissed/cleared or for a different app
        lifecycleScope.launch {
            LimitPlusApp.instance.repository.currentPrompt.collect { prompt ->
                if (prompt == null || prompt.packageName != packageName) {
                    finishAndRemoveTask()
                }
            }
        }

        // Handle hardware back button: gracefully close the target app and remove task
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                CloseAppActivity.start(this@OverlayDialogActivity, packageName)
                finishAndRemoveTask()
            }
        })

        setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = packageName,
                    appName = appName,
                    baselineMinutes = baselineMinutes,
                    isRelaunch = isRelaunch,
                    totalUsedSeconds = totalUsedSeconds,
                    extensionsGrantedToday = extensionsGrantedToday,
                    multiPressEnabled = multiPressEnabled,
                    pauseDelaySeconds = pauseDelaySeconds,
                    promptId = promptId,
                    onAddFiveMinutes = {
                        LimitPlusApp.instance.repository.grantFiveMinuteExtension(packageName, appName)
                        finishAndRemoveTask()
                    },
                    onCloseApp = {
                        CloseAppActivity.start(this@OverlayDialogActivity, packageName)
                        finishAndRemoveTask()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_BASELINE_MINUTES = "extra_baseline_minutes"
        const val EXTRA_IS_RELAUNCH = "extra_is_relaunch"
        const val EXTRA_TOTAL_USED_SECONDS = "extra_total_used_seconds"
        const val EXTRA_EXTENSIONS_GRANTED_TODAY = "extra_extensions_granted_today"
        const val EXTRA_MULTI_PRESS_ENABLED = "extra_multi_press_enabled"
        const val EXTRA_PAUSE_DELAY_SECONDS = "extra_pause_delay_seconds"
        const val EXTRA_PROMPT_ID = "extra_prompt_id"
    }
}
