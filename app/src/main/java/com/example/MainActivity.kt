package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.preferences.UserSettings
import com.example.domain.prayer.PrayerForegroundService
import com.example.pdfstudio.PdfStudioHubScreen
import com.example.ui.MainViewModel
import com.example.ui.components.MandatoryUserSetupDialog
import com.example.ui.screens.*
import com.example.ui.theme.RafiqTheme

import androidx.fragment.app.FragmentActivity
import com.example.domain.ads.AdMobManager
import com.example.domain.security.EmulatorDetector
import com.example.ui.security.AppLockScreen

import com.example.ui.screens.onboarding.RafeeqSetupFlowScreen
import com.example.ui.dialogs.UniversalNotificationPermissionDialog
import android.Manifest
import android.app.AlarmManager
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class NavItem(val route: String, val titleArabic: String, val titleEnglish: String, val icon: ImageVector) {
    HOME("home", "الرئيسية", "Home", Icons.Default.GridView),
    QURAN("quran", "المصحف", "Quran", Icons.Default.MenuBook),
    SCHEDULE("schedule", "جدول", "Schedule", Icons.Default.CalendarMonth),
    POMODORO("pomodoro", "مؤقت", "Timer", Icons.Default.Timer),
    DOCUMENTS("documents", "PDF", "PDF", Icons.Default.PictureAsPdf),
    SETTINGS("settings", "الإعدادات", "Settings", Icons.Default.Settings)
}

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN_DIRECT_SCREEN = "EXTRA_OPEN_DIRECT_SCREEN"
        const val DIRECT_SCREEN_QIBLA = "QIBLA_SCREEN"
    }

    private var pendingIntent by mutableStateOf<Intent?>(null)
    private var pendingDirectRoute by mutableStateOf<String?>(null)
    private var pendingSharedRafidUrl by mutableStateOf<String?>(null)
    private var pendingSmartDownloadUrl by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntent = intent
        pendingDirectRoute = resolveDirectRouteFromIntent(intent)
        pendingSharedRafidUrl = intent?.getStringExtra(com.hawas.study.rafid.ShareReceiverActivity.EXTRA_RAFID_SHARED_URL)
        pendingSmartDownloadUrl = intent?.getStringExtra(com.example.download.ui.DownloadShareReceiverActivity.EXTRA_SMART_DOWNLOAD_URL)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "enableEdgeToEdge skipped: ${t.message}")
        }

        // Mount Compose UI immediately so first frame renders without blocking or crashing on background services
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val userSettings by mainViewModel.userSettings.collectAsState()

            LaunchedEffect(userSettings.appLanguage) {
                try {
                    com.example.domain.localization.AppLanguageManager.applyLocale(this@MainActivity, userSettings.appLanguage)
                } catch (t: Throwable) {
                    android.util.Log.w("MainActivity", "applyLocale skipped: ${t.message}")
                }
            }

            LaunchedEffect(pendingIntent) {
                pendingIntent?.let { intentToProcess ->
                    val directRoute = resolveDirectRouteFromIntent(intentToProcess)
                    if (!directRoute.isNullOrBlank()) {
                        pendingDirectRoute = directRoute
                    }
                    intentToProcess.getStringExtra(com.hawas.study.rafid.ShareReceiverActivity.EXTRA_RAFID_SHARED_URL)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { sharedUrl ->
                            pendingSharedRafidUrl = sharedUrl
                            if (pendingDirectRoute.isNullOrBlank()) {
                                pendingDirectRoute = "rafid"
                            }
                        }
                    intentToProcess.getStringExtra(com.example.download.ui.DownloadShareReceiverActivity.EXTRA_SMART_DOWNLOAD_URL)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { smartUrl ->
                            pendingSmartDownloadUrl = smartUrl
                            if (pendingDirectRoute.isNullOrBlank()) {
                                pendingDirectRoute = "smart_download"
                            }
                        }
                    try {
                        handleIncomingPdfIntent(this@MainActivity, intentToProcess, mainViewModel)
                    } catch (t: Throwable) {
                        android.util.Log.w("MainActivity", "handleIncomingPdfIntent failed: ${t.message}")
                    }
                    pendingIntent = null
                }
            }

            val isDarkTheme = userSettings.isDarkMode == true
            val layoutDirection = if (com.example.domain.localization.AppLanguageManager.isRtlLanguage(userSettings.appLanguage)) LayoutDirection.Rtl else LayoutDirection.Ltr

            RafiqTheme(userSettings = userSettings, darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainAppContent(
                            mainViewModel = mainViewModel,
                            userSettings = userSettings,
                            directRouteRequest = pendingDirectRoute,
                            onDirectRouteConsumed = { pendingDirectRoute = null },
                            initialRafidUrl = pendingSharedRafidUrl,
                            onRafidUrlConsumed = { pendingSharedRafidUrl = null },
                            initialSmartDownloadUrl = pendingSmartDownloadUrl,
                            onSmartDownloadUrlConsumed = { pendingSmartDownloadUrl = null }
                        )
                    }
                }
            }
        }

        // Run non-UI startup tasks safely after UI mount and once Lifecycle reaches STARTED/RESUMED
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    try {
                        com.example.data.notification.AzanNotificationHelper(applicationContext).createAllNotificationChannels()
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "Failed creating notification channels", t)
                    }

                    try {
                        if (EmulatorDetector.isEmulator()) {
                            android.util.Log.w("MainActivitySecurity", "Running on emulator environment")
                        }
                    } catch (_: Throwable) {}

                    try {
                        com.example.domain.prayer.PrayerAlarmScheduler.rescheduleAll(applicationContext)
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "PrayerAlarmScheduler.rescheduleAll failed", t)
                    }

                    try {
                        com.example.domain.whatsapp.DailyWhatsAppReminderScheduler.scheduleDailyReminder(applicationContext)
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "DailyWhatsAppReminderScheduler failed", t)
                    }

                    try {
                        com.example.domain.notification.NotificationReliabilityManager.scheduleWatchdog(applicationContext)
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "NotificationReliabilityManager.scheduleWatchdog failed", t)
                    }
                }

                try {
                    AdMobManager.initialize(this@MainActivity) {
                        try {
                            AdMobManager.showAdOnFirstLaunchOnly(this@MainActivity) {}
                        } catch (_: Throwable) {}
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "AdMobManager.initialize failed", t)
                }

                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("MainActivity", "Notification permission request skipped: ${t.message}")
                    }

                    try {
                        PrayerForegroundService.start(this@MainActivity)
                    } catch (t: Throwable) {
                        android.util.Log.w("MainActivity", "PrayerForegroundService.start deferred: ${t.message}")
                    }
                    // Only run once per resume cycle when launched
                    kotlinx.coroutines.awaitCancellation()
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainActivity", "Non-fatal startup background setup error", t)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
        resolveDirectRouteFromIntent(intent)?.let { directRoute ->
            pendingDirectRoute = directRoute
        }
        intent.getStringExtra(com.hawas.study.rafid.ShareReceiverActivity.EXTRA_RAFID_SHARED_URL)
            ?.takeIf { it.isNotBlank() }
            ?.let { sharedUrl ->
                pendingSharedRafidUrl = sharedUrl
                pendingDirectRoute = "rafid"
            }
        intent.getStringExtra(com.example.download.ui.DownloadShareReceiverActivity.EXTRA_SMART_DOWNLOAD_URL)
            ?.takeIf { it.isNotBlank() }
            ?.let { smartUrl ->
                pendingSmartDownloadUrl = smartUrl
                pendingDirectRoute = "smart_download"
            }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_POWER,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    if (com.example.domain.prayer.AdhanPlaybackService.isPlayingAdhan) {
                        com.example.domain.prayer.AdhanPlaybackService.stop(this)
                        com.example.data.audio.AppAudioManager.stopAllNonAdhanAudio(this)
                        android.widget.Toast.makeText(this, "تم كتم وصمت صوت الأذان 🔇", android.widget.Toast.LENGTH_SHORT).show()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private fun handleIncomingPdfIntent(context: Context, intent: Intent?, viewModel: MainViewModel) {
    if (intent == null) return
    val action = intent.action
    var uri: Uri? = null

    if (Intent.ACTION_VIEW == action) {
        uri = intent.data
    } else if (Intent.ACTION_SEND == action) {
        uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    } else if (Intent.ACTION_SEND_MULTIPLE == action) {
        val uriList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        uri = uriList?.firstOrNull()
    }

    if (uri != null) {
        val fileName = getPdfFileName(context, uri) ?: "مستند_مستلم.pdf"
        viewModel.openPdfDocument(uri = uri.toString(), title = fileName)
    }
}

private fun getPdfFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        return cursor.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

@Composable
fun PermissionOptimizationDialog() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showUniversalNotificationDialog by remember { mutableStateOf(false) }

    fun checkAndMaybeShow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        val isNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val isExactAlarmsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.canScheduleExactAlarms() ?: true
        } else true
        val isOverlayGranted = Settings.canDrawOverlays(context)
        if (!isIgnoringBattery || !isNotificationsGranted || !isExactAlarmsGranted || !isOverlayGranted) {
            showUniversalNotificationDialog = true
        }
    }

    LaunchedEffect(Unit) { checkAndMaybeShow() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkAndMaybeShow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showUniversalNotificationDialog) {
        UniversalNotificationPermissionDialog(
            onDismiss = { showUniversalNotificationDialog = false },
            onPermissionGranted = { showUniversalNotificationDialog = false }
        )
    }
}

