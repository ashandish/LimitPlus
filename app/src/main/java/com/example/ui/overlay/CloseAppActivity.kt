package com.example.ui.overlay

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import com.example.LimitPlusApp

/**
 * Isolated transient activity that executes in its own task affinity.
 * It transitions the user to the Home screen, halts media playback in the target app,
 * and terminates background processes of the target app.
 *
 * Runs completely isolated from LimitPlus's MainActivity task so that launching LimitPlus
 * from the Android launcher is never affected.
 */
class CloseAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (targetPackage != null) {
            LimitPlusApp.instance.repository.closeTargetApp(targetPackage)
        }

        // 1. Halt audio/media playback immediately to stop PiP
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(focusReq)
                audioManager?.abandonAudioFocusRequest(focusReq)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}

        // 2. Bring Android Home launcher to the front
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            startActivity(homeIntent)
        } catch (_: Exception) {}

        // 3. Terminate background processes of the target app
        if (targetPackage != null) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(targetPackage)
            } catch (_: Exception) {}
        }

        // Finish transient activity cleanly without destroying the app's main task
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"

        fun start(context: Context, targetPackage: String) {
            val intent = Intent(context, CloseAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
            }
            context.startActivity(intent)
        }
    }
}
