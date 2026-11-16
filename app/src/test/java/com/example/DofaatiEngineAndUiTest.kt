package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.RafiqBottomBar
import com.hawas.study.dofaati.data.DofaatiDatabase
import com.hawas.study.dofaati.data.DofaatiLocalStore
import com.hawas.study.dofaati.engine.BulkQuestionGeneratorRepository
import com.hawas.study.dofaati.engine.LiveQuizScoringEngine
import com.hawas.study.dofaati.models.BulkGenerationProgress
import com.hawas.study.dofaati.models.DifficultyLevel
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.ui.screens.DofaatiNavigation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DofaatiEngineAndUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chunkedBulkGenerationProduces100UniqueQuestionsWithoutTruncation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BulkQuestionGeneratorRepository(context)
        val emissions: List<BulkGenerationProgress> = repo.generateBulkQuestionsInChunks(
            sourceText = "شدة التيار الكهربي وقانون أوم وتوصيل المقاومات توالي وتوازي",
            totalCount = 100,
            difficulties = setOf(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HIGHER_ORDER),
            types = setOf(QuestionType.MCQ, QuestionType.TRUE_FALSE),
            subjectName = "فيزيا"
        ).toList()

        assertTrue("Expected 4 chunk emissions for 100 questions", emissions.size == 4)
        val finalProgress = emissions.last()
        assertTrue(finalProgress.isCompleted)
        assertEquals(100, finalProgress.generatedCount)
        assertEquals(100, finalProgress.accumulatedQuestions.size)

        val distinctStems = finalProgress.accumulatedQuestions.map { it.questionText }.toSet()
        assertEquals(100, distinctStems.size)
    }

    @Test
    fun transcriptConverterSplitsInto5SegmentsFor50Questions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BulkQuestionGeneratorRepository(context)
        val generated = repo.convertAudioTranscriptTo50Questions(
            transcriptId = "lesson_5",
            transcriptText = "في هذا الدرس ندرس توصيل المقاومات على التوالي والتوازي ثم نطبق قانون أوم وحساب تيار الأفرع وحساب القدرة المستهلكة في كل فرع واختزال المقاومات المتماثلة في القنطرة المتزنة.",
            extractedLaws = listOf("Req = R1 + R2", "1/Req = 1/R1 + 1/R2"),
            examPoints = listOf("مراقبة القيم عند فتح المفتاح", "حساب تيار الفرع"),
            subjectName = "فيزيا"
        )

        assertEquals(50, generated.size)
        assertTrue(generated.first().questionText.contains("الدقيقة 0 إلى 12"))
        assertTrue(generated.last().questionText.contains("الدقيقة 48 إلى 60"))
    }

    @Test
    fun smartBulkPasteParserParsesNumberedAndInlineQuestions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BulkQuestionGeneratorRepository(context)
        val raw = """
            1- وحدة قياس شدة التيار الكهربي هي:
            أ) الفولت
            ب) الأمبير *
            ج) الأوم
            د) الوات

            2- عند توصيل مقاومتين متساويتين على التوازي تقل المقاومة للنصف؟
            أ) صح
            ب) خطأ
            الإجابة: أ

            3- يقاس فرق الجهد بوحدة: أ) الأمبير ب) الفولت ج) الأوم د) الكولوم
            الإجابة: ب
        """.trimIndent()

        val parsed = repo.parsePastedBulkQuestions(raw, "فيزيا")
        assertEquals(3, parsed.size)
        assertEquals(1, parsed[0].correctOptionIndex)
        assertEquals("الأمبير", parsed[0].options[1])
        assertEquals(0, parsed[1].correctOptionIndex)
        assertEquals(4, parsed[2].options.size)
        assertEquals(1, parsed[2].correctOptionIndex)
    }

    @Test
    fun liveQuizScoringFormulaMatchesExactSpec() {
        assertEquals(165, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = true, remainingSeconds = 13))
        assertEquals(100, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = true, remainingSeconds = 0))
        assertEquals(0, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = false, remainingSeconds = 15))

        val (podium, average) = LiveQuizScoringEngine.computeLeaderboardUpdate(
            studentName = "أنت",
            earnedPoints = 1050,
            totalQuestions = 10,
            correctAnswers = 10
        )
        assertEquals(3, podium.size)
        assertEquals(1, podium[0].rank)
        assertEquals("أنت", podium[0].name)
        assertTrue(average in 70..99)
    }

    @Test
    fun roomDatabaseSeeds500QuestionsAndPreservesDiagramQuestion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DofaatiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = DofaatiLocalStore(db.dofaatiDao())
            store.ensureSeeded()
            val seeded = store.observeQuestions().first()
            assertEquals(500, seeded.size)
            assertEquals("احسب المقاومة المكافئة للدائرة", seeded.first().questionText)
            assertEquals("SERIES_PARALLEL_3", seeded.first().circuitDiagramType)

            val extra = BulkQuestionGeneratorRepository(context).convertAudioTranscriptTo50Questions("t_1")
            store.appendQuestions(extra)
            val afterAppend = store.observeQuestions().first()
            assertEquals(550, afterAppend.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun bottomBarPaymentsTabRoutesToDofaati() {
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
        composeTestRule.onNodeWithTag("bottom_tab_dofaati").performClick()
        assertEquals("dofaati", selectedRoute)
    }

    @Test
    fun dofaatiHubAndSubScreensNavigateCleanlyInPortraitAndLandscape() {
        var isFullScreenReported = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 820.dp)) {
                    DofaatiNavigation(
                        onNavigateBackToApp = {},
                        onFullScreenChanged = { isFullScreenReported = it }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Screen 1 checks
        composeTestRule.onNodeWithText("دفعاتي").assertIsDisplayed()
        composeTestRule.onNodeWithText("تالتة ثانوي 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("4861 3927").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_batch_chat").assertIsDisplayed()

        // Navigate to Screen 7 via Exams tab
        composeTestRule.onNodeWithTag("hub_tab_exams").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_open_bulk_ai_generator").performClick()
        composeTestRule.waitForIdle()
        assertTrue(isFullScreenReported)
        composeTestRule.onNodeWithTag("bulk_ai_generator_root").assertIsDisplayed()

        // Back to Hub and open Screen 2 (Create Batch)
        composeTestRule.onNodeWithTag("btn_bulk_back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_create_batch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_batch_root").assertIsDisplayed()
    }

    @Test
    fun dofaatiHubRendersTwoColumnLayoutInLandscape() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 840.dp, height = 420.dp)) {
                    DofaatiNavigation(
                        onNavigateBackToApp = {},
                        onFullScreenChanged = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("active_batch_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dofaati_hub_tab_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("subject_card_sub_physics").assertIsDisplayed()
    }
}
