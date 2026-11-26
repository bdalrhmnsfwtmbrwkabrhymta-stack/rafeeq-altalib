package com.example.domain.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FahimAiEngineTest {

    @Test
    fun configHasDeveloperPinAndHiddenKeyResolution() {
        assertEquals("gemini-2.5-flash", FahimConfig.GEMINI_MODEL)
        assertEquals("2026", FahimConfig.DEVELOPER_CONTROL_PIN)
        assertEquals("custom_key_test", FahimConfig.resolveGeminiApiKey("  custom_key_test  "))
        assertEquals("https://piper.local/tts", FahimConfig.resolvePiperTtsUrl("https://piper.local/tts"))
        assertEquals("https://coqui.local/tts", FahimConfig.resolveCoquiTtsUrl("https://coqui.local/tts"))
        assertEquals("", FahimAiConfig.resolveEffectiveDeepSeekKey())
        assertFalse(FahimAiConfig.isDeepSeekConfigured())
    }

    @Test
    fun whiteboardEngineBuildsMultiDialectLessonsAndParsesJsonCleanly() {
        val engine = FahimBoardDrawingEngine()

        val egyptianLesson = engine.buildLocalWhiteboardLesson("اشرح مساحة الجزء المظلل", FahimDialect.EGYPTIAN)
        assertEquals("مساحة الجزء المظلل", egyptianLesson.title)
        assertEquals(WhiteboardGeometryScene.SHADED_CIRCLES_RECT, egyptianLesson.sceneType)
        assertTrue(egyptianLesson.steps.any { it.isFinalResult })
        assertTrue(egyptianLesson.finalSummaryText.contains("3.44"))

        val saudiLesson = engine.buildLocalWhiteboardLesson("نظرية فيثاغورس والمثلث القائم", FahimDialect.SAUDI)
        assertEquals(WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS, saudiLesson.sceneType)
        assertTrue(saudiLesson.steps.isNotEmpty())

        val jsonLesson = engine.parseWhiteboardLessonJson(
            rawJson = """
                {
                  "title": "اختبار الدائرة",
                  "sceneType": "CIRCLE_PROPERTIES",
                  "dimensionLabel": "d = 10",
                  "radiusLabel": "r = 5",
                  "finalSummaryText": "Area = 25π",
                  "steps": [
                    {"rightGivenText":"r = 5","leftOperationText":"π × 5²","centerFormulaText":"Area = 25π","speechLine":"مساحة الدائرة","isFinalResult":true}
                  ]
                }
            """.trimIndent(),
            fallbackPrompt = "دائرة",
            dialect = FahimDialect.FUSHA
        )
        assertNotNull(jsonLesson)
        assertEquals(WhiteboardGeometryScene.CIRCLE_PROPERTIES, jsonLesson!!.sceneType)
        assertEquals("Area = 25π", jsonLesson.finalSummaryText)
    }

    @Test
    fun parseReplyExtractsMindMapAndStudyTableJsonCleanly() {
        val service = FahimAiService()
        val raw = """
            أبشر! إليك خطة المذاكرة الشاملة لامتحان الغد:

            [MINDMAP_JSON]
            {"centerTitle":"امتحان الفيزياء","branches":[
              {"title":"القوانين","items":["قانون أوم","المقاومة النوعية"]},
              {"title":"المسائل","items":["توصيل توالي","توصيل توازي"]},
              {"title":"التعاريف","items":["شدة التيار","فرق الجهد"]},
              {"title":"المراجعة","items":["نماذج الوزارة","أفكار عليا"]}
            ]}
            [/MINDMAP_JSON]

            [STUDY_TABLE_JSON]
            {"title":"جدول امتحان الغد","columns":["الوقت","المهمة","المدة"],"rows":[
              {"time":"07:00 - 08:30","task":"فيزياء: الفصل الأول","duration":"ساعة ونصف","dayOfWeek":1,"start":"07:00","end":"08:30"},
              {"time":"09:00 - 10:30","task":"فيزياء: الفصل الثاني","duration":"ساعة ونصف","dayOfWeek":1,"start":"09:00","end":"10:30"}
            ]}
            [/STUDY_TABLE_JSON]
        """.trimIndent()

        val parsed = service.buildParsedFahimReply(
            rawText = raw,
            prompt = "جدول امتحان الغد",
            wantsMindMap = true,
            wantsStudySchedule = true,
            wantsComparisonTable = false
        )

        assertFalse(parsed.text.contains("[MINDMAP_JSON]"))
        assertFalse(parsed.text.contains("[STUDY_TABLE_JSON]"))
        assertNotNull(parsed.mindMap)
        assertEquals("امتحان الفيزياء", parsed.mindMap!!.centerTitle)
        assertEquals(4, parsed.mindMap!!.branches.size)

        assertNotNull(parsed.studyTable)
        assertEquals("جدول امتحان الغد", parsed.studyTable!!.title)
        assertEquals(2, parsed.studyTable!!.rows.size)
        assertFalse(parsed.studyTable!!.isComparisonOnly)
    }

    @Test
    fun askFahimAlwaysReturnsCleanStructuredReplyWithoutApiKeyWarning() = runBlocking {
        val service = FahimAiService()
        val reply = service.askFahim(
            prompt = "نظّم لي جدول مراجعة مكثف لامتحان الغد مع خريطة ذهنية"
        )

        assertFalse(reply.text.contains("مفتاح Gemini API"))
        assertFalse(reply.text.contains("API key", ignoreCase = true))
        assertNotNull(reply.mindMap)
        assertNotNull(reply.studyTable)
        assertTrue(reply.studyTable!!.rows.isNotEmpty())
    }

    @Test
    fun persistFahimTableToRoomInsertsScheduleSessionsAndTasks() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        val table = FahimStudyTableData(
            title = "جدول مراجعة الغد التجريبي",
            columnHeaders = listOf("الوقت", "المهمة", "المدة"),
            rows = listOf(
                FahimStudyTableRow("07:00 - 08:30", "فيزياء: قانون أوم", "ساعة ونصف", dayOfWeek = 2, startTime = "07:00", endTime = "08:30"),
                FahimStudyTableRow("09:00 - 10:30", "كيمياء: الاتزان", "ساعة ونصف", dayOfWeek = 2, startTime = "09:00", endTime = "10:30")
            ),
            isComparisonOnly = false
        )

        val inserted = vm.persistFahimTableToRoom(table)
        assertEquals(2, inserted)

        val allSessions = vm.db.scheduleDao().getAllSessions().first()
        val allTasks = vm.db.taskDao().getAllActiveTasks().first()

        assertTrue(allSessions.any { it.roomOrLink == "فهيم الذكي" && it.startTimeString == "07:00" })
        assertTrue(allTasks.any { it.tag == "جدول فهيم" && it.title.contains("فيزياء: قانون أوم") })
    }
}
