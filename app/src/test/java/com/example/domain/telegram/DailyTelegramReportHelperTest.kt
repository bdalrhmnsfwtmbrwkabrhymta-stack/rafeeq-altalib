package com.example.domain.telegram

import com.example.data.database.TaskEntity
import com.example.data.preferences.UserSettings
import org.junit.Assert.*
import org.junit.Test

class DailyTelegramReportHelperTest {

    @Test
    fun testSecureLinkingTokenGeneration() {
        val phone = "01007730570"
        val appUserId = "student_user_123"
        val token = DailyTelegramReportHelper.generateSecureLinkingToken(phone, appUserId)

        assertNotNull(token)
        assertTrue("Token should be at least 16 characters", token.length >= 16)
        assertFalse("Token must never contain raw phone number directly", token.contains(phone))
        assertFalse("Token must never contain raw appUserId directly", token.contains(appUserId))
    }

    @Test
    fun testDeepLinkUrlFormat() {
        val token = "abc123securetoken"
        val url = DailyTelegramReportHelper.getDeepLinkUrl(token)

        assertTrue(url.startsWith("https://t.me/RafeeqAlTalebBot?start="))
        assertTrue(url.endsWith(token))
    }

    @Test
    fun testArabicDayNames() {
        assertEquals("السبت", DailyTelegramReportHelper.getArabicDayName(1))
        assertEquals("الأحد", DailyTelegramReportHelper.getArabicDayName(2))
        assertEquals("الإثنين", DailyTelegramReportHelper.getArabicDayName(3))
        assertEquals("الثلاثاء", DailyTelegramReportHelper.getArabicDayName(4))
        assertEquals("الأربعاء", DailyTelegramReportHelper.getArabicDayName(5))
        assertEquals("الخميس", DailyTelegramReportHelper.getArabicDayName(6))
        assertEquals("الجمعة", DailyTelegramReportHelper.getArabicDayName(7))
    }

    @Test
    fun testGenerateDailyReportMessage() {
        val settings = UserSettings(
            userName = "أحمد محمد",
            dailyWaterCups = 6,
            dailyWaterGoalCups = 8
        )
        val tasks = listOf(
            TaskEntity(id = 1, title = "مذاكرة الفيزياء الحديثة", isCompleted = true),
            TaskEntity(id = 2, title = "حل تمارين التفاضل والتكامل", isCompleted = false)
        )

        val report = DailyTelegramReportHelper.generateDailyReportMessage(settings, tasks)
        assertNotNull(report)
        assertTrue(report.contains("أحمد محمد"))
        assertTrue(report.contains("مذاكرة الفيزياء الحديثة"))
        assertTrue(report.contains("حل تمارين التفاضل والتكامل"))
        assertTrue(report.contains("50%"))
        assertTrue(report.contains("6 من أصل 8 أكواب"))
    }

    @Test
    fun testAthkarMessageContainsDuaaAndSalawat() {
        val settings = UserSettings(userName = "سارة محمود")
        val athkar = DailyTelegramReportHelper.generateAthkarMessage(settings)

        assertNotNull(athkar)
        assertTrue(athkar.contains("سارة محمود"))
        assertTrue(athkar.contains("الصلاة الإبراهيمية"))
        assertTrue(athkar.contains("رفيق الطالب"))
    }
}
