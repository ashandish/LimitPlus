package com.limitplus.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.limitplus.app.ui.overlay.OverlayPromptCard
import com.limitplus.app.ui.overlay.formatExtensionPressMessage
import com.limitplus.app.ui.theme.LimitPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OverlayMultiPressTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun formatExtensionPressMessage_formats_initial_and_remaining_messages() {
        assertEquals(
            "Extension #2 • Press two times to extend",
            formatExtensionPressMessage(requiredPresses = 2, tapsCompleted = 0)
        )
        assertEquals(
            "Press 1 more time to extend (1/2)",
            formatExtensionPressMessage(requiredPresses = 2, tapsCompleted = 1)
        )
        assertEquals(
            "Extension #3 • Press three times to extend",
            formatExtensionPressMessage(requiredPresses = 3, tapsCompleted = 0)
        )
        assertEquals(
            "Press 2 more times to extend (1/3)",
            formatExtensionPressMessage(requiredPresses = 3, tapsCompleted = 1)
        )
        assertEquals(
            "Press 1 more time to extend (2/3)",
            formatExtensionPressMessage(requiredPresses = 3, tapsCompleted = 2)
        )
        assertEquals(
            "Extension #4 • Press 4 times to extend",
            formatExtensionPressMessage(requiredPresses = 4, tapsCompleted = 0)
        )
    }

    @Test
    fun firstExtension_grantsOnSinglePressWithoutHint() {
        var grantedCount = 0
        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 0,
                    promptId = 1L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("extension_press_hint").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, grantedCount)
    }

    @Test
    fun secondExtension_requiresTwoPressesAndUpdatesMessage() {
        var grantedCount = 0
        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 1,
                    promptId = 2L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extension #2 • Press two times to extend").assertIsDisplayed()

        // First tap should not grant yet and should update hint
        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, grantedCount)
        composeTestRule.onNodeWithText("Press 1 more time to extend (1/2)").assertIsDisplayed()

        // Second tap grants extension
        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, grantedCount)
    }

    @Test
    fun thirdExtension_requiresThreePressesAndUpdatesMessage() {
        var grantedCount = 0
        composeTestRule.setContent {
            LimitPlusTheme {
                OverlayPromptCard(
                    packageName = "com.limitplus.sample.video",
                    appName = "VideoApp",
                    baselineMinutes = 20,
                    isRelaunch = false,
                    extensionsGrantedToday = 2,
                    promptId = 3L,
                    onAddFiveMinutes = { grantedCount++ },
                    onCloseApp = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Extension #3 • Press three times to extend").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, grantedCount)
        composeTestRule.onNodeWithText("Press 2 more times to extend (1/3)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, grantedCount)
        composeTestRule.onNodeWithText("Press 1 more time to extend (2/3)").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_minutes_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, grantedCount)
    }
}
