package com.example.domain.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
        assertEquals("gemini-2.5-flash", FahimConfig.PRIMARY_MODEL)
        assertEquals("gemini-2.0-flash", FahimConfig.FALLBACK_MODEL)
        assertEquals("gemini-2.5-flash", FahimConfig.GEMINI_MODEL)
        assertEquals("2026", FahimConfig.DEVELOPER_CONTROL_PIN)
        assertEquals("custom_key_test", FahimConfig.resolveGeminiApiKey("  \"custom_key_test\"\n "))
        assertEquals("https://piper.local/tts", FahimConfig.resolvePiperTtsUrl("https://piper.local/tts"))
        assertEquals("https://coqui.local/tts", FahimConfig.resolveCoquiTtsUrl("https://coqui.local/tts"))
    }

    @Test
    fun dialectsAreStrictlyLimitedToEgyptianAndFusha() {
        assertEquals(
            listOf(FahimDialect.EGYPTIAN, FahimDialect.FUSHA),
            FahimDialect.entries
        )
        assertEquals("مصري", FahimDialect.EGYPTIAN.labelAr)
        assertEquals("فصحى", FahimDialect.FUSHA.labelAr)
        assertEquals(FahimDialect.EGYPTIAN, FahimDialect.fromName("SAUDI"))
        assertEquals(FahimDialect.FUSHA, FahimDialect.fromName("FUSHA"))
    }

    @Test
    fun geminiClientTrimsKeyFallsBackOn404AndStripsMarkdownFences() = runBlocking {
        val capturedUrls = mutableListOf<String>()
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            capturedUrls.add(url)
            assertEquals("application/json", req.header("Content-Type"))

            if (url.contains("/models/${FahimConfig.PRIMARY_MODEL}:generateContent")) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("""{"error":{"message":"Model not found"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        {"candidates":[{"content":{"parts":[{"text":"```json\n{\"ok\":true}\n```"}]}}]}
                        """.trimIndent().toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
        }
        val client = FahimGeminiClient(
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        )

        val result = client.generateContent(
            prompt = "اختبار",
            apiKey = "  AIzaSyTestKey123 \n "
        )
        assertTrue(result.isSuccess)
        val rawText = result.getOrNull().orEmpty()
        assertEquals("{\"ok\":true}", FahimGeminiClient.stripMarkdownJsonFences(rawText))

        assertEquals(2, capturedUrls.size)
        assertTrue(capturedUrls[0].contains("key=AIzaSyTestKey123"))
        assertTrue(capturedUrls[0].contains("/models/gemini-2.5-flash:generateContent"))
        assertTrue(capturedUrls[1].contains("/models/gemini-2.0-flash:generateContent"))

        val testConn = client.testConnection("  AIzaSyTestKey123  ")
        assertTrue(testConn.isSuccess)
        assertEquals("gemini-2.0-flash", testConn.modelUsed)
    }

    @Test
    fun whiteboardEngineBuildsDualDialectLessonsAndParsesJsonCleanly() {
        val engine = FahimBoardDrawingEngine()

        val egyptianLesson = engine.buildLocalWhiteboardLesson("اشرح مساحة الجزء المظلل", FahimDialect.EGYPTIAN)
        assertEquals("مساحة الجزء المظلل", egyptianLesson.title)
        assertEquals(WhiteboardGeometryScene.SHADED_CIRCLES_RECT, egyptianLesson.sceneType)
        assertTrue(egyptianLesson.steps.any { it.isFinalResult })
        assertTrue(egyptianLesson.finalSummaryText.contains("3.44"))

        val fushaLesson = engine.buildLocalWhiteboardLesson("نظرية فيثاغورس والمثلث القائم", FahimDialect.FUSHA)
        assertEquals(WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS, fushaLesson.sceneType)
        assertTrue(fushaLesson.steps.isNotEmpty())

        val jsonLesson = engine.parseWhiteboardLessonJson(
            rawJson = """
                ```json
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
                ```
            """.trimIndent(),
            fallbackPrompt = "دائرة",
            dialect = FahimDialect.FUSHA
        )
        assertNotNull(jsonLesson)
        assertEquals(WhiteboardGeometryScene.CIRCLE_PROPERTIES, jsonLesson!!.sceneType)
        assertEquals("Area = 25π", jsonLesson.finalSummaryText)
    }

    @Test
    fun openMaicEngineOrchestratesMultiAgentSessionWithMindMapAndStudyTable() = runBlocking {
        val engine = OpenMaicPdfBoardEngine()
        val fakeDoc = OpenMaicPdfDocument(
            uriString = "content://example/book.pdf",
            displayName = "الفيزياء_الثالث_الثانوي.pdf",
            pageCount = 12,
            currentPageIndex = 2,
            extractedPageText = "قانون نيوتن الثاني: القوة المحصلة تساوي الكتلة مضروبة في العجلة"
        )

        val session = engine.buildLocalOpenMaicSession(
            topic = "شرح قانون نيوتن الثاني",
            dialect = FahimDialect.EGYPTIAN,
            pdfDoc = fakeDoc
        )

        assertTrue(session.topicTitle.contains("الفيزياء_الثالث_الثانوي"))
        assertEquals("الفيزياء_الثالث_الثانوي.pdf", session.sourcePdfName)
        assertEquals(3, session.sourcePageNumber)
        assertTrue(session.lesson.steps.isNotEmpty())
        assertTrue(session.mindMap.branches.size >= 4)
        assertTrue(session.studyTable.rows.isNotEmpty())
        assertTrue(session.agentSummaryText.contains("OpenMAIC"))
    }

    @Test
    fun audioEngineSupportsGenderSpeedAndMathCleaning() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val audioEngine = FahimAudioEngine(app)

        assertEquals(FahimVoiceGender.MALE, audioEngine.voiceGender.value)
        assertEquals(1.0f, audioEngine.speechRate.value, 0.001f)

        audioEngine.setVoiceGender(FahimVoiceGender.FEMALE)
        assertEquals(FahimVoiceGender.FEMALE, audioEngine.voiceGender.value)

        val nextRate = audioEngine.cycleSpeechRate()
        assertEquals(1.25f, nextRate, 0.001f)

        val spoken = audioEngine.cleanMathForArabicSpeech("Area_shaded = 4 × π ≈ 12.56")
        assertTrue(spoken.contains("المساحة المظللة"))
        assertTrue(spoken.contains("ضرب"))
        assertTrue(spoken.contains("يساوي"))
        audioEngine.shutdown()
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
