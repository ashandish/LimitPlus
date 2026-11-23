package com.limitplus.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.limitplus.app.BuildConfig
import com.limitplus.app.data.preferences.FrictionPreferences
import com.limitplus.app.data.preferences.FrictionSettings
import com.limitplus.app.ui.overlay.OverlayPromptCard
import com.limitplus.app.ui.permissions.PermissionsScreen
import com.limitplus.app.ui.theme.LimitPlusTheme
import com.limitplus.app.ui.viewmodel.PermissionsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayFrictionSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun frictionPreferences_persistsAndClampsValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FrictionPreferences(context)

        prefs.setMultiPressEnabled(false)
        prefs.setPauseDelayEnabled(true)
        prefs.setPauseDelaySeconds(15)

        val reloaded = FrictionPreferences(context).currentSettings
        assertFalse(reloaded.isMultiPressEnabled)
        assertTrue(reloaded.isPauseDelayEnabled)
        assertEquals(15, reloaded.pauseDelaySeconds)
        assertEquals(15, reloaded.effectivePauseDelaySeconds)

        // Clamping checks
        prefs.setPauseDelaySeconds(1)
        assertEquals(FrictionSettings.MIN_PAUSE_DELAY_SECONDS, prefs.currentSettings.pauseDelaySeconds)
        prefs.setPauseDelaySeconds(999)
        assertEquals(FrictionSettings.MAX_PAUSE_DELAY_SECONDS, prefs.currentSettings.pauseDelaySeconds)

        // When disabled, effectivePauseDelaySeconds is 0
        prefs.setPauseDelayEnabled(false)
        assertEquals(0, prefs.currentSettings.effectivePauseDelaySeconds)
    }

    @Test
    fun pauseDelayOnly_blocksUntilCountdownCompletesThenGrantsInOnePress() {
        var grantedCount = 0
        var closeCount = 0
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 2,
                    multiPressEnabled = false,
                    pauseDelaySeconds = 3,
                    promptId = 101L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = { closeCount++ }
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // During delay: extend button disabled with countdown label, close button enabled
        composeTestRule.onNodeWithTag("add_five_minutes_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Wait 3s").assertIsDisplayed()
        composeTestRule.onNodeWithText("Take a brief pause • Unlocks in 3s").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_delay_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_app_button").assertIsEnabled()

        // Advance past the 3-second pause delay
        composeTestRule.mainClock.advanceTimeBy(3500L)
        composeTestRule.waitForIdle()

        // Unlocked and multi-press is OFF -> single tap grants immediately
        composeTestRule.onNodeWithTag("add_five_minutes_button").assertIsEnabled()
        composeTestRule.onNodeWithText("+5 Minutes").assertIsDisplayed()
        composeTestRule.onNodeWithTag("extension_press_hint").assertDoesNotExist()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, grantedCount)
        assertEquals(0, closeCount)
    }

    @Test
    fun combinedMode_runsCountdownFirstThenRequiresEscalatingPresses() {
        var grantedCount = 0
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 1, // 2nd extension -> 2 presses
                    multiPressEnabled = true,
                    pauseDelaySeconds = 2,
                    promptId = 102L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Phase 1: Countdown active, mentions both wait and upcoming presses
        composeTestRule.onNodeWithTag("add_five_minutes_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Wait 2s").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pause active • 2s left, then press two times").assertIsDisplayed()

        // Advance past the 2-second countdown
        composeTestRule.mainClock.advanceTimeBy(2500L)
        composeTestRule.waitForIdle()

        // Phase 2: Unlocked, now requires 2 presses
        composeTestRule.onNodeWithTag("add_five_minutes_button").assertIsEnabled()
        composeTestRule.onNodeWithText("Extension #2 • Press two times to extend").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()
        assertEquals(0, grantedCount)
        composeTestRule.onNodeWithText("Press 1 more time to extend (1/2)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()
        assertEquals(1, grantedCount)
    }

    @Test
    fun neitherFrictionEnabled_grantsImmediatelyInOnePress() {
        var grantedCount = 0
        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 4,
                    multiPressEnabled = false,
                    pauseDelaySeconds = 0,
                    promptId = 103L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pause_delay_hint").assertDoesNotExist()
        composeTestRule.onNodeWithTag("extension_press_hint").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_five_minutes_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, grantedCount)
    }

    @Test
    fun settingsScreen_allowsTogglingBothModesAndConfiguringDelay() {
        var latestSettings = FrictionSettings()
        composeTestRule.setContent {
            LimitPlusTheme {
                var settings by remember { mutableStateOf(FrictionSettings()) }
                PermissionsScreen(
                    permissions = PermissionsStatus(
                        hasUsageAccess = true,
                        hasOverlayPermission = true,
                        hasBatteryOptimizationIgnored = true,
                        hasNotificationPermission = true
                    ),
                    frictionSettings = settings,
                    onMultiPressToggle = {
                        settings = settings.copy(isMultiPressEnabled = it)
                        latestSettings = settings
                    },
                    onPauseDelayToggle = {
                        settings = settings.copy(isPauseDelayEnabled = it)
                        latestSettings = settings
                    },
                    onPauseDelaySecondsChange = {
                        settings = settings.copy(pauseDelaySeconds = it)
                        latestSettings = settings
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Initially Multi-Press ON, Pause Delay OFF
        composeTestRule.onNodeWithText("Active mode: Escalating presses per extension").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pause_delay_config_panel").assertDoesNotExist()

        // Enable Pause Delay -> reveals duration controls and combined mode summary
        composeTestRule.onNodeWithTag("switch_pause_delay").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(latestSettings.isPauseDelayEnabled)
        composeTestRule.onNodeWithTag("pause_delay_config_panel").performScrollTo().assertIsDisplayed()

        // Select 30s preset pill
        composeTestRule.onNodeWithTag("delay_preset_30").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(30, latestSettings.pauseDelaySeconds)
        composeTestRule.onNodeWithTag("pause_delay_value").performScrollTo().assertIsDisplayed()

        // Step up by 1s to 31s
        composeTestRule.onNodeWithTag("delay_increase_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(31, latestSettings.pauseDelaySeconds)
        composeTestRule.onNodeWithText("31s").assertIsDisplayed()

        // Disable Multi-Press -> Pause Delay only
        composeTestRule.onNodeWithTag("switch_multi_press").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(latestSettings.isMultiPressEnabled)
        assertEquals("31s pause delay before extending", latestSettings.activeModeSummary())
    }

    @Test
    fun settingsScreen_displaysLiveBuildVersionFooter() {
        assertEquals(52, BuildConfig.VERSION_CODE)
        assertEquals("1.2.0", BuildConfig.VERSION_NAME)

        composeTestRule.setContent {
            LimitPlusTheme {
                PermissionsScreen(
                    permissions = PermissionsStatus(
                        hasUsageAccess = true,
                        hasOverlayPermission = true,
                        hasBatteryOptimizationIgnored = true,
                        hasNotificationPermission = true
                    )
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_version_footer"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_version_footer").assertIsDisplayed()
        composeTestRule.onNodeWithText("v1.2.0 (Build 52)").assertIsDisplayed()
    }
}
