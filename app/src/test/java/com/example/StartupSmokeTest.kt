package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupSmokeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStartsAndRendersUpgradedSplashThenTransitionsWithoutCrash() {
        val activity = composeTestRule.activity
        assertNotNull(activity)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_3d_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_brand_title", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("مُتعلِّم", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("منظمتك الدراسية والدينية الشاملة", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("المطور: عبد الرحمن صفوت عطا", useUnmergedTree = true).assertIsDisplayed()

        // Tap anywhere on splash skips immediately without crashing
        composeTestRule.onNodeWithTag("splash_screen_root").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen_root").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun mainActivityLandscapeSplashRendersCleanlyWithoutCrash() {
        val activity = composeTestRule.activity
        assertNotNull(activity)

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_3d_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_brand_title", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_developer_badge", useUnmergedTree = true).assertIsDisplayed()

        // Advance virtual clock past splash delay (1800ms)
        composeTestRule.mainClock.advanceTimeBy(2500L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun directActivitySetupCompletesWithoutException() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
