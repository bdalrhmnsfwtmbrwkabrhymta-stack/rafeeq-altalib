package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.download.HawasApplication
import com.example.ui.components.StartupCrashStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = HawasApplication::class)
class StartupCrashDiagnosticTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun splashTransitionToSetupAndHomeDoesNotCrash() {
        composeRule.mainClock.autoAdvance = false

        // Frame 1: Splash screen appears cleanly
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()

        // Advance past SplashScreen 1800ms delay -> transitions to RafeeqSetupFlowScreen
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()

        // Advance past the 2200ms post-splash permission/FGS timer too
        composeRule.mainClock.advanceTimeBy(2500)
        composeRule.waitForIdle()

        // Transition from RafeeqSetupFlowScreen -> FirstLaunchLocationSheet + HomeScreen
        val skipNodes = composeRule.onAllNodesWithText("تخطي للتجربة ⚡", substring = true).fetchSemanticsNodes()
        if (skipNodes.isNotEmpty()) {
            composeRule.onAllNodesWithText("تخطي للتجربة ⚡", substring = true)[0].performClick()
            composeRule.mainClock.advanceTimeBy(1500)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun startupCrashStorePersistsAndClearsReports() {
        val context = ApplicationProvider.getApplicationContext<HawasApplication>()
        StartupCrashStore.clearLastCrash(context)
        assertNull(StartupCrashStore.getLastCrash(context))

        StartupCrashStore.recordCrash(
            context = context,
            threadName = "main",
            throwable = IllegalStateException("Test startup failure"),
            stage = "SplashTransition"
        )

        val stored = StartupCrashStore.getLastCrash(context)
        assertNotNull(stored)
        assertEquals("SplashTransition", stored?.stage)
        assertEquals("Test startup failure", stored?.message)

        StartupCrashStore.clearLastCrash(context)
        assertNull(StartupCrashStore.getLastCrash(context))
    }
}
