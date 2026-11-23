package com.limitplus.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.limitplus.app.LimitPlusApp
import com.limitplus.app.ui.overlay.CloseAppActivity
import com.limitplus.app.ui.overlay.OverlayPromptCard
import com.limitplus.app.ui.theme.LimitPlusTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentShowingPackage: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        serviceScope.launch {
            LimitPlusApp.instance.repository.currentPrompt.collect { prompt ->
                if (prompt == null || (currentShowingPackage != null && prompt.packageName != currentShowingPackage)) {
                    removeOverlay()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: return START_NOT_STICKY
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        val baselineMinutes = intent.getIntExtra(EXTRA_BASELINE_MINUTES, 20)
        val isRelaunch = intent.getBooleanExtra(EXTRA_IS_RELAUNCH, false)
        val totalUsedSeconds = intent.getLongExtra(EXTRA_TOTAL_USED_SECONDS, baselineMinutes * 60L)
        val extensionsGrantedToday = intent.getIntExtra(EXTRA_EXTENSIONS_GRANTED_TODAY, 0)
        val multiPressEnabled = intent.getBooleanExtra(EXTRA_MULTI_PRESS_ENABLED, true)
        val pauseDelaySeconds = intent.getIntExtra(EXTRA_PAUSE_DELAY_SECONDS, 0)
        val promptId = intent.getLongExtra(EXTRA_PROMPT_ID, 0L)

        showOverlay(
            targetPackage = packageName,
            appName = appName,
            baselineMinutes = baselineMinutes,
            isRelaunch = isRelaunch,
            totalUsedSeconds = totalUsedSeconds,
            extensionsGrantedToday = extensionsGrantedToday,
            multiPressEnabled = multiPressEnabled,
            pauseDelaySeconds = pauseDelaySeconds,
            promptId = promptId
        )
        return START_NOT_STICKY
    }

    private fun showOverlay(
        targetPackage: String,
        appName: String,
        baselineMinutes: Int,
        isRelaunch: Boolean,
        totalUsedSeconds: Long,
        extensionsGrantedToday: Int,
        multiPressEnabled: Boolean,
        pauseDelaySeconds: Int,
        promptId: Long
    ) {
        if (overlayView != null && currentShowingPackage == targetPackage) {
            // Card is already displayed and completely still on screen
            return
        }
        currentShowingPackage = targetPackage
        removeOverlay()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    CloseAppActivity.start(this@FloatingOverlayService, targetPackage)
                    removeOverlay()
                    stopSelf()
                    true
                } else {
                    false
                }
            }

            setContent {
                LimitPlusTheme {
                    OverlayPromptCard(
                        packageName = targetPackage,
                        appName = appName,
                        baselineMinutes = baselineMinutes,
                        isRelaunch = isRelaunch,
                        totalUsedSeconds = totalUsedSeconds,
                        extensionsGrantedToday = extensionsGrantedToday,
                        multiPressEnabled = multiPressEnabled,
                        pauseDelaySeconds = pauseDelaySeconds,
                        promptId = promptId,
                        onAddFiveMinutes = {
                            LimitPlusApp.instance.repository.grantFiveMinuteExtension(targetPackage, appName)
                            removeOverlay()
                            stopSelf()
                        },
                        onCloseApp = {
                            CloseAppActivity.start(this@FloatingOverlayService, targetPackage)
                            removeOverlay()
                            stopSelf()
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, layoutParams)
            overlayView = composeView
        } catch (_: Exception) {
            // Fallback will be handled via OverlayDialogActivity
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
            currentShowingPackage = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
        super.onDestroy()
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
