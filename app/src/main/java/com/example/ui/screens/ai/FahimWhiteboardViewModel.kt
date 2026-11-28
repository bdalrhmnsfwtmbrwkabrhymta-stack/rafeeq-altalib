package com.example.ui.screens.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.ai.FahimAudioEngine
import com.example.domain.ai.FahimDialect
import com.example.domain.ai.OpenMaicBoardSession
import com.example.domain.ai.OpenMaicPdfDocument
import com.example.domain.ai.OpenMaicPdfSummarizer
import com.example.domain.ai.WhiteboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel مستقل لسبورة فهيم الذكية (FahimWhiteboardViewModel):
 * - يبدأ دائماً بحالة [WhiteboardState.Empty] النظيفة بدون أي مسائل أو جداول وهمية مسبقة.
 * - يدير رفع ملفات PDF واختيار الصفحة عبر [OpenMaicPdfSummarizer] مع الشرح الصوتي الثنائي (مصري/فصحى).
 */
class FahimWhiteboardViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val summarizer = OpenMaicPdfSummarizer()
    val audioEngine = FahimAudioEngine(application)

    private val _whiteboardState = MutableStateFlow<WhiteboardState>(WhiteboardState.Empty)
    val whiteboardState: StateFlow<WhiteboardState> = _whiteboardState.asStateFlow()

    private val _pdfDocument = MutableStateFlow<OpenMaicPdfDocument?>(null)
    val pdfDocument: StateFlow<OpenMaicPdfDocument?> = _pdfDocument.asStateFlow()

    private val _selectedDialect = MutableStateFlow(FahimDialect.EGYPTIAN)
    val selectedDialect: StateFlow<FahimDialect> = _selectedDialect.asStateFlow()

    fun setDialect(dialect: FahimDialect, customGeminiKey: String = "") {
        _selectedDialect.value = dialect
        val current = _whiteboardState.value
        if (current is WhiteboardState.Active) {
            askQuestion(
                prompt = current.session.topicTitle,
                dialect = dialect,
                customGeminiKey = customGeminiKey,
                autoSpeak = true
            )
        }
    }

    fun askQuestion(
        prompt: String,
        dialect: FahimDialect = _selectedDialect.value,
        customGeminiKey: String = "",
        autoSpeak: Boolean = true,
        preferMindMap: Boolean = false
    ) {
        val clean = prompt.trim()
        if (clean.isBlank() && _pdfDocument.value == null && !preferMindMap) return

        viewModelScope.launch {
            _whiteboardState.value = WhiteboardState.Loading("فهيم يحلل السؤال ويرسم المخطط حياً على السبورة...")
            val result = summarizer.summarizeAndBuildSessionResult(
                topicOrQuestion = clean,
                dialect = dialect,
                pdfDoc = _pdfDocument.value,
                customGeminiKey = customGeminiKey,
                preferMindMap = preferMindMap
            )
            result.fold(
                onSuccess = { session ->
                    _whiteboardState.value = WhiteboardState.Active(session)
                    if (autoSpeak) {
                        audioEngine.speakLessonSteps(
                            steps = session.lesson.steps,
                            dialect = dialect
                        )
                    }
                },
                onFailure = { err ->
                    _whiteboardState.value = WhiteboardState.Error(
                        message = err.localizedMessage ?: "تعذّر الاتصال بالذكاء الاصطناعي. حاول مرة أخرى.",
                        retryPrompt = clean
                    )
                }
            )
        }
    }

    fun uploadPdfAndSummarize(
        uri: Uri,
        pageIndex: Int = 0,
        dialect: FahimDialect = _selectedDialect.value,
        customGeminiKey: String = ""
    ) {
        viewModelScope.launch {
            _whiteboardState.value = WhiteboardState.Loading("جارٍ قراءة كتاب PDF واستخلاص الصفحة...")
            val doc = summarizer.inspectPdfPage(getApplication(), uri, pageIndex)
            if (doc != null) {
                _pdfDocument.value = doc
                val pdfPrompt = "لخّص واشرح صفحة ${doc.currentPageIndex + 1} من ${doc.displayName}"
                _whiteboardState.value = WhiteboardState.Loading("وكلاء OpenMAIC يلخصون صفحة ${doc.currentPageIndex + 1}...")
                val result = summarizer.summarizeAndBuildSessionResult(
                    topicOrQuestion = pdfPrompt,
                    dialect = dialect,
                    pdfDoc = doc,
                    customGeminiKey = customGeminiKey
                )
                result.fold(
                    onSuccess = { session ->
                        _whiteboardState.value = WhiteboardState.Active(session)
                    },
                    onFailure = { err ->
                        _whiteboardState.value = WhiteboardState.Error(
                            message = err.localizedMessage ?: "تعذّر تلخيص الصفحة. حاول مرة أخرى.",
                            retryPrompt = pdfPrompt
                        )
                    }
                )
            } else {
                _whiteboardState.value = WhiteboardState.Empty
            }
        }
    }

    fun selectPdfPage(pageIndex: Int, customGeminiKey: String = "") {
        val doc = _pdfDocument.value ?: return
        val safePage = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        uploadPdfAndSummarize(Uri.parse(doc.uriString), safePage, _selectedDialect.value, customGeminiKey)
    }

    fun clearToEmpty() {
        audioEngine.stop()
        _pdfDocument.value = null
        _whiteboardState.value = WhiteboardState.Empty
    }

    fun markSessionSavedToTasks() {
        val current = _whiteboardState.value
        if (current is WhiteboardState.Active) {
            _whiteboardState.value = WhiteboardState.Active(current.session.copy(isSavedToTasks = true))
        }
    }

    override fun onCleared() {
        audioEngine.shutdown()
        super.onCleared()
    }
}