private fun resolveDirectRouteFromIntent(intent: Intent?): String? {
    if (intent == null) return null
    val directFlag = intent.getStringExtra(MainActivity.EXTRA_OPEN_DIRECT_SCREEN)
    if (directFlag == MainActivity.DIRECT_SCREEN_QIBLA) return "qibla"
    return intent.getStringExtra("NAVIGATE_TO")?.takeIf { it.isNotBlank() }
}

@Composable
fun MainAppContent(
    mainViewModel: MainViewModel,
    userSettings: UserSettings,
    directRouteRequest: String? = null,
    onDirectRouteConsumed: () -> Unit = {},
    initialRafidUrl: String? = null,
    onRafidUrlConsumed: () -> Unit = {},
    initialSmartDownloadUrl: String? = null,
    onSmartDownloadUrlConsumed: () -> Unit = {}
) {
    var showSplash by remember { mutableStateOf(directRouteRequest == null) }
    var showLoginGateway by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf(directRouteRequest ?: "home") }
    var subRouteOrigin by remember { mutableStateOf("home") }
    var calculatorOriginRoute by remember { mutableStateOf("home") }
    var isMushafFullScreen by remember { mutableStateOf(false) }
    var isDofaatiFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(directRouteRequest) {
        if (!directRouteRequest.isNullOrBlank()) {
            showSplash = false
            currentRoute = directRouteRequest
            onDirectRouteConsumed()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != "quran" && currentRoute != "quran_audio") {
            isMushafFullScreen = false
        }
        if (currentRoute != "dofaati") {
            isDofaatiFullScreen = false
        }
    }

    val incomingPdfUri by mainViewModel.currentOpenPdfUri.collectAsState()
    val incomingPdfTitle by mainViewModel.currentOpenPdfTitle.collectAsState()
    val autoSessionStatus by mainViewModel.autoSessionRestoreStatus.collectAsState()

    LaunchedEffect(autoSessionStatus) {
        if (autoSessionStatus != null) {
            kotlinx.coroutines.delay(4500)
            mainViewModel.clearAutoSessionRestoreStatus()
        }
    }

    LaunchedEffect(incomingPdfUri) {
        if (!incomingPdfUri.isNullOrBlank()) {
            showSplash = false
            currentRoute = "pdf_studio"
        }
    }

    val isEnglish = userSettings.appLanguage == "en"
    val isAppUnlocked by mainViewModel.isAppUnlocked.collectAsState()
    val isSecurityLocked = (userSettings.isBiometricEnabled || (userSettings.isPinLockEnabled && userSettings.pinCode.isNotBlank())) && !isAppUnlocked

    val isProfileCompleted = userSettings.isOnboardingCompleted
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    if (showSplash) {
        SplashScreen(
            onSplashFinished = {
                showSplash = false
            }
        )
    } else if (userSettings.isAppLockedByAdmin) {
        AdminLockdownScreen(
            lockMessage = userSettings.adminLockMessage,
            onAdminUnlock = { enteredPin ->
                if (mainViewModel.verifyAdminPin(enteredPin)) {
                    mainViewModel.setAppLockedByAdmin(false)
                    true
                } else {
                    false
                }
            }
        )
    } else if (isSecurityLocked) {
        AppLockScreen(
            viewModel = mainViewModel,
            userSettings = userSettings,
            onUnlocked = {
                mainViewModel.unlockApp()
            }
        )
    } else if (!userSettings.isOnboardingCompleted) {
        RafeeqSetupFlowScreen(
            viewModel = mainViewModel,
            userSettings = userSettings,
            onFinishSetup = {
                mainViewModel.completeOnboarding()
            }
        )
    } else {
        PermissionOptimizationDialog()
        // Safe hardware/gesture back handler: return to home before exiting
        BackHandler(enabled = currentRoute != "home") {
            currentRoute = "home"
        }

        CompositionLocalProvider(LocalLayoutDirection provides (if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl)) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    com.example.ui.components.AppDrawerContent(
                        userSettings = userSettings,
                        currentRoute = currentRoute,
                        onNavigate = { route -> currentRoute = route },
                        onCloseDrawer = {
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isLandscape = maxWidth > maxHeight && maxWidth >= 480.dp
                    val isStudyToolsRoute = currentRoute == "study_tools" || currentRoute.startsWith("study_") && currentRoute != "study_schedule_ai"
                    val isFullScreenPdfRoute = currentRoute == "pdf_studio" || currentRoute == "documents" || currentRoute == "calculator" || currentRoute == "rafid" || currentRoute == "smart_download" || isStudyToolsRoute || (isMushafFullScreen && (currentRoute == "quran" || currentRoute == "quran_audio")) || (isDofaatiFullScreen && currentRoute == "dofaati")
                    val showMainNav = currentRoute in listOf(
                        "home", "account", "quests", "quran", "quran_audio",
                        "dofaati", "zakat", "library", "community", "games", "plans", "tools",
                        "schedule", "tasks"
                    ) && !isFullScreenPdfRoute
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = if (isFullScreenPdfRoute) WindowInsets(0) else if (isLandscape) WindowInsets.safeDrawing else ScaffoldDefaults.contentWindowInsets,
                        bottomBar = {
                            if (showMainNav && !isLandscape) {
                                com.example.ui.components.RafiqBottomBar(
                                    currentRoute = currentRoute,
                                    onTabSelected = { tab ->
                                        currentRoute = tab.route
                                    }
                                )
                            }
                        }
                    ) { paddingValues ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isFullScreenPdfRoute) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .padding(paddingValues)
                                            .consumeWindowInsets(paddingValues)
                                    }
                                )
                        ) {
                            if (showMainNav && isLandscape) {
                                com.example.ui.components.RafiqNavigationRail(
                                    currentRoute = currentRoute,
                                    onTabSelected = { tab ->
                                        currentRoute = tab.route
                                    }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                AnimatedContent(
                            targetState = currentRoute,
                            transitionSpec = {
                                (slideInHorizontally(
                                    initialOffsetX = { -it / 3 },
                                    animationSpec = androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(340)) + scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(340))) togetherWith
                                (slideOutHorizontally(
                                    targetOffsetX = { it / 3 },
                                    animationSpec = androidx.compose.animation.core.tween(340, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(340)) + scaleOut(targetScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(340)))
                            },
                            label = "screenTransition"
                        ) { targetRoute ->
                            when (targetRoute) {
                                "home" -> HomeScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { next ->
                                        if (next == "calculator") calculatorOriginRoute = "home"
                                        currentRoute = next
                                    },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "library" -> LibraryScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "games" -> GamesScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "plans" -> PlansScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "tools" -> ToolsScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { next ->
                                        if (next == "calculator") calculatorOriginRoute = "tools"
                                        currentRoute = next
                                    },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "community" -> CommunityScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "account" -> AccountScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                                "irab" -> IrabGrammarScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "tools" })
                                "calculator" -> com.example.calculator.ScientificCalculatorScreen(
                                    onNavigateBack = { currentRoute = calculatorOriginRoute }
                                )
                                "tasks" -> TasksScreen(viewModel = mainViewModel)
                                "quests" -> QuestsScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "games" })
                                "schedule" -> ScheduleScreen(viewModel = mainViewModel, onNavigateToAiSchedule = { currentRoute = "study_schedule_ai" })
                                "study_schedule_ai" -> StudyScheduleGeneratorScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "plans" })
                                "pomodoro" -> PomodoroScreen(viewModel = mainViewModel)
                                "chemistry" -> ChemistryScreen(viewModel = mainViewModel)
                                "english_learning" -> com.example.ui.screens.EnglishLearningDashboardScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "tools" })
                                "flashcards" -> FlashcardsScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "library" })
                                "notes" -> NotesScreen(
                                    viewModel = mainViewModel,
                                    onNavigateToOcr = { currentRoute = "image_ocr_summary" }
                                )
                                "whiteboard" -> WhiteboardScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "tools" })
                                "pdf_studio" -> PdfStudioHubScreen(
                                    viewModel = mainViewModel,
                                    initialPdfUri = incomingPdfUri,
                                    initialPdfTitle = incomingPdfTitle,
                                    onNavigateBack = {
                                        mainViewModel.clearOpenPdfDocument()
                                        currentRoute = "library"
                                    },
                                    onClearIncomingPdf = {
                                        mainViewModel.clearOpenPdfDocument()
                                    }
                                )
                                "grade_selection" -> GradeSelectionScreen(viewModel = mainViewModel, onStageSelected = { currentRoute = "home" })
                                "prayer" -> PrayerScreen(
                                    viewModel = mainViewModel,
                                    initialSection = com.example.ui.screens.islamic.IslamicSubSection.HUB,
                                    onNavigate = { next ->
                                        subRouteOrigin = "prayer"
                                        currentRoute = next
                                    },
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "qibla" -> PrayerScreen(
                                    viewModel = mainViewModel,
                                    initialSection = com.example.ui.screens.islamic.IslamicSubSection.QIBLA,
                                    onNavigate = { next ->
                                        subRouteOrigin = "qibla"
                                        currentRoute = next
                                    },
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "dofaati" -> com.hawas.study.dofaati.ui.screens.DofaatiNavigation(
                                    onNavigateBackToApp = {
                                        isDofaatiFullScreen = false
                                        currentRoute = "home"
                                    },
                                    onFullScreenChanged = { isDofaatiFullScreen = it }
                                )
                                "rafid" -> com.hawas.study.rafid.ui.screens.RafidRootHost(
                                    initialSharedUrl = initialRafidUrl,
                                    onConsumeSharedUrl = onRafidUrlConsumed,
                                    onNavigateBackToApp = { currentRoute = "home" }
                                )
                                "smart_download" -> com.example.download.ui.DownloadCenterScreen(
                                    initialSharedUrl = initialSmartDownloadUrl,
                                    onConsumeSharedUrl = onSmartDownloadUrlConsumed,
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_tools" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = null,
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_timer" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "timer",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_inheritance" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "inheritance",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_irab" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "irab",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_dictionary" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "dictionary",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_scanner" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "scanner",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_files" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "files",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_translator" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "translator",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "study_gpa" -> com.example.studytools.StudyToolsHubScreen(
                                    viewModel = mainViewModel,
                                    initialToolKey = "gpa",
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "zakat" -> ZakatCalculatorScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "home" })
                                "god_names" -> GodNamesScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "home" })
                                "azkar" -> PrayerScreen(
                                    viewModel = mainViewModel,
                                    initialSection = com.example.ui.screens.islamic.IslamicSubSection.AZKAR,
                                    onNavigate = { next ->
                                        subRouteOrigin = "azkar"
                                        currentRoute = next
                                    },
                                    onNavigateBack = { currentRoute = "home" }
                                )
                                "quran" -> QuranScreen(
                                    viewModel = mainViewModel,
                                    initialTab = QuranTab.SURAHS,
                                    onFullScreenViewerChanged = { isMushafFullScreen = it },
                                    onNavigateBack = {
                                        isMushafFullScreen = false
                                        currentRoute = if (subRouteOrigin == "prayer") {
                                            subRouteOrigin = "home"
                                            "prayer"
                                        } else {
                                            "home"
                                        }
                                    }
                                )
                                "quran_audio" -> QuranScreen(
                                    viewModel = mainViewModel,
                                    initialTab = QuranTab.AUDIO,
                                    onFullScreenViewerChanged = { isMushafFullScreen = it },
                                    onNavigateBack = {
                                        isMushafFullScreen = false
                                        currentRoute = "home"
                                    }
                                )
                                "ai" -> AiScreen(viewModel = mainViewModel, onNavigate = { currentRoute = it })
                                "copy" -> BestCopyScreen(viewModel = mainViewModel)
                                "dictionary" -> DictionaryScreen(viewModel = mainViewModel)
                                "numbers" -> NumbersScreen(viewModel = mainViewModel)
                                "documents" -> PdfStudioHubScreen(
                                    viewModel = mainViewModel,
                                    initialPdfUri = incomingPdfUri,
                                    initialPdfTitle = incomingPdfTitle,
                                    onNavigateBack = {
                                        mainViewModel.clearOpenPdfDocument()
                                        currentRoute = "home"
                                    },
                                    onClearIncomingPdf = {
                                        mainViewModel.clearOpenPdfDocument()
                                    }
                                )
                                "brain_games" -> BrainGamesScreen(
                                    onNavigateBack = { currentRoute = "games" },
                                    onNavigateToChess = { currentRoute = "chess" }
                                )
                                "chess" -> com.example.ui.screens.chess.ChessScreen(onNavigateBack = { currentRoute = "games" })
                                "habits" -> HabitsScreen(viewModel = mainViewModel, onNavigateBack = { currentRoute = "plans" })
                                "memory_book" -> MemoryBookScreen(viewModel = mainViewModel, onNavigate = { currentRoute = it })
                                "idea_vault" -> IdeaVaultScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { currentRoute = "tools" },
                                    onNavigateToTasks = { currentRoute = "tasks" }
                                )
                                "image_ocr_summary" -> OcrSummarizerScreen(
                                    viewModel = mainViewModel,
                                    onNavigateBack = { currentRoute = "tools" },
                                    onNavigateToFlashcards = { currentRoute = "flashcards" },
                                    onNavigateToDocuments = { currentRoute = "documents" },
                                    onNavigateToNotes = { currentRoute = "notes" }
                                )
                                "addons" -> AddonsScreen(viewModel = mainViewModel, onNavigate = { currentRoute = it })
                                "notification_reliability_check" -> NotificationReliabilityCheckScreen(
                                    onNavigateBack = {
                                        currentRoute = if (subRouteOrigin == "prayer") {
                                            subRouteOrigin = "home"
                                            "prayer"
                                        } else {
                                            "account"
                                        }
                                    }
                                )
                                "settings" -> SettingsScreen(viewModel = mainViewModel, onNavigate = { currentRoute = it })
                                "app_theme", "theme" -> AppThemeScreen(viewModel = mainViewModel, onBackClick = { currentRoute = "account" })
                                "admin_console" -> AdminConsoleScreen(viewModel = mainViewModel, onBack = { currentRoute = "account" })
                                "onboarding" -> RafeeqSetupFlowScreen(viewModel = mainViewModel, userSettings = userSettings, onFinishSetup = { currentRoute = "home" })
                                "login", "student_gateway" -> StudentGatewayScreen(viewModel = mainViewModel, userSettings = userSettings, onLoginSuccess = { currentRoute = "home" })
                                "register" -> StudentRegisterScreen(viewModel = mainViewModel, userSettings = userSettings, onNavigateToLogin = { currentRoute = "login" }, onRegisterSuccess = { currentRoute = "home" })
                                else -> HomeScreen(
                                    viewModel = mainViewModel,
                                    onNavigate = { currentRoute = it },
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } }
                                )
                            }
                        }

                // Cloud Session Auto-Restoration Banner
                androidx.compose.animation.AnimatedVisibility(
                    visible = autoSessionStatus != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .zIndex(99f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = autoSessionStatus ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
                }
                }
            }
        }
    }
}
