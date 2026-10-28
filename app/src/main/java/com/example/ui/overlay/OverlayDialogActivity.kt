package com.example.ui.overlay

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
import com.example.LimitPlusApp
import com.example.ui.theme.LimitPlusTheme
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

        // Auto-dismiss if prompt is dismissed/cleared
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
                lifecycleScope.launch {
                    LimitPlusApp.instance.repository.closeTargetApp(packageName)
                    finishAndRemoveTask()
                }
            }
        })

        setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = packageName,
                    appName = appName,
                    baselineMinutes = baselineMinutes,
                    isRelaunch = isRelaunch,
                    onAddFiveMinutes = {
                        finishAndRemoveTask()
                        lifecycleScope.launch {
                            LimitPlusApp.instance.repository.grantFiveMinuteExtension(packageName, appName)
                        }
                    },
                    onCloseApp = {
                        finishAndRemoveTask()
                        lifecycleScope.launch {
                            LimitPlusApp.instance.repository.closeTargetApp(packageName)
                        }
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
    }
}
