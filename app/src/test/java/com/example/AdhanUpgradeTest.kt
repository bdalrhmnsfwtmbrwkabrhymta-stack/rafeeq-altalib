package com.example

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.example.data.audio.AzanAudioPlayer
import com.example.data.preferences.UserSettings
import com.example.domain.prayer.AdhanAudioDownloaderManager
import com.example.domain.prayer.AdhkarAutoTriggerManager
import com.example.domain.prayer.AdhkarWindow
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.prayer.PrayerDataConstants
import com.example.ui.prayer.AdhanAudioSelectorScreen
import com.example.ui.prayer.AutoAdhkarScreen
import com.example.ui.prayer.PrayerAlertsSettingsScreen
import com.example.ui.prayer.PrayerTimeVisualTheme
import com.example.ui.prayer.RealisticAnimatedAdhanScreen
import com.example.ui.prayer.resolvePrayerVisualTheme
import com.example.ui.theme.RafiqTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdhanUpgradeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun domainDefaultsEnforceThreeStudioVoicesAndManualSunriseOff() {
        val defaults = UserSettings()
        assertFalse(defaults.isSunriseAlertEnabled)
        assertTrue(defaults.isAutoUpdateHighQualityAudio)
        assertEquals("الأذان المصري الأصيل (MBC وإذاعة القرآن)", defaults.muezzinVoice)

        // Exactly 3 core studio voices offline & downloadable packs
        assertEquals(3, PrayerDataConstants.MUEZZIN_VOICES.size)
        assertEquals(3, AdhanAudioDownloaderManager.ONLINE_MUEZZIN_LIST.size)
        assertEquals(
            listOf("egypt_mbc_hq", "makkah_hq", "madinah_hq"),
            PrayerDataConstants.MUEZZIN_VOICES.map { it.id }
        )

        // Sunrise strictly skipped unless manually enabled
        assertFalse(PrayerAlarmScheduler.shouldSchedulePrayer("sunrise", emptySet(), false))
        assertTrue(PrayerAlarmScheduler.shouldSchedulePrayer("sunrise", emptySet(), true))

        // All 5 obligatory prayers active by default
        listOf("fajr", "dhuhr", "asr", "maghrib", "isha").forEach { id ->
            assertTrue(PrayerAlarmScheduler.shouldSchedulePrayer(id, emptySet(), false))
        }
        assertFalse(PrayerAlarmScheduler.shouldSchedulePrayer("fajr", setOf("fajr"), true))
    }

    @Test
    fun adhkarAutoTriggerDetectsDynamicFajrAndAsrWindows() {
        val fajrMs = 1_000_000L
        val sunriseMs = 2_000_000L
        val dhuhrMs = 5_000_000L
        val asrMs = 8_000_000L
        val maghribMs = 10_000_000L

        assertEquals(
            AdhkarWindow.MORNING,
            AdhkarAutoTriggerManager.detectActiveAdhkarWindow(
                fajrMs, sunriseMs, dhuhrMs, asrMs, maghribMs,
                nowMs = fajrMs + 60_000L
            )
        )
        assertEquals(
            AdhkarWindow.NONE,
            AdhkarAutoTriggerManager.detectActiveAdhkarWindow(
                fajrMs, sunriseMs, dhuhrMs, asrMs, maghribMs,
                nowMs = sunriseMs + 1_000L
            )
        )
        assertEquals(
            AdhkarWindow.NONE,
            AdhkarAutoTriggerManager.detectActiveAdhkarWindow(
                fajrMs, sunriseMs, dhuhrMs, asrMs, maghribMs,
                nowMs = dhuhrMs + 60_000L
            )
        )
        assertEquals(
            AdhkarWindow.EVENING,
            AdhkarAutoTriggerManager.detectActiveAdhkarWindow(
                fajrMs, sunriseMs, dhuhrMs, asrMs, maghribMs,
                nowMs = asrMs + 60_000L
            )
        )
        assertEquals(
            AdhkarWindow.NONE,
            AdhkarAutoTriggerManager.detectActiveAdhkarWindow(
                fajrMs, sunriseMs, dhuhrMs, asrMs, maghribMs,
                nowMs = maghribMs + 1_000L
            )
        )
    }

    @Test
    fun prayerAlertsScreenShowsFiveObligatoryOnAndSunriseManuallyOff() {
        composeTestRule.setContent {
            RafiqTheme {
                var sunriseOn by remember { mutableStateOf(false) }
                PrayerAlertsSettingsScreen(
                    prayerTimes = null,
                    mutedPrayers = emptySet(),
                    isSunriseAlertEnabled = sunriseOn,
                    onTogglePrayerMute = {},
                    onToggleSunriseAlert = { sunriseOn = it },
                    showTopBar = true
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("prayer_alerts_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("الشروق 06:18 ص — مغلق افتراضياً (فعّله يدوياً)", useUnmergedTree = true)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("prayer_alert_switch_fajr").assertIsOn()
        composeTestRule.onNodeWithTag("sunrise_alert_switch").assertIsOff()
        composeTestRule.onNodeWithTag("prayer_alert_switch_dhuhr").assertIsOn()
        composeTestRule.onNodeWithTag("prayer_alert_switch_asr").assertIsOn()
        composeTestRule.onNodeWithTag("prayer_alert_switch_maghrib").assertIsOn()
        composeTestRule.onNodeWithTag("prayer_alert_switch_isha").assertIsOn()

        // Manual opt-in
        composeTestRule.onNodeWithTag("sunrise_alert_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sunrise_alert_switch").assertIsOn()
    }

    @Test
    fun adhanAudioSelectorRendersOnlyThreeStudioCardsAndAutoUpdateToggle() {
        val player = AzanAudioPlayer(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            RafiqTheme {
                var selectedVoice by remember {
                    mutableStateOf("الأذان المصري الأصيل (MBC وإذاعة القرآن)")
                }
                var autoUpdate by remember { mutableStateOf(true) }
                var previewId by remember { mutableStateOf<String?>(null) }

                AdhanAudioSelectorScreen(
                    selectedVoiceName = selectedVoice,
                    isAutoUpdateHighQualityAudio = autoUpdate,
                    playingVoiceId = previewId,
                    azanAudioPlayer = player,
                    onSelectVoice = { selectedVoice = it },
                    onPreviewVoiceChange = { previewId = it },
                    onToggleAutoUpdate = { autoUpdate = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("adhan_audio_selector_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_voice_card_egypt_mbc_hq").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_voice_card_makkah_hq").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_voice_card_madinah_hq").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("auto_update_hq_switch").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("auto_update_hq_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auto_update_hq_switch").assertIsOff()
    }

    @Test
    fun realisticAnimatedAdhanRendersSunriseWithMorningAdhkarCtaAndAllSixRealMosqueSlots() {
        assertEquals(PrayerTimeVisualTheme.FAJR, resolvePrayerVisualTheme("الفجر"))
        assertEquals(PrayerTimeVisualTheme.SUNRISE, resolvePrayerVisualTheme("الشروق"))
        assertEquals(PrayerTimeVisualTheme.DHUHR_ASR, resolvePrayerVisualTheme("العصر"))
        assertEquals(PrayerTimeVisualTheme.MAGHRIB_ISHA, resolvePrayerVisualTheme("المغرب"))

        assertEquals(R.drawable.bg_mosque_fajr, com.example.ui.prayer.resolvePrayerBackgroundSlot("الفجر").photoRes)
        assertEquals(R.drawable.bg_mosque_sunrise, com.example.ui.prayer.resolvePrayerBackgroundSlot("الشروق").photoRes)
        assertEquals(R.drawable.bg_mosque_dhuhr, com.example.ui.prayer.resolvePrayerBackgroundSlot("الظهر").photoRes)
        assertEquals(R.drawable.bg_mosque_asr, com.example.ui.prayer.resolvePrayerBackgroundSlot("العصر").photoRes)
        assertEquals(R.drawable.bg_mosque_maghrib, com.example.ui.prayer.resolvePrayerBackgroundSlot("المغرب").photoRes)
        assertEquals(R.drawable.bg_mosque_isha, com.example.ui.prayer.resolvePrayerBackgroundSlot("العشاء").photoRes)

        var morningClicked = false
        var qiblaClicked = false
        var stoppedBySwipe = false
        composeTestRule.setContent {
            RafiqTheme {
                var prayer by remember { mutableStateOf("الشروق") }
                RealisticAnimatedAdhanScreen(
                    prayerName = prayer,
                    cityName = "القاهرة",
                    muezzinVoice = "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
                    formattedClockTime = "06:18",
                    formattedAmPm = "ص",
                    onOpenQibla = { qiblaClicked = true },
                    onOpenNearbyMosques = {},
                    onOpenMorningAdhkar = { morningClicked = true },
                    onStopAdhan = {
                        stoppedBySwipe = true
                        prayer = "الفجر"
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("realistic_adhan_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_unified_glass_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_prayer_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_qibla_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_nearby_mosques_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_swipe_dismiss_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhan_swipe_thumb").assertIsDisplayed()

        composeTestRule.onNodeWithTag("sunrise_morning_adhkar_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sunrise_morning_adhkar_button").performClick()
        composeTestRule.onNodeWithTag("adhan_qibla_button").performClick()

        // Drag thumb right-to-left (from its start position on the right toward the left end)
        composeTestRule.onNodeWithTag("adhan_swipe_thumb").performTouchInput {
            swipeLeft(startX = centerX, endX = -220f, durationMillis = 300)
        }
        composeTestRule.waitForIdle()
        assertTrue(morningClicked)
        assertTrue(qiblaClicked)
        assertTrue(stoppedBySwipe)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun realisticAdhanAndAutoAdhkarRenderCleanlyInLandscapeAndAdvanceCounter() {
        val player = AzanAudioPlayer(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            RafiqTheme {
                AutoAdhkarScreen(
                    azanAudioPlayer = player,
                    initialWindow = AdhkarWindow.MORNING
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("auto_adhkar_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_adhkar_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adhkar_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 3", useUnmergedTree = true).assertIsDisplayed()

        // Tap 1 -> 2 / 3
        composeTestRule.onNodeWithTag("adhkar_tasbeeh_counter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 / 3", useUnmergedTree = true).assertIsDisplayed()

        // Tap 2 -> 3 / 3
        composeTestRule.onNodeWithTag("adhkar_tasbeeh_counter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3 / 3", useUnmergedTree = true).assertIsDisplayed()

        // Tap 3 -> completes 3/3 and auto-advances to the next dhikr
        composeTestRule.onNodeWithTag("adhkar_tasbeeh_counter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("الذكر 2 من 7", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun exactMinuteAlignmentAndCleanAdhanHeaderMatchUserSpec() {
        val sampleExactMs = 1_700_000_040_321L
        assertEquals(
            "alignToExactMinute must preserve exact milliseconds without shifting",
            sampleExactMs,
            com.example.data.prayer.PrayerTimeCalculator.alignToExactMinute(sampleExactMs)
        )

        val fixedDate = java.util.Date(1_732_536_000_000L)
        val rawAdhanTimes = com.example.data.prayer.PrayerTimeCalculator.getPrayerTimes(
            latitude = 30.0444,
            longitude = 31.2357,
            date = fixedDate,
            method = com.batoulapps.adhan.CalculationMethod.EGYPTIAN,
            madhab = com.batoulapps.adhan.Madhab.SHAFI
        )!!
        val calcResult = com.example.data.prayer.PrayerTimeCalculator.calculatePrayerTimes(
            latitude = 30.0444,
            longitude = 31.2357,
            date = fixedDate,
            methodName = "EGYPTIAN",
            isEnglish = false
        )
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.fajr!!.time), calcResult.fajr.timestampMillis)
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.sunrise!!.time), calcResult.sunrise.timestampMillis)
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.dhuhr!!.time), calcResult.dhuhr.timestampMillis)
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.asr!!.time), calcResult.asr.timestampMillis)
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.maghrib!!.time), calcResult.maghrib.timestampMillis)
        assertEquals(com.example.data.prayer.PrayerTimeCalculator.stripSubSecondJitter(rawAdhanTimes.isha!!.time), calcResult.isha.timestampMillis)

        val dailySchedule = com.example.domain.prayer.AdhanPrayerService.calculateDailySchedule(
            latitude = 30.0444,
            longitude = 31.2357,
            date = fixedDate,
            methodName = "EGYPTIAN",
            isEnglish = false
        )
        assertEquals(calcResult.fajr.timestampMillis, dailySchedule.fajr.timestampMs)
        assertEquals(calcResult.sunrise.timestampMillis, dailySchedule.sunrise.timestampMs)
        assertEquals(calcResult.dhuhr.timestampMillis, dailySchedule.dhuhr.timestampMs)
        assertEquals(calcResult.asr.timestampMillis, dailySchedule.asr.timestampMs)
        assertEquals(calcResult.maghrib.timestampMillis, dailySchedule.maghrib.timestampMs)
        assertEquals(calcResult.isha.timestampMillis, dailySchedule.isha.timestampMs)

        // Verify Adhkar trigger does not skip upcoming events by an artificial 15s buffer
        val fajrPlus5Min = calcResult.fajr.timestampMillis + 5 * 60 * 1000L
        assertEquals(
            fajrPlus5Min,
            AdhkarAutoTriggerManager.computeNextMorningTriggerMs(
                todayResult = calcResult,
                tomorrowResult = calcResult,
                nowMs = fajrPlus5Min - 1_000L
            )
        )

        composeTestRule.setContent {
            RafiqTheme {
                RealisticAnimatedAdhanScreen(
                    prayerName = "العصر",
                    cityName = "عزب بسنتاواي",
                    muezzinVoice = "الأذان المصري الأصيل (MBC وإذاعة القرآن)",
                    formattedClockTime = "02:38",
                    formattedAmPm = "م",
                    onOpenQibla = {},
                    onOpenNearbyMosques = {},
                    onOpenMorningAdhkar = {},
                    onStopAdhan = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("حان الآن موعد", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("أذان العصر", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "«اللهم ربَّ هذه الدعوة التامة، والصلاة القائمة،\nآتِ محمدًا الوسيلة والفضيلة،\nوابعثه مقامًا محمودًا الذي وعدته»",
            useUnmergedTree = true
        ).assertIsDisplayed()
    }
}
