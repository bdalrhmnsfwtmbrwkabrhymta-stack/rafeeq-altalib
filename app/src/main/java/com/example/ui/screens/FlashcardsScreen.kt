package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.FlashcardDeckEntity
import com.example.data.database.FlashcardEntity
import com.example.domain.utils.DocumentTextExtractor
import com.example.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    AnkiDarkTheme {
        val userSettings by viewModel.userSettings.collectAsState(initial = null)
        val isEnglish = userSettings?.appLanguage == "en"
        val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val context = LocalContext.current
                val decks by viewModel.flashcardDecks.collectAsState()

                var currentScreenMode by remember { mutableStateOf("QUICK_REVIEW") }
                var activeDeck by remember { mutableStateOf<FlashcardDeckEntity?>(null) }

                // Scanner & Card Creation states
                var showScannerView by remember { mutableStateOf(false) }
                var scannedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var showMediaPicker by remember { mutableStateOf(false) }
                var showAddDeckDialog by remember { mutableStateOf(false) }
                var deckToDelete by remember { mutableStateOf<FlashcardDeckEntity?>(null) }

                // Temporary file and bitmap for camera
                var tempPhotoFile by remember { mutableStateOf<File?>(null) }

                // Fallback thumbnail camera launcher
                val cameraPreviewLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { previewBitmap: Bitmap? ->
                    if (previewBitmap != null) {
                        scannedBitmap = previewBitmap
                        showScannerView = true
                    } else {
                        Toast.makeText(context, if (isEnglish) "No image captured" else "لم يتم التقاط صورة", Toast.LENGTH_SHORT).show()
                    }
                }

                // Full-resolution camera launcher via FileProvider
                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicture()
                ) { success ->
                    if (success && tempPhotoFile != null && tempPhotoFile!!.exists() && tempPhotoFile!!.length() > 0) {
                        val bitmap = BitmapFactory.decodeFile(tempPhotoFile!!.absolutePath)
                        if (bitmap != null) {
                            scannedBitmap = bitmap
                            showScannerView = true
                        } else {
                            // Fallback to preview launcher if file decoding failed
                            cameraPreviewLauncher.launch(null)
                        }
                    } else {
                        // In some emulators / devices TakePicture returns false or empty file, fallback to TakePicturePreview
                        cameraPreviewLauncher.launch(null)
                    }
                }

                fun launchCameraWithPermissionCheck() {
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
                        Toast.makeText(
                            context,
                            if (isEnglish) "Camera permission is required" else "يرجى منح إذن الكاميرا لالتقاط بطاقات أنكي",
                            Toast.LENGTH_SHORT
                        ).show()
                        try {
                            cameraPreviewLauncher.launch(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        launchCameraWithPermissionCheck()
                    } else {
                        Toast.makeText(
                            context,
                            if (isEnglish) "Camera permission denied" else "تم رفض إذن الكاميرا",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val galleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            if (bitmap != null) {
                                scannedBitmap = bitmap
                                showScannerView = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, if (isEnglish) "Failed to load image" else "فشل تحميل الصورة", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                if (showScannerView) {
                    ScannerWorkflowScreen(
                        viewModel = viewModel,
                        bitmap = scannedBitmap,
                        targetDeck = activeDeck ?: decks.firstOrNull(),
                        decks = decks,
                        isEnglish = isEnglish,
                        onClose = {
                            showScannerView = false
                            scannedBitmap = null
                            currentScreenMode = "DECKS_VIEW"
                        }
                    )
                } else if (showMediaPicker) {
                    AlertDialog(
                        onDismissRequest = { showMediaPicker = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isEnglish) "Create Cards with AI" else "توليد بطاقات بالذكاء الاصطناعي", fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (isEnglish) "Choose source content:" else "اختر طريقة استيراد المحتوى الدراسي:")

                                Button(
                                    onClick = {
                                        showMediaPicker = false
                                        val hasCamPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.CAMERA
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasCamPermission) {
                                            launchCameraWithPermissionCheck()
                                        } else {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isEnglish) "Camera (Take Photo)" else "الكاميرا (التقاط صورة)")
                                }

                                Button(
                                    onClick = {
                                        showMediaPicker = false
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isEnglish) "Gallery (Choose Image)" else "المعرض (اختيار صورة)")
                                }

                                OutlinedButton(
                                    onClick = {
                                        showMediaPicker = false
                                        scannedBitmap = null
                                        showScannerView = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Description, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isEnglish) "Document / Text Input" else "استيراد ملف أو كتابة نص")
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showMediaPicker = false }) {
                                Text(if (isEnglish) "Cancel" else "إلغاء")
                            }
                        }
                    )
                }

                if (showAddDeckDialog) {
                    var newTitle by remember { mutableStateOf("") }
                    var newDesc by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { showAddDeckDialog = false },
                        title = { Text(if (isEnglish) "Create Flashcard Deck" else "إنشاء حزمة بطاقات جديدة") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text(if (isEnglish) "Deck Title" else "عنوان الحزمة (مثال: فيزياء الفصل الأول)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newDesc,
                                    onValueChange = { newDesc = it },
                                    label = { Text(if (isEnglish) "Description (optional)" else "وصف الحزمة (اختياري)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (newTitle.isNotBlank()) {
                                    viewModel.createFlashcardDeck(newTitle.trim(), newDesc.trim())
                                    showAddDeckDialog = false
                                }
                            }) {
                                Text(if (isEnglish) "Create" else "إنشاء")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddDeckDialog = false }) {
                                Text(if (isEnglish) "Cancel" else "إلغاء")
                            }
                        }
                    )
                }

                if (deckToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { deckToDelete = null },
                        title = { Text(if (isEnglish) "Delete Deck" else "حذف الحزمة") },
                        text = { Text(if (isEnglish) "Are you sure you want to delete '${deckToDelete?.title}' and all its cards?" else "هل أنت متأكد من رغبتك في حذف حزمة '${deckToDelete?.title}' وجميع البطاقات التابعة لها؟") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    deckToDelete?.let { viewModel.deleteDeck(it) }
                                    deckToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(if (isEnglish) "Delete" else "حذف")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deckToDelete = null }) {
                                Text(if (isEnglish) "Cancel" else "إلغاء")
                            }
                        }
                    )
                }

                when (currentScreenMode) {
                    "QUICK_REVIEW" -> {
                        QuickReviewFlashcardsScreen(
                            viewModel = viewModel,
                            onNavigateBack = onNavigateBack,
                            onNavigateToDeckManager = { currentScreenMode = "DECKS_VIEW" }
                        )
                    }
                    "WEAK_WORDS" -> {
                        val weakDeck = FlashcardDeckEntity(
                            0,
                            if (isEnglish) "Weak Cards" else "البطاقات التي تحتاج تركيز",
                            if (isEnglish) "Cards that need more spaced repetition review" else "بطاقات بمستوى إتقان منخفض لمراجعتها",
                            0
                        )
                        AnkiReviewSessionScreen(
                            deck = weakDeck,
                            viewModel = viewModel,
                            isEnglish = isEnglish,
                            onBack = { currentScreenMode = "DECKS_VIEW" }
                        )
                    }
                    "REVIEW_SESSION" -> {
                        if (activeDeck != null) {
                            AnkiReviewSessionScreen(
                                deck = activeDeck!!,
                                viewModel = viewModel,
                                isEnglish = isEnglish,
                                onBack = { currentScreenMode = "DECKS_VIEW" }
                            )
                        } else {
                            currentScreenMode = "DECKS_VIEW"
                        }
                    }
                    "CARD_BROWSER" -> {
                        if (activeDeck != null) {
                            CardBrowserScreen(
                                deck = activeDeck!!,
                                viewModel = viewModel,
                                isEnglish = isEnglish,
                                onBack = { currentScreenMode = "DECKS_VIEW" }
                            )
                        } else {
                            currentScreenMode = "DECKS_VIEW"
                        }
                    }
                    else -> {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        brush = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8))),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(if (isEnglish) "Unky Spaced Repetition" else "أنكي - التكرار المتباعد", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                Text(if (isEnglish) "Smart Flashcards Engine" else "نظام الاستذكار المتباعد الذكي", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { currentScreenMode = "QUICK_REVIEW" }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Quick Review")
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { currentScreenMode = "QUICK_REVIEW" }) {
                                            Icon(Icons.Default.Bolt, "احفظ بسرعة", tint = Color(0xFFFF7A00))
                                        }
                                        IconButton(onClick = { showAddDeckDialog = true }) {
                                            Icon(Icons.Default.AddCircleOutline, "Add Deck", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            },
                            floatingActionButton = {
                                ExtendedFloatingActionButton(
                                    onClick = { currentScreenMode = "QUICK_REVIEW" },
                                    containerColor = Color(0xFFFF7A00),
                                    contentColor = Color.White,
                                    icon = { Icon(Icons.Default.Bolt, null) },
                                    text = { Text(if (isEnglish) "Fast Recall" else "احفظ بسرعة", fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        ) { padding ->
                            LazyColumn(
                                modifier = Modifier
                                    .padding(padding)
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Fast Recall Banner
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { currentScreenMode = "QUICK_REVIEW" },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.5.dp, Color(0xFFFF7A00).copy(alpha = 0.4f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .background(Color(0xFFFF7A00).copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Bolt, null, tint = Color(0xFFFF7A00))
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    if (isEnglish) "Fast Recall (Quick AI Cards)" else "احفظ بسرعة ⚡ (فلاش كاردز ذكية)",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = Color(0xFFFF7A00)
                                                )
                                                Text(
                                                    if (isEnglish) "Snap pages, upload PDF or paste text to master in minutes" else "صور صفحاتك، ارفع PDF أو الصق النص واحفظ في دقائق",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFFFF7A00))
                                        }
                                    }
                                }
                                // Weak words banner
                                item {
                                    val weakCards by viewModel.weakFlashcards.collectAsState(initial = emptyList())
                                    if (weakCards.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { currentScreenMode = "WEAK_WORDS" },
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                                }
                                                Spacer(modifier = Modifier.width(14.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        if (isEnglish) "Cards Needing Attention" else "بطاقات تحتاج إلى مراجعة مكثفة",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        "${weakCards.size} ${if (isEnglish) "cards have low mastery rating" else "بطاقة بمستوى إتقان أقل من 60%"}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (isEnglish) "Flashcard Decks" else "حزم البطاقات التعليمية",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${decks.size} ${if (isEnglish) "Decks" else "حزمة"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (decks.isEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(28.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Default.Layers,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    if (isEnglish) "No Flashcard Decks Yet" else "لا توجد حزم بطاقات بعد",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    if (isEnglish)
                                                        "Tap the AI Generator below to scan a book page, import a PDF/Docx, or create a new deck."
                                                    else
                                                        "اضغط على زر التوليد بالذكاء الاصطناعي لتصوير صفحة من الكتاب، أو استيراد ملف PDF/Docx، أو إنشاء حزمة جديدة.",
                                                    textAlign = TextAlign.Center,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                items(decks) { deck ->
                                    DeckCardItem(
                                        deck = deck,
                                        viewModel = viewModel,
                                        isEnglish = isEnglish,
                                        onReviewClick = {
                                            activeDeck = deck
                                            currentScreenMode = "REVIEW_SESSION"
                                        },
                                        onBrowseClick = {
                                            activeDeck = deck
                                            currentScreenMode = "CARD_BROWSER"
                                        },
                                        onDeleteClick = {
                                            deckToDelete = deck
                                        }
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(60.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeckCardItem(
    deck: FlashcardDeckEntity,
    viewModel: MainViewModel,
    isEnglish: Boolean,
    onReviewClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dueCards: List<FlashcardEntity> by viewModel.getDueCardsForDeck(deck.id).collectAsState(initial = emptyList())
    val totalCards: List<FlashcardEntity> by viewModel.getCardsForDeckFlow(deck.id).collectAsState(initial = emptyList())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReviewClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (deck.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = deck.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Row {
                    IconButton(onClick = onBrowseClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Browse Cards", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Deck", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (dueCards.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (dueCards.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.4f) else Color.Transparent)
                    ) {
                        Text(
                            text = if (isEnglish) "Due: ${dueCards.size}" else "مستحق للمراجعة: ${dueCards.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dueCards.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isEnglish) "Total: ${totalCards.size}" else "الإجمالي: ${totalCards.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Button(
                    onClick = onReviewClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isEnglish) "Study" else "بدء المراجعة", fontSize = 13.sp)
                }
            }
        }
    }
}
