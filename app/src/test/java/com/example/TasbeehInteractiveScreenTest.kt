package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.islamic.TasbeehInteractiveScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasbeehInteractiveScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun clearPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("islamic_tasbeeh_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("tasbeeh_muted", true)
            .commit()
    }

    @Test
    fun sahihMuslim100CycleTransitionsMaterialsPhrasesAndShowsCompletion() {
        var backCalled = false
        composeTestRule.setContent {
            TasbeehInteractiveScreen(onBack = { backCalled = true })
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tasbeeh_hadith_badge").assertTextContains("صحيح مسلم ٥٩٧")
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("سُبْحَانَ اللَّهِ")
        composeTestRule.onNodeWithTag("tasbeeh_counters").assertTextContains("0 / 33  •  0 / 100")
        composeTestRule.onNodeWithTag("tasbeeh_material_badge").assertTextContains("اللازورد الأزرق (1/9)", substring = true)

        val tapSurface = composeTestRule.onNodeWithTag("tasbeeh_tap_surface")

        // 11 taps -> auto-switch material to (2/9) العقيق الأحمر
        repeat(11) { tapSurface.performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_counters").assertTextContains("11 / 33  •  11 / 100")
        composeTestRule.onNodeWithTag("tasbeeh_material_badge").assertTextContains("العقيق الأحمر (2/9)", substring = true)

        // Reach 33 -> transition into الْحَمْدُ لِلَّهِ phase (at 33..65)
        repeat(22) { tapSurface.performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("الْحَمْدُ لِلَّهِ")
        composeTestRule.onNodeWithTag("tasbeeh_counters").assertTextContains("0 / 33  •  33 / 100")

        // Reach 66 -> transition into اللَّهُ أَكْبَرُ phase
        repeat(33) { tapSurface.performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("اللَّهُ أَكْبَرُ")
        composeTestRule.onNodeWithTag("tasbeeh_counters").assertTextContains("0 / 33  •  66 / 100")

        // Reach 99 -> transition into 100th Tahleel
        repeat(33) { tapSurface.performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("لَا إِلَٰهَ إِلَّا اللَّهُ", substring = true)
        composeTestRule.onNodeWithTag("tasbeeh_material_badge").assertTextContains("الكريستال النقي (9/9)", substring = true)

        // 100th tap -> shows completion overlay with Hadith promise
        tapSurface.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_completion_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("تَقَبَّلَ اللَّهُ ذِكْرَكَ").assertIsDisplayed()
        composeTestRule.onNodeWithText("«غُفِرَتْ خَطَايَاهُ وَإِنْ كَانَتْ مِثْلَ زَبَدِ الْبَحْرِ»").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tasbeeh_grand_total").assertTextContains("الإجمالي: 100")

        // Restart new cycle
        composeTestRule.onNodeWithTag("tasbeeh_restart_cycle_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_counters").assertTextContains("0 / 33  •  0 / 100")
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("سُبْحَانَ اللَّهِ")

        // Verify mode switch & mute toggle don't crash
        composeTestRule.onNodeWithTag("tasbeeh_mode_free").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_active_phrase").assertTextContains("سبحان الله")
        composeTestRule.onNodeWithTag("tasbeeh_tap_surface").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tasbeeh_grand_total").assertTextContains("الإجمالي: 101")

        composeTestRule.onNodeWithTag("tasbeeh_reset_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(!backCalled)
    }
}
