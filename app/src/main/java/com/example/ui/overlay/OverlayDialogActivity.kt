package com.example.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val baselineMinutes = intent.getIntExtra(EXTRA_BASELINE_MINUTES, 20)
        val isRelaunch = intent.getBooleanExtra(EXTRA_IS_RELAUNCH, false)

        setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = packageName,
                    appName = appName,
                    baselineMinutes = baselineMinutes,
                    isRelaunch = isRelaunch,
                    onAddFiveMinutes = {
                        lifecycleScope.launch {
                            LimitPlusApp.instance.repository.grantFiveMinuteExtension(packageName, appName)
                            finish()
                        }
                    },
                    onCloseApp = {
                        lifecycleScope.launch {
                            LimitPlusApp.instance.repository.closeTargetApp(packageName)
                            finish()
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
