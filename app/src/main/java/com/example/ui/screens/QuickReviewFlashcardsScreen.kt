package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.FlashcardDeckEntity
import com.example.domain.anki.QuickFlashcardsEngine
import com.example.domain.anki.QuickReviewCard
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class QuickReviewScreenState {
    SETUP,
    GENERATING,
    PLAYER,
    SESSION_SUMMARY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReviewFlashcardsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDeckManager: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val userSettings by viewModel.userSettings.collectAsState(initial = null)
    val isEnglish = userSettings?.appLanguage == "en"
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

    // State Machine
    var screenState by remember { mutableStateOf(QuickReviewScreenState.SETUP) }

    // Setup configuration
    var selectedCardCount by remember { mutableIntStateOf(25) }
    var selectedSourceType by remember { mutableStateOf("TEXT") } // "CAMERA", "FILE", "TEXT", "LIBRARY"
    var customPastedText by remember { mutableStateOf("") }
    var customInstructions by remember { mutableStateOf("") }
    var currentDeckTitle by remember { mutableStateOf("مراجعة سريعة") }

    // Media & Files
    var capturedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    // Generation state
    var isGenerating by remember { mutableStateOf(false) }
    var generationProgressMessage by remember { mutableStateOf("جاري استخراج وتحليل المحتوى...") }
    var generationErrorMessage by remember { mutableStateOf<String?>(null) }

    // Review Player state
    var generatedCards by remember { mutableStateOf<List<QuickReviewCard>>(emptyList()) }
    var currentCardIndex by remember { mutableIntStateOf(0) }
    var isCardFlipped by remember { mutableStateOf(false) }
    var savedSessionDeckTitle by remember { mutableStateOf("") }

    // Dialogs & Sheets
    var showSourceSelectionSheet by remember { mutableStateOf(false) }
    var showLibraryPickerSheet by remember { mutableStateOf(false) }
    var showHistoryStatsDialog by remember { mutableStateOf(false) }
    var showSaveToDeckDialog by remember { mutableStateOf(false) }

    // Review stats
    var againCount by remember { mutableIntStateOf(0) }
    var hardCount by remember { mutableIntStateOf(0) }
    var goodCount by remember { mutableIntStateOf(0) }
    var easyCount by remember { mutableIntStateOf(0) }

    // Initialize TTS
    LaunchedEffect(Unit) {
        AnkiTTS.init(context)
    }

    // Camera Launchers
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { previewBitmap: Bitmap? ->
        if (previewBitmap != null) {
            capturedBitmaps = capturedBitmaps + previewBitmap
            selectedSourceType = "CAMERA"
            Toast.makeText(context, if (isEnglish) "Photo captured successfully" else "تم التقاط الصفحة بنجاح 📸", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null && tempPhotoFile!!.exists() && tempPhotoFile!!.length() > 0) {
            val bitmap = BitmapFactory.decodeFile(tempPhotoFile!!.absolutePath)
            if (bitmap != null) {
                capturedBitmaps = capturedBitmaps + bitmap
                selectedSourceType = "CAMERA"
                Toast.makeText(context, if (isEnglish) "Photo captured successfully" else "تم التقاط الصفحة بنجاح 📸", Toast.LENGTH_SHORT).show()
            } else {
                cameraPreviewLauncher.launch(null)
            }
        } else {
            cameraPreviewLauncher.launch(null)
        }
    }

    fun launchCamera() {
        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPermission) {
            try {
                val file = ImageStorageHelper.createTempImageFile(context)
                tempPhotoFile = file
                val uri = ImageStorageHelper.getUriForFile(context, file)
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                cameraPreviewLauncher.launch(null)
            }
        } else {
            Toast.makeText(context, if (isEnglish) "Camera permission required" else "يرجى منح إذن الكاميرا لتصوير صفحات الكتاب", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, if (isEnglish) "Camera permission denied" else "تم رفض إذن الكاميرا", Toast.LENGTH_SHORT).show()
        }
    }

    // Document / File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment?.substringAfterLast("/") ?: "ملف دراسي"
            selectedSourceType = "FILE"
            Toast.makeText(context, if (isEnglish) "File selected: $selectedFileName" else "تم اختيار الملف: $selectedFileName 📄", Toast.LENGTH_SHORT).show()
        }
    }

    // Multi-Image Gallery Picker
    val multipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val loadedBitmaps = mutableListOf<Bitmap>()
            for (u in uris) {
                try {
                    context.contentResolver.openInputStream(u)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) loadedBitmaps.add(bmp)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (loadedBitmaps.isNotEmpty()) {
                capturedBitmaps = capturedBitmaps + loadedBitmaps
                selectedSourceType = "CAMERA"
                Toast.makeText(context, if (isEnglish) "${loadedBitmaps.size} photos loaded" else "تم استيراد ${loadedBitmaps.size} صور من المعرض 🖼️", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Generate Flashcards Function
    fun triggerGeneration() {
        if (selectedSourceType == "TEXT" && customPastedText.isBlank()) {
            Toast.makeText(context, if (isEnglish) "Please paste or type study text" else "يرجى لصق أو كتابة النص الدراسي أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedSourceType == "CAMERA" && capturedBitmaps.isEmpty()) {
            Toast.makeText(context, if (isEnglish) "Please capture or choose textbook photos" else "يرجى تصوير أو اختيار صورة واحدة على الأقل من كتابك", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedSourceType == "FILE" && selectedFileUri == null) {
            Toast.makeText(context, if (isEnglish) "Please select a PDF or Document" else "يرجى اختيار ملف PDF أو مستند دراسي", Toast.LENGTH_SHORT).show()
            return
        }

        screenState = QuickReviewScreenState.GENERATING
        isGenerating = true
        generationProgressMessage = "جاري قراءة واستخراج المحتوى بالذكاء الاصطناعي..."
        generationErrorMessage = null

        coroutineScope.launch {
            try {
                // Step 1: Content extraction
                generationProgressMessage = "جاري تحليل المفاهيم، القوانين والتعريفات الأساسية..."
                
                // Step 2: AI Call
                val result = QuickFlashcardsEngine.generateFlashcards(
                    context = context,
                    sourceType = selectedSourceType,
                    rawText = customPastedText,
                    fileUri = selectedFileUri,
                    bitmapList = capturedBitmaps,
                    cardCount = selectedCardCount,
                    customInstructions = customInstructions,
                    deckTitle = currentDeckTitle.ifBlank { "مراجعة سريعة" },
                    customApiKey = userSettings?.geminiApiKey ?: ""
                )

                if (result.isSuccess && result.getOrNull()?.isNotEmpty() == true) {
                    generatedCards = result.getOrNull()!!
                    currentCardIndex = 0
                    isCardFlipped = false
                    againCount = 0
                    hardCount = 0
                    goodCount = 0
                    easyCount = 0
                    savedSessionDeckTitle = currentDeckTitle.ifBlank { "مراجعة سريعة - ${generatedCards.size} بطاقة" }
                    screenState = QuickReviewScreenState.PLAYER
                    viewModel.awardGamificationXp(25, "توليد بطاقات احفظ بسرعة")
                } else {
                    generationErrorMessage = "تعذر توليد البطاقات. سيتم استخدام حزمة الاستذكار الاحتياطية."
                    generatedCards = QuickFlashcardsEngine.generateFallbackFlashcards(
                        customPastedText,
                        currentDeckTitle,
                        selectedCardCount
                    )
                    currentCardIndex = 0
                    screenState = QuickReviewScreenState.PLAYER
                }
            } catch (e: Exception) {
                generationErrorMessage = e.localizedMessage ?: "حدث خطأ أثناء معالجة المحتوى"
                generatedCards = QuickFlashcardsEngine.generateFallbackFlashcards(
                    customPastedText,
                    currentDeckTitle,
                    selectedCardCount
                )
                currentCardIndex = 0
                screenState = QuickReviewScreenState.PLAYER
            } finally {
                isGenerating = false
            }
        }
    }

    // Handle Back Press
    BackHandler {
        when (screenState) {
            QuickReviewScreenState.SETUP -> onNavigateBack()
            QuickReviewScreenState.GENERATING -> screenState = QuickReviewScreenState.SETUP
            QuickReviewScreenState.PLAYER -> screenState = QuickReviewScreenState.SETUP
            QuickReviewScreenState.SESSION_SUMMARY -> screenState = QuickReviewScreenState.SETUP
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screenState) {
                QuickReviewScreenState.SETUP -> {
                    QuickReviewSetupView(
                        isEnglish = isEnglish,
                        selectedCardCount = selectedCardCount,
                        onCardCountSelected = { selectedCardCount = it },
                        selectedSourceType = selectedSourceType,
                        customPastedText = customPastedText,
                        onCustomPastedTextChanged = { customPastedText = it },
                        customInstructions = customInstructions,
                        onCustomInstructionsChanged = { customInstructions = it },
                        currentDeckTitle = currentDeckTitle,
                        onDeckTitleChanged = { currentDeckTitle = it },
                        capturedPhotosCount = capturedBitmaps.size,
                        selectedFileName = selectedFileName,
                        onOpenSourceSheet = { showSourceSelectionSheet = true },
                        onOpenCamera = {
                            val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasCamPermission) launchCamera() else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        onOpenGallery = {
                            multipleImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onOpenFilePicker = {
                            filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                        },
                        onOpenLibrary = { showLibraryPickerSheet = true },
                        onClearPhotos = { capturedBitmaps = emptyList() },
                        onClearFile = { selectedFileUri = null; selectedFileName = "" },
                        onStartGenerating = { triggerGeneration() },
                        onNavigateBack = onNavigateBack,
                        onOpenHistory = { showHistoryStatsDialog = true },
                        onNavigateToDeckManager = onNavigateToDeckManager
                    )
                }

                QuickReviewScreenState.GENERATING -> {
                    QuickReviewLoadingView(
                        progressMessage = generationProgressMessage,
                        cardCount = selectedCardCount,
                        onCancel = { screenState = QuickReviewScreenState.SETUP }
                    )
                }

                QuickReviewScreenState.PLAYER -> {
                    if (generatedCards.isNotEmpty() && currentCardIndex < generatedCards.size) {
                        val currentCard = generatedCards[currentCardIndex]
                        QuickReviewPlayerView(
                            isEnglish = isEnglish,
                            cards = generatedCards,
                            currentIndex = currentCardIndex,
                            currentCard = currentCard,
                            isFlipped = isCardFlipped,
                            onFlipCard = { isCardFlipped = !isCardFlipped },
                            onNextCard = {
                                if (currentCardIndex + 1 < generatedCards.size) {
                                    currentCardIndex++
                                    isCardFlipped = false
                                } else {
                                    screenState = QuickReviewScreenState.SESSION_SUMMARY
                                }
                            },
                            onPreviousCard = {
                                if (currentCardIndex > 0) {
                                    currentCardIndex--
                                    isCardFlipped = false
                                }
                            },
                            onRateCard = { rating ->
                                currentCard.masteryScore = rating
                                when (rating) {
                                    1 -> againCount++
                                    2 -> hardCount++
                                    3 -> goodCount++
                                    4 -> easyCount++
                                }
                                viewModel.awardGamificationXp(if (rating >= 3) 15 else 5, "مراجعة بطاقة فلاش كارد")
                                if (currentCardIndex + 1 < generatedCards.size) {
                                    currentCardIndex++
                                    isCardFlipped = false
                                } else {
                                    screenState = QuickReviewScreenState.SESSION_SUMMARY
                                }
                            },
                            onToggleBookmark = {
                                currentCard.isBookmarked = !currentCard.isBookmarked
                            },
                            onSpeakText = { textToSpeak ->
                                AnkiTTS.speak(textToSpeak)
                            },
                            onSaveToLibrary = {
                                showSaveToDeckDialog = true
                            },
                            onClosePlayer = {
                                screenState = QuickReviewScreenState.SETUP
                            }
                        )
                    } else {
                        QuickReviewSummaryView(
                            isEnglish = isEnglish,
                            totalCards = generatedCards.size,
                            againCount = againCount,
                            hardCount = hardCount,
                            goodCount = goodCount,
                            easyCount = easyCount,
                            onRestartAll = {
                                currentCardIndex = 0
                                isCardFlipped = false
                                againCount = 0
                                hardCount = 0
                                goodCount = 0
                                easyCount = 0
                                screenState = QuickReviewScreenState.PLAYER
                            },
                            onReviewUnmastered = {
                                val unmastered = generatedCards.filter { it.masteryScore <= 2 }
                                if (unmastered.isNotEmpty()) {
                                    generatedCards = unmastered
                                    currentCardIndex = 0
                                    isCardFlipped = false
                                    screenState = QuickReviewScreenState.PLAYER
                                } else {
                                    Toast.makeText(context, if (isEnglish) "All cards mastered! 🎉" else "أحسنت! جميع البطاقات متقنة بنسبة 100% 🌟", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSaveDeck = {
                                showSaveToDeckDialog = true
                            },
                            onDone = {
                                screenState = QuickReviewScreenState.SETUP
                            }
                        )
                    }
                }

                QuickReviewScreenState.SESSION_SUMMARY -> {
                    QuickReviewSummaryView(
                        isEnglish = isEnglish,
                        totalCards = generatedCards.size,
                        againCount = againCount,
                        hardCount = hardCount,
                        goodCount = goodCount,
                        easyCount = easyCount,
                        onRestartAll = {
                            currentCardIndex = 0
                            isCardFlipped = false
                            againCount = 0
                            hardCount = 0
                            goodCount = 0
                            easyCount = 0
                            screenState = QuickReviewScreenState.PLAYER
                        },
                        onReviewUnmastered = {
                            val unmastered = generatedCards.filter { it.masteryScore <= 2 }
                            if (unmastered.isNotEmpty()) {
                                generatedCards = unmastered
                                currentCardIndex = 0
                                isCardFlipped = false
                                screenState = QuickReviewScreenState.PLAYER
                            } else {
                                Toast.makeText(context, if (isEnglish) "All cards mastered! 🎉" else "أحسنت! جميع البطاقات متقنة بنسبة 100% 🌟", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSaveDeck = {
                            showSaveToDeckDialog = true
                        },
                        onDone = {
                            screenState = QuickReviewScreenState.SETUP
                        }
                    )
                }
            }

            // Bottom Sheet for Source Selection
            if (showSourceSelectionSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSourceSelectionSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    SourceSelectionSheetContent(
                        isEnglish = isEnglish,
                        onSelectCamera = {
                            showSourceSelectionSheet = false
                            val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasCam) launchCamera() else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        onSelectGallery = {
                            showSourceSelectionSheet = false
                            multipleImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onSelectFile = {
                            showSourceSelectionSheet = false
                            filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                        },
                        onSelectPasteText = {
                            showSourceSelectionSheet = false
                            selectedSourceType = "TEXT"
                        },
                        onSelectLibrary = {
                            showSourceSelectionSheet = false
                            showLibraryPickerSheet = true
                        }
                    )
                }
            }

            // Bottom Sheet for Saved Library Items Picker
            if (showLibraryPickerSheet) {
                val allDocs by viewModel.documents.collectAsState()
                val allNotes by viewModel.notes.collectAsState()

                ModalBottomSheet(
                    onDismissRequest = { showLibraryPickerSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Choose from Your Library" else "اختر من مستنداتك وملاحظاتك 📚",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (allDocs.isEmpty() && allNotes.isEmpty()) {
                            Text(
                                text = if (isEnglish) "No saved documents or notes found." else "لا توجد مستندات أو ملاحظات محفوظة في مكتبتك بعد.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allDocs) { doc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                customPastedText = doc.extractedText.ifBlank { doc.importantPointsJson }
                                                currentDeckTitle = doc.title
                                                selectedSourceType = "TEXT"
                                                showLibraryPickerSheet = false
                                                Toast.makeText(context, "تم اختيار: ${doc.title}", Toast.LENGTH_SHORT).show()
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Text("مستند دراسي • ${doc.extractedText.length} حرف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                items(allNotes) { note ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                customPastedText = note.content
                                                currentDeckTitle = note.title
                                                selectedSourceType = "TEXT"
                                                showLibraryPickerSheet = false
                                                Toast.makeText(context, "تم اختيار: ${note.title}", Toast.LENGTH_SHORT).show()
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.EditNote, null, tint = Color(0xFF10B981))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Text("ملاحظة ملخصة • ${note.content.length} حرف", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save to Permanent Decks Dialog
            if (showSaveToDeckDialog) {
                var saveTitle by remember { mutableStateOf(savedSessionDeckTitle.ifBlank { "حزمة مراجعة سريعة" }) }
                var saveDesc by remember { mutableStateOf("تم التوليد بواسطة محرك احفظ بسرعة بالذكاء الاصطناعي") }

                AlertDialog(
                    onDismissRequest = { showSaveToDeckDialog = false },
                    title = { Text(if (isEnglish) "Save to Flashcard Decks" else "حفظ الحزمة في مكتبة البطاقات الدائمة 💾") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (isEnglish) "This deck will be saved permanently for daily Spaced Repetition (Anki SM-2)." else "سيتم حفظ هذه الحزمة في قاعدة بيانات التطبيق لتتمكن من مراجعتها بنظام التكرار المتباعد الذكي يومياً.")
                            OutlinedTextField(
                                value = saveTitle,
                                onValueChange = { saveTitle = it },
                                label = { Text(if (isEnglish) "Deck Title" else "عنوان الحزمة") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = saveDesc,
                                onValueChange = { saveDesc = it },
                                label = { Text(if (isEnglish) "Description" else "الوصف (اختياري)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (saveTitle.isNotBlank()) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val newDeck = FlashcardDeckEntity(
                                            title = saveTitle.trim(),
                                            description = saveDesc.trim(),
                                            cardCount = generatedCards.size
                                        )
                                        val deckId = viewModel.db.flashcardDao().insertDeck(newDeck)
                                        val entities = QuickFlashcardsEngine.convertToFlashcardEntities(generatedCards, deckId)
                                        viewModel.db.flashcardDao().insertCards(entities)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "تم حفظ الحزمة في مكتبتك بنجاح ✅", Toast.LENGTH_SHORT).show()
                                            showSaveToDeckDialog = false
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(if (isEnglish) "Save" else "حفظ في مكتبتي")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveToDeckDialog = false }) {
                            Text(if (isEnglish) "Cancel" else "إلغاء")
                        }
                    }
                )
            }

            // History & Quick Stats Dialog
            if (showHistoryStatsDialog) {
                QuickStatsHistoryDialog(
                    isEnglish = isEnglish,
                    onDismiss = { showHistoryStatsDialog = false },
                    onNavigateToDeckManager = {
                        showHistoryStatsDialog = false
                        onNavigateToDeckManager()
                    }
                )
            }
        }
    }
}

/**
 * 1. Pixel-Perfect Setup Screen Matching Design & Screenshots
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReviewSetupView(
    isEnglish: Boolean,
    selectedCardCount: Int,
    onCardCountSelected: (Int) -> Unit,
    selectedSourceType: String,
    customPastedText: String,
    onCustomPastedTextChanged: (String) -> Unit,
    customInstructions: String,
    onCustomInstructionsChanged: (String) -> Unit,
    currentDeckTitle: String,
    onDeckTitleChanged: (String) -> Unit,
    capturedPhotosCount: Int,
    selectedFileName: String,
    onOpenSourceSheet: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onOpenLibrary: () -> Unit,
    onClearPhotos: () -> Unit,
    onClearFile: () -> Unit,
    onStartGenerating: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onNavigateToDeckManager: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEnglish) "Fast Recall" else "احفظ بسرعة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Flashcards for Quick Review" else "فلاش كاردز للمراجعة السريعة",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.QueryStats, contentDescription = "History & Stats", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToDeckManager) {
                        Icon(Icons.Default.Layers, contentDescription = "All Decks", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prominent Hero Instructional Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFFFF7A00), Color(0xFFFF9E40))
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEnglish) "Fast Active Recall" else "استذكار فائق السرعة ⚡",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isEnglish) "Instant AI Q&A Card Generation" else "توليد كروت ذكية وسريعة بضغطة واحدة",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Exactly matching Arabic prompt instruction
                        Text(
                            text = if (isEnglish)
                                "Choose your source — snap textbook pages, upload a PDF, or paste text — leave the rest to us! We'll extract Q&A flashcards you can flip and master in minutes."
                            else
                                "اختر مصدرك — صور صفحاتك أو ارفع PDF أو الصق النص — وسيب الباقي علينا — هنطلعلك كروت سؤال وإجابة تقلبها وتحفظها في دقايق.",
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Card Count Selector (15, 25, 40)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Number of Flashcards" else "عدد البطاقات المطلوبة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "$selectedCardCount ${if (isEnglish) "Cards" else "بطاقة"}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CardCountOption(
                            count = 15,
                            label = if (isEnglish) "15 (Quick)" else "15 كارت",
                            subtitle = if (isEnglish) "Quick Quiz" else "مراجعة سريعة",
                            isSelected = selectedCardCount == 15,
                            modifier = Modifier.weight(1f),
                            onClick = { onCardCountSelected(15) }
                        )

                        CardCountOption(
                            count = 25,
                            label = if (isEnglish) "25 (Standard)" else "25 كارت",
                            subtitle = if (isEnglish) "Balanced" else "شامل ومتوازن",
                            badge = if (isEnglish) "POPULAR" else "الأكثر طلباً",
                            isSelected = selectedCardCount == 25,
                            modifier = Modifier.weight(1f),
                            onClick = { onCardCountSelected(25) }
                        )

                        CardCountOption(
                            count = 40,
                            label = if (isEnglish) "40 (Deep)" else "40 كارت",
                            subtitle = if (isEnglish) "Full Topic" else "تغطية كاملة",
                            isSelected = selectedCardCount == 40,
                            modifier = Modifier.weight(1f),
                            onClick = { onCardCountSelected(40) }
                        )
                    }
                }
            }

            // Deck Title / Subject Name Input
            OutlinedTextField(
                value = currentDeckTitle,
                onValueChange = onDeckTitleChanged,
                label = { Text(if (isEnglish) "Topic / Subject Title" else "عنوان الموضوع / اسم المادة") },
                placeholder = { Text(if (isEnglish) "e.g. Biology - Genetics Chapter" else "مثال: أحياء - الوراثة والجينات") },
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Multi-Source Selection Grid Cards
            Text(
                text = if (isEnglish) "Select Study Source" else "اختر مصدر المحتوى الدراسي:",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Scan Photos
                SourceSelectorTile(
                    icon = Icons.Default.CameraAlt,
                    title = if (isEnglish) "Scan Pages" else "صور صفحاتك",
                    desc = if (capturedPhotosCount > 0) "$capturedPhotosCount صور جاهزة" else "كاميرا / معرض",
                    isActive = selectedSourceType == "CAMERA" || capturedPhotosCount > 0,
                    activeColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCamera
                )

                // 2. Upload File (PDF / Docx)
                SourceSelectorTile(
                    icon = Icons.Default.UploadFile,
                    title = if (isEnglish) "Upload File" else "ارفع ملف",
                    desc = if (selectedFileName.isNotBlank()) selectedFileName.take(12) + "..." else "PDF أو Word",
                    isActive = selectedSourceType == "FILE" || selectedFileName.isNotBlank(),
                    activeColor = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFilePicker
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3. Paste Text
                SourceSelectorTile(
                    icon = Icons.Default.ContentPaste,
                    title = if (isEnglish) "Paste Text" else "الصق النص",
                    desc = if (customPastedText.isNotBlank()) "${customPastedText.length} حرف" else "نص مخصص",
                    isActive = selectedSourceType == "TEXT" && customPastedText.isNotBlank(),
                    activeColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Quick Paste from clipboard
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onCustomPastedTextChanged(clip)
                            Toast.makeText(context, "تم لصق النص من الحافظة 📋", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // 4. From Library
                SourceSelectorTile(
                    icon = Icons.Default.FolderSpecial,
                    title = if (isEnglish) "From Library" else "من مكتبتك",
                    desc = "مستندات وملاحظات",
                    isActive = false,
                    activeColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLibrary
                )
            }

            // Attached Media Preview if any
            if (capturedPhotosCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تم إرفاق $capturedPhotosCount صفحات من كتابك",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                        IconButton(onClick = onClearPhotos, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (selectedFileName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Description, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الملف المرفق: $selectedFileName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        IconButton(onClick = onClearFile, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Direct Text Input Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "Study Text Snippet" else "النص أو ملخص الدرس",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Row {
                            TextButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        onCustomPastedTextChanged(clip)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isEnglish) "Paste" else "لصق", fontSize = 12.sp)
                            }
                            if (customPastedText.isNotBlank()) {
                                TextButton(
                                    onClick = { onCustomPastedTextChanged("") },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(if (isEnglish) "Clear" else "مسح", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customPastedText,
                        onValueChange = onCustomPastedTextChanged,
                        placeholder = { Text(if (isEnglish) "Paste definitions, formulas, or study notes here..." else "الصق هنا التعريفات، القوانين، أو ملخص الدرس ليتم تحويلها لبطاقات...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (customPastedText.isNotBlank()) {
                        Text(
                            text = "${customPastedText.length} حرف",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Custom Instructions (Optional)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isEnglish) "Custom AI Instructions (Optional)" else "تعليمات خاصة للذكاء الاصطناعي (اختياري) 🎯",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customInstructions,
                        onValueChange = onCustomInstructionsChanged,
                        placeholder = { Text(if (isEnglish) "e.g. Focus heavily on exam questions, formulas and definitions." else "مثال: ركز على أسئلة علل والقوانين الفيزيائية ومصطلحات الامتحان.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Action Button: "اختر المصدر وابدأ"
            Button(
                onClick = onStartGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEnglish) "Generate $selectedCardCount Cards & Start" else "توليد $selectedCardCount بطاقة وبدء الحفظ 🚀",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * Tile Component for Source Selection
 */
@Composable
fun SourceSelectorTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .height(105.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            if (isActive) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isActive) activeColor else activeColor.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isActive) Color.White else activeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Card Count Option Chip (15, 25, 40)
 */
@Composable
fun CardCountOption(
    count: Int,
    label: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFF7A00),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 2. Animated AI Loading Screen
 */
@Composable
fun QuickReviewLoadingView(
    progressMessage: String,
    cardCount: Int,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0xFFFF7A00).copy(alpha = pulseAlpha),
                                Color(0xFF3B82F6).copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(60.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFF7A00),
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "جاري توليد $cardCount بطاقة استذكار ذكية ⚡",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = progressMessage,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إلغاء")
            }
        }
    }
}

/**
 * 3. Interactive 3D Flippable Flashcard Review Player
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReviewPlayerView(
    isEnglish: Boolean,
    cards: List<QuickReviewCard>,
    currentIndex: Int,
    currentCard: QuickReviewCard,
    isFlipped: Boolean,
    onFlipCard: () -> Unit,
    onNextCard: () -> Unit,
    onPreviousCard: () -> Unit,
    onRateCard: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onSpeakText: (String) -> Unit,
    onSaveToLibrary: () -> Unit,
    onClosePlayer: () -> Unit
) {
    // 3D Flip Card Animation Rotation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "flipRotation"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "احفظ بسرعة (${currentIndex + 1}/${cards.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = currentCard.category.ifBlank { "مراجعة شاملة" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClosePlayer) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            if (currentCard.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (currentCard.isBookmarked) Color(0xFFFF7A00) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSaveToLibrary) {
                        Icon(Icons.Default.Save, contentDescription = "Save Deck", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Progress Bar & Percentage
            Column(modifier = Modifier.fillMaxWidth()) {
                val progress = (currentIndex + 1).toFloat() / cards.size.toFloat()
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFFF7A00),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "البطاقة ${currentIndex + 1} من ${cards.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(progress * 100).toInt()}% منجز",
                        fontSize = 12.sp,
                        color = Color(0xFFFF7A00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3D Flippable Flashcard View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX < -120) {
                                    onNextCard()
                                } else if (offsetX > 120) {
                                    onPreviousCard()
                                }
                                offsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                offsetX += dragAmount
                            }
                        )
                    }
                    .clickable { onFlipCard() }
            ) {
                if (rotation <= 90f) {
                    // FRONT OF CARD (Question)
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(6.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Card Top Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "❓ سؤال / مصطلح",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { onSpeakText(currentCard.question) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, "Speak Question", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            // Question Text
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = currentCard.question,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 32.sp
                                )

                                if (currentCard.keyConcept.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFFF7A00).copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "المفهوم: ${currentCard.keyConcept}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFFF7A00),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Tap to Flip Prompt
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اضغط على البطاقة لقلبها ومعرفة الإجابة",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    // BACK OF CARD (Answer)
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationY = 180f }
                            .shadow(6.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Back Top Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "💡 الإجابة النموذجية",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }

                                IconButton(
                                    onClick = { onSpeakText(currentCard.answer) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, "Speak Answer", tint = Color(0xFF10B981))
                                }
                            }

                            // Answer Details
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = currentCard.answer,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 28.sp
                                )

                                if (currentCard.formulaOrRule.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("📐 القانون / القاعدة:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(currentCard.formulaOrRule, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                if (currentCard.mnemonicTip.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("🧠 رابط ذهني للتذكر السريع:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(currentCard.mnemonicTip, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }

                            // Tap to Flip Back
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اضغط للعودة إلى السؤال",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rating / Spaced Repetition Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "قيم درجة تذكرك للمعلومة:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Again (أعدها)
                    RatingActionChip(
                        label = "أعدها",
                        subtitle = "نسيت",
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f),
                        onClick = { onRateCard(1) }
                    )

                    // 2. Hard (صعبة)
                    RatingActionChip(
                        label = "صعبة",
                        subtitle = "بصعوبة",
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f),
                        onClick = { onRateCard(2) }
                    )

                    // 3. Good (جيدة)
                    RatingActionChip(
                        label = "جيدة",
                        subtitle = "تذكرت",
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f),
                        onClick = { onRateCard(3) }
                    )

                    // 4. Easy (سهلة)
                    RatingActionChip(
                        label = "سهلة",
                        subtitle = "أتقنتها",
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = { onRateCard(4) }
                    )
                }

                // Prev / Next Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onPreviousCard,
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("السابق")
                    }

                    Button(
                        onClick = onFlipCard,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Flip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isFlipped) "عرض السؤال" else "قلب البطاقة", color = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(
                        onClick = onNextCard
                    ) {
                        Text(if (currentIndex + 1 < cards.size) "التالي" else "إنهاء الجلسة")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Rating Chip Component
 */
@Composable
fun RatingActionChip(
    label: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.6f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
            Text(subtitle, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

/**
 * 4. Summary & Mastery Results View
 */
@Composable
fun QuickReviewSummaryView(
    isEnglish: Boolean,
    totalCards: Int,
    againCount: Int,
    hardCount: Int,
    goodCount: Int,
    easyCount: Int,
    onRestartAll: () -> Unit,
    onReviewUnmastered: () -> Unit,
    onSaveDeck: () -> Unit,
    onDone: () -> Unit
) {
    val mastered = goodCount + easyCount
    val masteryPercentage = if (totalCards > 0) (mastered * 100) / totalCards else 100

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isEnglish) "Fast Recall Session Completed!" else "أحسنت! أتممت جلسة احفظ بسرعة 🚀",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "مستوى الإتقان التقديري: $masteryPercentage%",
                fontSize = 15.sp,
                color = if (masteryPercentage >= 70) Color(0xFF10B981) else Color(0xFFFF7A00),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("تفاصيل تقييم البطاقات:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatItem("متقنة تماماً (Easy)", "$easyCount", Color(0xFF10B981))
                        SummaryStatItem("جيدة (Good)", "$goodCount", Color(0xFF3B82F6))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatItem("صعبة (Hard)", "$hardCount", Color(0xFFF59E0B))
                        SummaryStatItem("تحتاج إعادة (Again)", "$againCount", Color(0xFFEF4444))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = onReviewUnmastered,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A00))
            ) {
                Icon(Icons.Default.Replay, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة البطاقات غير المتقنة (${againCount + hardCount})", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSaveDeck,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ الحزمة في مكتبتي للمراجعة اليومية")
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("العودة للرئيسية", fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun SummaryStatItem(title: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Bottom Sheet for Source Options
 */
@Composable
fun SourceSelectionSheetContent(
    isEnglish: Boolean,
    onSelectCamera: () -> Unit,
    onSelectGallery: () -> Unit,
    onSelectFile: () -> Unit,
    onSelectPasteText: () -> Unit,
    onSelectLibrary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isEnglish) "Select Study Source" else "اختر مصدر المحتوى الدراسي 📖",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        SourceSheetItem(
            icon = Icons.Default.CameraAlt,
            title = if (isEnglish) "Camera (Snap Pages)" else "الكاميرا (تصوير صفحات الكتاب)",
            subtitle = if (isEnglish) "Instant photo OCR & extraction" else "التقاط سريع لصفحات ملزمتك أو كتابك",
            color = Color(0xFF3B82F6),
            onClick = onSelectCamera
        )

        SourceSheetItem(
            icon = Icons.Default.PhotoLibrary,
            title = if (isEnglish) "Gallery (Choose Photos)" else "معرض الصور (اختيار صفحات)",
            subtitle = if (isEnglish) "Up to 10 textbook photos" else "استيراد حتى 10 صور من المعرض دفعة واحدة",
            color = Color(0xFFFF7A00),
            onClick = onSelectGallery
        )

        SourceSheetItem(
            icon = Icons.Default.UploadFile,
            title = if (isEnglish) "PDF & Word Documents" else "رفع ملف (PDF أو Word أو TXT)",
            subtitle = if (isEnglish) "Supports textbook chapters up to 300 pages" else "قراءة فورية لملفات الـ PDF والمذكرات",
            color = Color(0xFFEF4444),
            onClick = onSelectFile
        )

        SourceSheetItem(
            icon = Icons.Default.ContentPaste,
            title = if (isEnglish) "Paste Text Directly" else "الصق النص مباشرة",
            subtitle = if (isEnglish) "Paste custom definitions, notes or summaries" else "كتابة أو لصق ملخص أو قوانين معينة",
            color = Color(0xFF10B981),
            onClick = onSelectPasteText
        )

        SourceSheetItem(
            icon = Icons.Default.FolderSpecial,
            title = if (isEnglish) "From App Library" else "من مكتبتك المحفوظة",
            subtitle = if (isEnglish) "Saved notes, transcripts and books" else "اختر من مذكراتك وملخصاتك المحفوظة بالتطبيق",
            color = Color(0xFF8B5CF6),
            onClick = onSelectLibrary
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SourceSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * History & Quick Stats Dialog
 */
@Composable
fun QuickStatsHistoryDialog(
    isEnglish: Boolean,
    onDismiss: () -> Unit,
    onNavigateToDeckManager: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueryStats, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEnglish) "Quick Recall Stats" else "إحصائيات احفظ بسرعة 📊", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isEnglish) "Active recall performance and quick review summary:" else "ملخص أداء الاسترجاع النشط وجلسات المراجعة السريعة:")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("100%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("دقة الذكاء الاصطناعي", fontSize = 10.sp)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("25+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            Text("كارت في الدقيقة", fontSize = 10.sp)
                        }
                    }
                }

                Text(
                    text = "نصيحة: احفظ بطاقاتك في مكتبة التكرار المتباعد لتثبيتها في الذاكرة طويلة المدى.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onNavigateToDeckManager) {
                Text(if (isEnglish) "View All Decks" else "عرض مكتبة الحزم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "إغلاق")
            }
        }
    )
}
