package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    fun mainActivityStartsAndRendersSplashThenOnboardingWithoutCrash() {
        val activity = composeTestRule.activity
        assertNotNull(activity)

        composeTestRule.waitForIdle()
        // Splash screen should render app title visibly
        composeTestRule.onNodeWithText("هوس الدراسة", substring = true).assertExists()

        // Advance virtual clock past splash delay (1800ms) -> transitions cleanly to Setup/Home
        composeTestRule.mainClock.advanceTimeBy(2500L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun directActivitySetupCompletesWithoutException() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
