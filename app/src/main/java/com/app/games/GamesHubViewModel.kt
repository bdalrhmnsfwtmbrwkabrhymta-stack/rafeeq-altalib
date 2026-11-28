package com.app.games

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GamesHubUiState(
    val selectedGrade: HighSchoolGrade = HighSchoolGrade.GRADE_3,
    val selectedSubject: HighSchoolSubject = HighSchoolSubject.PHYSICS,
    val selectedChapter: String = HighSchoolSubject.PHYSICS.chapters.first(),
    val selectedStageNumber: Int = 1,
    val customPdfTitle: String? = null,
    val customPdfQuestions: List<GameQuestion> = emptyList(),
    val isUsingCustomPdf: Boolean = false,
    val isGeneratingQuestions: Boolean = false,
    val statusBannerMessage: String? = null,
    val activeRunStageNumber: Int? = null,
    val activeRunQuestions: List<GameQuestion> = emptyList()
)

class GamesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameShopRepository(application)

    val shopState: StateFlow<GameShopState> = repository.shopStateFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GameShopState()
    )

    private val _uiState = MutableStateFlow(GamesHubUiState())
    val uiState: StateFlow<GamesHubUiState> = _uiState.asStateFlow()

    private val askedPromptHistory = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            shopState.collect { shop ->
                _uiState.update { current ->
                    val clampedStage = current.selectedStageNumber.coerceIn(1, shop.unlockedStage)
                    current.copy(selectedStageNumber = if (current.selectedStageNumber == 1) shop.unlockedStage else clampedStage)
                }
            }
        }
    }

    fun selectGrade(grade: HighSchoolGrade) {
        _uiState.update { it.copy(selectedGrade = grade, isUsingCustomPdf = false) }
    }

    fun selectSubject(subject: HighSchoolSubject) {
        _uiState.update {
            it.copy(
                selectedSubject = subject,
                selectedChapter = subject.chapters.first(),
                isUsingCustomPdf = false
            )
        }
    }

    fun selectChapter(chapter: String) {
        _uiState.update { it.copy(selectedChapter = chapter, isUsingCustomPdf = false) }
    }

    fun selectStage(stageNumber: Int) {
        val unlocked = shopState.value.unlockedStage
        if (stageNumber in 1..unlocked) {
            _uiState.update { it.copy(selectedStageNumber = stageNumber) }
        } else {
            showStatus("أكمل المرحلة ${stageNumber - 1} أولاً لفتح هذه المرحلة 🔒")
        }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    private fun showStatus(msg: String) {
        _uiState.update { it.copy(statusBannerMessage = msg) }
    }

    fun buyOrEquipCharacter(character: GameCharacterId) {
        viewModelScope.launch {
            val (_, message) = repository.purchaseOrEquipCharacter(character)
            showStatus(message)
        }
    }

    fun upgradePowerUp(type: PowerUpType) {
        viewModelScope.launch {
            val (_, message) = repository.upgradePowerUp(type)
            showStatus(message)
        }
    }

    fun importPdfForGame(uri: Uri) {
        viewModelScope.launch {
            val fileTitle = resolveDisplayName(uri) ?: "ملف دراسي.pdf"
            _uiState.update {
                it.copy(
                    isGeneratingQuestions = true,
                    statusBannerMessage = "جارٍ تحليل «$fileTitle» وتجهيز بوابات الأسئلة..."
                )
            }
            val extracted = HighSchoolCurriculumBank.generateQuestionsFromPdf(
                context = getApplication(),
                pdfUri = uri,
                fileTitle = fileTitle
            )
            if (extracted.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        customPdfTitle = fileTitle,
                        customPdfQuestions = extracted,
                        isUsingCustomPdf = true,
                        isGeneratingQuestions = false,
                        statusBannerMessage = "تم استخراج ${extracted.size} أسئلة من «$fileTitle» — اضغط العب فوراً! 🚀"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isGeneratingQuestions = false,
                        statusBannerMessage = "تعذّر قراءة نص هذا الـ PDF (قد يكون مصوّراً بدون نص). جرب ملفاً نصياً أو العب من المنهج المدمج."
                    )
                }
            }
        }
    }

    fun clearCustomPdf() {
        _uiState.update {
            it.copy(
                isUsingCustomPdf = false,
                statusBannerMessage = "تم الرجوع إلى شجرة مناهج الثانوية العامة المدمجة."
            )
        }
    }

    /**
     * يطلق المرحلة المحددة فوراً ببنك المنهج الأوفلاين (أو ملف الـ PDF) مع توليد أسئلة متجددة من Gemini في الخلفية!
     */
    fun startStage(stageNumber: Int = _uiState.value.selectedStageNumber) {
        val unlocked = shopState.value.unlockedStage
        val safeStage = stageNumber.coerceIn(1, unlocked)
        val state = _uiState.value

        val initialPool = if (state.isUsingCustomPdf && state.customPdfQuestions.isNotEmpty()) {
            state.customPdfQuestions
        } else {
            HighSchoolCurriculumBank.getOfflineQuestions(
                grade = state.selectedGrade,
                subject = state.selectedSubject,
                chapter = state.selectedChapter
            )
        }

        val stageSpec = StageThemeEngine.stageFor(safeStage)
        val orderedPool = buildRunQuestionDeck(initialPool, stageSpec.standardGatesCount)

        _uiState.update {
            it.copy(
                selectedStageNumber = safeStage,
                activeRunStageNumber = safeStage,
                activeRunQuestions = orderedPool
            )
        }

        // Background Gemini refresh for fresh, non-repeating questions on upcoming gates (when not playing a custom PDF)
        if (!state.isUsingCustomPdf) {
            viewModelScope.launch {
                val aiQuestions = HighSchoolCurriculumBank.generateFreshQuestionsWithAi(
                    grade = state.selectedGrade,
                    subject = state.selectedSubject,
                    chapter = state.selectedChapter,
                    stageSpec = stageSpec,
                    excludePrompts = askedPromptHistory.toSet()
                )
                if (aiQuestions.isNotEmpty()) {
                    _uiState.update { current ->
                        if (current.activeRunStageNumber == safeStage) {
                            current.copy(activeRunQuestions = buildRunQuestionDeck(aiQuestions + initialPool, stageSpec.standardGatesCount))
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun exitActiveRun() {
        _uiState.update { it.copy(activeRunStageNumber = null) }
    }

    suspend fun finishRunAndPersist(
        stageNumber: Int,
        victory: Boolean,
        trackCoins: Int,
        score: Int,
        correctAnswers: Int,
        wrongAnswers: Int,
        maxCombo: Int
    ): RunSummaryResult {
        val spec = StageThemeEngine.stageFor(stageNumber)
        val bossBonus = if (victory) spec.bossRewardCoins else 0
        val totalCoinsAdded = trackCoins + bossBonus

        val newlyUnlocked = repository.recordRunOutcome(
            stageNumber = stageNumber,
            victory = victory,
            totalCoinsAdded = totalCoinsAdded,
            score = score,
            correctAnswers = correctAnswers,
            maxCombo = maxCombo
        )

        if (newlyUnlocked != null) {
            _uiState.update { it.copy(selectedStageNumber = newlyUnlocked) }
        }

        return RunSummaryResult(
            stageNumber = stageNumber,
            victory = victory,
            coinsEarnedFromTrack = trackCoins,
            bossChestBonus = bossBonus,
            totalCoinsAdded = totalCoinsAdded,
            score = score,
            correctAnswers = correctAnswers,
            wrongAnswers = wrongAnswers,
            maxCombo = maxCombo,
            newlyUnlockedStage = newlyUnlocked,
            chestNameAr = spec.chestNameAr,
            bonusPowerUpGranted = if (victory) spec.bonusPowerUp else null
        )
    }

    fun markQuestionAsked(prompt: String) {
        if (askedPromptHistory.size > 120) askedPromptHistory.clear()
        askedPromptHistory.add(prompt)
    }

    private fun buildRunQuestionDeck(raw: List<GameQuestion>, standardGatesCount: Int): List<GameQuestion> {
        val shuffledRaw = raw.map { HighSchoolCurriculumBank.shuffleQuestionOptions(it) }
        val normal = shuffledRaw.filterNot { it.isBossQuestion }.shuffled()
        val boss = shuffledRaw.filter { it.isBossQuestion }.shuffled()
        val fallbackNormal = if (normal.isEmpty()) shuffledRaw else normal
        val combined = mutableListOf<GameQuestion>()
        while (combined.size < standardGatesCount && fallbackNormal.isNotEmpty()) {
            combined.addAll(fallbackNormal.map { HighSchoolCurriculumBank.shuffleQuestionOptions(it) })
        }
        val firstStandard = combined.take(standardGatesCount)
        val bossQuestion = boss.firstOrNull()
            ?: fallbackNormal.lastOrNull()?.copy(isBossQuestion = true)
        return buildList {
            addAll(firstStandard)
            if (bossQuestion != null) add(HighSchoolCurriculumBank.shuffleQuestionOptions(bossQuestion))
            addAll(fallbackNormal.shuffled().map { HighSchoolCurriculumBank.shuffleQuestionOptions(it) })
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }
}
