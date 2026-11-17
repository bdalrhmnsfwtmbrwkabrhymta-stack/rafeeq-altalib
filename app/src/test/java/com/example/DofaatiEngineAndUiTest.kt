package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import com.hawas.study.dofaati.models.DofaatiBatch
import com.hawas.study.dofaati.models.DofaatiBatchMember
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.DofaatiQuizAttempt
import com.hawas.study.dofaati.models.DofaatiScheduleEvent
import com.hawas.study.dofaati.models.DofaatiSubject
import com.hawas.study.dofaati.models.QuestionType
import com.hawas.study.dofaati.ui.screens.DofaatiNavigation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun chunkedBulkGenerationFailsHonestlyWithoutFakeQuestionsWhenBlankOrUnconfigured() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BulkQuestionGeneratorRepository(context)

        val blankEmissions: List<BulkGenerationProgress> = repo.generateBulkQuestionsInChunks(
            sourceText = " ",
            totalCount = 25,
            difficulties = setOf(DifficultyLevel.MEDIUM),
            types = setOf(QuestionType.MCQ),
            subjectName = "عام"
        ).toList()

        assertTrue(blankEmissions.isNotEmpty())
        val blankFinal = blankEmissions.last()
        assertTrue(blankFinal.isCompleted)
        assertEquals(0, blankFinal.accumulatedQuestions.size)
        assertNotNull(blankFinal.errorMessage)

        val noKeyEmissions = repo.generateBulkQuestionsInChunks(
            sourceText = "قوانين الحركة والسرعة والعجلة",
            totalCount = 20,
            difficulties = setOf(DifficultyLevel.EASY),
            types = setOf(QuestionType.MCQ),
            subjectName = "فيزياء"
        ).toList()

        val finalProgress = noKeyEmissions.last()
        assertTrue(finalProgress.isCompleted)
        assertTrue(finalProgress.accumulatedQuestions.isEmpty())
        assertNotNull(finalProgress.errorMessage)
    }

    @Test
    fun smartBulkPasteParserParsesFormattedQuestionsAndNeverFabricatesGarbage() = runBlocking {
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

        val parsed = repo.parsePastedBulkQuestions(raw, "فيزيا", allowAiFallback = false)
        assertEquals(3, parsed.size)
        assertEquals(1, parsed[0].correctOptionIndex)
        assertEquals("الأمبير", parsed[0].options[1])
        assertEquals(0, parsed[1].correctOptionIndex)
        assertEquals(4, parsed[2].options.size)
        assertEquals(1, parsed[2].correctOptionIndex)

        val unformatted = repo.parsePastedBulkQuestions("مجرد سطر عشوائي بدون أسئلة أو اختيارات", "عام", allowAiFallback = false)
        assertTrue("Parser must not fabricate placeholder options for unformatted prose", unformatted.isEmpty())
    }

    @Test
    fun liveQuizScoringFormulaAndRealAttemptsLeaderboardAreAccurate() {
        assertEquals(165, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = true, remainingSeconds = 13))
        assertEquals(100, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = true, remainingSeconds = 0))
        assertEquals(0, LiveQuizScoringEngine.calculateQuestionScore(isCorrect = false, remainingSeconds = 15))

        val (emptyTop, emptyAvg) = LiveQuizScoringEngine.computeLeaderboardFromAttempts(emptyList())
        assertTrue(emptyTop.isEmpty())
        assertEquals(0, emptyAvg)

        val realAttempts = listOf(
            DofaatiQuizAttempt("a1", "كويز 1", "أحمد", 5, 10, 50, 500, 100L),
            DofaatiQuizAttempt("a2", "كويز 1", "سارة", 10, 10, 100, 1200, 200L)
        )
        val (podium, average) = LiveQuizScoringEngine.computeLeaderboardFromAttempts(realAttempts)
        assertEquals(2, podium.size)
        assertEquals(1, podium[0].rank)
        assertEquals("سارة", podium[0].name)
        assertEquals(100, podium[0].scorePercent)
        assertEquals(2, podium[1].rank)
        assertEquals("أحمد", podium[1].name)
        assertEquals(75, average)
    }

    @Test
    fun freshRoomDatabaseStartsEmptyAndPersistsRealUserEntities() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DofaatiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = DofaatiLocalStore(db.dofaatiDao())

            assertTrue(store.observeBatches().first().isEmpty())
            assertTrue(store.observeQuestions().first().isEmpty())
            assertTrue(store.observeFlashcards().first().isEmpty())
            assertTrue(store.observeAudioLessons().first().isEmpty())
            assertTrue(store.observeStudyFiles().first().isEmpty())
            assertTrue(store.observeScheduleEvents().first().isEmpty())
            assertTrue(store.observeQuizAttempts().first().isEmpty())

            val batch = DofaatiBatch(
                id = "b_test",
                name = "دفعة الطب",
                code = "1234 5678",
                logoColorHex = 0xFF1A6FE3L,
                adminName = "كريم",
                studentsCount = 1,
                subjects = listOf(DofaatiSubject(id = "sub_anatomy", batchId = "b_test", name = "تشريح"))
            )
            val admin = DofaatiBatchMember(
                id = "mem_1",
                batchId = "b_test",
                name = "كريم",
                roleLabel = "مشرف الدفعة",
                isAdmin = true
            )
            store.saveBatchWithInitialSetup(batch, admin)
            val savedBatches = store.observeBatches().first()
            assertEquals(1, savedBatches.size)
            assertEquals("دفعة الطب", savedBatches.first().name)
            assertEquals("تشريح", savedBatches.first().subjects.first().name)

            val question = DofaatiQuestionItem(
                id = "q_1",
                questionNumber = 1,
                subjectName = "تشريح",
                questionText = "ما أكبر عظمة في جسم الإنسان؟",
                questionType = QuestionType.MCQ,
                difficulty = DifficultyLevel.EASY,
                options = listOf("الفخذ", "العضد", "الزند", "القص"),
                correctOptionIndex = 0,
                detailedExplanation = "عظمة الفخذ هي الأطول والأقوى.",
                sourceTag = "مضاف يدوياً"
            )
            store.appendQuestions(listOf(question))
            assertEquals(1, store.observeQuestions().first().size)

            store.insertQuizAttempt(
                DofaatiQuizAttempt("att_1", "اختبار تشريح", "كريم", 1, 1, 100, 150, System.currentTimeMillis())
            )
            assertEquals(1, store.observeQuizAttempts().first().size)

            store.upsertScheduleEvent(
                DofaatiScheduleEvent("ev_1", "غداً 10 ص", "تشريح", "مراجعة عملية", true, System.currentTimeMillis() + 86_400_000L)
            )
            assertEquals(1, store.observeScheduleEvents().first().size)
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
    fun dofaatiHubStartsCleanEmptyAndNavigatesToSubScreens() {
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

        composeTestRule.onNodeWithText("دفعاتي").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_batch_welcome_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("لا توجد دفعات مضافة بعد").assertIsDisplayed()

        composeTestRule.onNodeWithTag("hub_tab_exams").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dofaati_hub_root").performScrollToNode(hasTestTag("btn_open_bulk_ai_generator"))
        composeTestRule.onNodeWithTag("btn_open_bulk_ai_generator").performClick()
        composeTestRule.waitForIdle()
        assertTrue(isFullScreenReported)
        composeTestRule.onNodeWithTag("bulk_ai_generator_root").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_bulk_back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dofaati_hub_root").performScrollToNode(hasTestTag("btn_create_batch"))
        composeTestRule.onNodeWithTag("btn_create_batch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_batch_screen").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h480dp-land")
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
        composeTestRule.onNodeWithTag("empty_batch_welcome_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dofaati_hub_tab_bar").assertIsDisplayed()
    }
}
