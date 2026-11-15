package com.example

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import com.example.ui.screens.QuranScreen
import com.example.ui.screens.QuranTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranUiOverhaulTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quranHubRenders4UniqueTabsAndOpensFullScreenMushafForFatihah() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.loadQuranData()

        var isFullScreen = false

        composeTestRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var viewerOpen by remember { mutableStateOf(false) }
                    QuranScreen(
                        viewModel = viewModel,
                        initialTab = QuranTab.SURAHS,
                        onFullScreenViewerChanged = {
                            viewerOpen = it
                            isFullScreen = it
                        },
                        onNavigateBack = {}
                    )
                }
            }
        }

        // Wait briefly for coroutine loading of quran.json
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            viewModel.allSurahs.value.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify calligraphic header and 4 non-duplicated tabs
        composeTestRule.onNodeWithTag("quran_header_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quran_tab_surahs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quran_tab_juz").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quran_tab_audio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quran_tab_khatmah").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quran_continue_reading_button").assertIsDisplayed()

        // Switch to Juz tab
        composeTestRule.onNodeWithTag("quran_tab_juz").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quran_juz_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("الجزء الأول (الم)").assertIsDisplayed()

        // Switch to Audio tab
        composeTestRule.onNodeWithTag("quran_tab_audio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quran_audio_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reciter_chip_alafasy").assertIsDisplayed()

        // Switch back to Surahs tab, scroll to Al-Fatihah card, and open full-screen Mushaf
        composeTestRule.onNodeWithTag("quran_tab_surahs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("quran_surahs_list")
            .performScrollToNode(hasTestTag("surah_card_1"))
        composeTestRule.onNodeWithTag("surah_card_1").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Full-screen Mushaf should hide bottom navigation bar", isFullScreen)
        composeTestRule.waitUntil(timeoutMillis = 8000) {
            composeTestRule.onAllNodesWithTag("mushaf_viewer_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("mushaf_viewer_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_segment_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_page_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_zoom_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_index_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_bookmark_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mushaf_audio_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("صفحة ١ من ٦٠٤").assertIsDisplayed()
        composeTestRule.onNodeWithText("100%").assertIsDisplayed()

        // Exit Mushaf back to Hub and restore bottom navigation bar
        composeTestRule.onNodeWithTag("mushaf_back_button").performClick()
        composeTestRule.waitForIdle()
        assertFalse("Exiting Mushaf should restore bottom navigation bar", isFullScreen)
        composeTestRule.onNodeWithTag("quran_hub_root").assertIsDisplayed()

        // Clicking "+ متابعة القراءة" should also open full-screen Mushaf directly
        composeTestRule.onNodeWithTag("quran_surahs_list")
            .performScrollToNode(hasTestTag("quran_continue_reading_button"))
        composeTestRule.onNodeWithTag("quran_continue_reading_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(isFullScreen)
        composeTestRule.onNodeWithTag("mushaf_viewer_root").assertIsDisplayed()
    }
}
