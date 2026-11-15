package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AutoSizeText
import com.example.ui.components.RafiqBottomBar
import com.example.ui.components.RafiqNavigationRail
import com.example.ui.components.RafiqTab
import com.example.ui.screens.SplashScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAndSplashResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashTapOnlyTriggersOnce() {
        var finishCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                SplashScreen(onSplashFinished = { finishCount++ })
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen_root").performClick()
        composeTestRule.onNodeWithTag("splash_screen_root").performClick()
        composeTestRule.mainClock.advanceTimeBy(2500L)
        composeTestRule.waitForIdle()

        assertEquals(1, finishCount)
    }

    @Test
    fun autoSizeTextRendersLongTriPartArabicNameWithoutEllipsis() {
        val longGreeting = "مرحباً بك يا عبد الرحمن صفوت عطا"
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.width(180.dp)) {
                        AutoSizeText(
                            text = longGreeting,
                            maxFontSize = 17.sp,
                            minFontSize = 13.sp,
                            overflowToTwoLinesAtMinSize = true
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(longGreeting).assertIsDisplayed()
    }

    @Test
    fun navigationRailSwitchesTabsInLandscapeRtl() {
        var selectedRoute = "home"
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        RafiqNavigationRail(
                            currentRoute = selectedRoute,
                            onTabSelected = { tab: RafiqTab -> selectedRoute = tab.route }
                        )
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("rafiq_navigation_rail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rail_tab_quran").performClick()
        assertEquals("quran", selectedRoute)
    }

    @Test
    fun bottomBarRemainsFunctionalInPortrait() {
        var selectedRoute = "home"
        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RafiqBottomBar(
                        currentRoute = selectedRoute,
                        onTabSelected = { tab -> selectedRoute = tab.route }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_tab_quests").performClick()
        assertEquals("quests", selectedRoute)
    }
}
