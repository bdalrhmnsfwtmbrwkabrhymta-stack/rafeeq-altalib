package com.example.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.preferences.UserSettings
import com.example.domain.localization.AppLanguageManager
import com.example.domain.prayer.CalculationMethod
import com.example.domain.prayer.PrayerAlarmScheduler
import com.example.domain.telegram.DailyTelegramReportHelper
import com.example.ui.MainViewModel
import com.example.ui.TelegramLinkState
import com.example.ui.components.validateEgyptianPhone
import com.example.ui.components.validateFullName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val TELEGRAM_USERNAME_REGEX = Regex("^@[A-Za-z][A-Za-z0-9_]{4,31}$")

fun validatePhoneOrTelegramContact(value: String): Pair<Boolean, String> {
    val trimmed = value.trim()
    if (trimmed.isBlank()) {
        return Pair(false, "يرجى كتابة رقم الهاتف (11 رقماً) أو معرف تليجرام يبدأ بـ @")
    }
    return if (trimmed.startsWith("@")) {
        if (TELEGRAM_USERNAME_REGEX.matches(trimmed)) {
            Pair(true, "✓ معرف تليجرام صحيح")
        } else {
            Pair(false, "❌ خطأ: معرف التليجرام يجب أن يبدأ بحرف بعد @، ويتكون من 5 أحرف على الأقل.")
        }
    } else {
        validateEgyptianPhone(trimmed)
    }
}

/**
 * MODULE 1: Onboarding & Step-by-Step Setup Flow (تدفق التهيئة)
 * Includes a top progress step bar across all onboarding screens.
 *
 * Step 1: Language Selection (اختيار اللغة)
 *         - Header with greeting character illustration and bubble text "السلام عليكم".
 *         - 7 Language options with custom landmark icons:
 *           * العربية (Kaaba Icon)
 *           * English (Big Ben Icon)
 *           * Indonesia (Indonesian Temple Icon)
 *           * Français (Eiffel Tower Icon)
 *           * Russian (Kremlin Icon)
 *           * Türk (Sultan Ahmed Mosque Icon)
 *           * Duits (Brandenburg Gate Icon)
 *         - Store selected locale globally across the app.
 *
 * Step 2: GPS Location & Auto-Qibla Calibration (تحديد الموقع والقبلة)
 *         - Request GPS permissions to calculate millisecond-accurate prayer schedules and Qibla.
 *         - Auto-detected location confirmation screen (e.g. المحمودية - مصر).
 *         - Buttons: Primary "الموقع صحيح", and text link "موقعك غير صحيح؟ اضغط هنا" for manual search.
 *
 * Step 3: Gamified Student Account & Competition Flow (تسجيل الدخول والتنافس)
 *         - Registration highlights: Sync progress, student global leaderboards, points system, and rewards.
 *         - Terms and Conditions consent note.
 *         - Buttons: Primary "سجل دخول الآن" and Secondary "تخطي".
 */

data class AppLanguageOption(
    val code: String,
    val nameNative: String,
    val landmarkName: String,
    val landmarkEmoji: String,
    val flagEmoji: String,
    val primaryColor: Color
)

@Composable
fun RafeeqSetupFlowScreen(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    onFinishSetup: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) } // 1: Language, 2: Notification & Battery, 3: GPS Location, 4: Account & Gamification
    val totalSteps = 4

    // Selected Language
    var selectedLangCode by remember { mutableStateOf(userSettings.appLanguage.ifBlank { "ar" }) }

    // Location State
    var detectedCity by remember { mutableStateOf(userSettings.cityLocation.ifBlank { "المحمودية - مصر" }) }
    var detectedLat by remember { mutableDoubleStateOf(if (userSettings.latitude != 0.0) userSettings.latitude else 31.1873) }
    var detectedLng by remember { mutableDoubleStateOf(if (userSettings.longitude != 0.0) userSettings.longitude else 30.5284) }
    var isLocating by remember { mutableStateOf(false) }
    var showManualCitySearchDialog by remember { mutableStateOf(false) }
    var manualSearchQuery by remember { mutableStateOf("") }

    // Account & Gamification State
    var studentNameInput by remember { mutableStateOf(if (userSettings.userName != "الطالب المجتهد" && userSettings.userName != "طالب العلم") userSettings.userName else "") }
    var studentAgeInput by remember { mutableIntStateOf(if (userSettings.studentAge in 6..40) userSettings.studentAge else 17) }
    var educationalStageInput by remember { mutableStateOf(userSettings.educationalStage.ifBlank { "HIGH_SCHOOL" }) }
    var gradeYearInput by remember { mutableStateOf(userSettings.studentGradeYear.ifBlank { "الصف الثالث الثانوي" }) }
    var studentGmailInput by remember { mutableStateOf(userSettings.userGmail) }
    var studentTelegramInput by remember { mutableStateOf(userSettings.telegramNumber.ifBlank { userSettings.whatsappNumber }) }
    var agreedToTerms by remember { mutableStateOf(true) }

    val languages = remember {
        listOf(
            AppLanguageOption("ar", "العربية", "الكعبة المشرفة (مكة المكرمة)", "🕋", "🇸🇦", Color(0xFF10B981)),
            AppLanguageOption("en", "English", "Big Ben (London)", "🕰️", "🇬🇧", Color(0xFF3B82F6)),
            AppLanguageOption("fr", "Français", "Tour Eiffel (Paris)", "🗼", "🇫🇷", Color(0xFF8B5CF6)),
            AppLanguageOption("es", "Español", "La Sagrada Família (Madrid)", "⛪", "🇪🇸", Color(0xFFF97316)),
            AppLanguageOption("de", "Deutsch", "Brandenburg Gate (Berlin)", "🏛️", "🇩🇪", Color(0xFF06B6D4)),
            AppLanguageOption("tr", "Türkçe", "Sultan Ahmed Mosque (Istanbul)", "🕌", "🇹🇷", Color(0xFFF59E0B)),
            AppLanguageOption("id", "Indonesia", "Borobudur Temple (Jakarta)", "🛕", "🇮🇩", Color(0xFFEF4444)),
            AppLanguageOption("ru", "Русский", "Saint Basil's Kremlin (Moscow)", "🏰", "🇷🇺", Color(0xFFEC4899)),
            AppLanguageOption("zh", "中文", "万里长城 (Great Wall)", "🏯", "🇨🇳", Color(0xFFDC2626))
        )
    }

    // Permission launcher for Location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            isLocating = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
                    val isNetEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

                    var lastLoc = if (isGpsEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    } else null

                    if (lastLoc == null && isNetEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        lastLoc = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }

                    if (lastLoc != null) {
                        detectedLat = lastLoc.latitude
                        detectedLng = lastLoc.longitude

                        try {
                            val geocoder = Geocoder(context, Locale(selectedLangCode))
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(lastLoc.latitude, lastLoc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "الموقع المكتشف"
                                val country = addr.countryName ?: "مصر"
                                detectedCity = "$city - $country"
                            }
                        } catch (_: Exception) {
                            detectedCity = "المحمودية - مصر"
                        }
                    } else {
                        detectedCity = "المحمودية - مصر"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLocating = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎓", fontSize = 18.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "رفيق الطالب",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (currentStep) {
                                    1 -> "الخطوة 1: حساب الطالب والتسجيل 🎓"
                                    2 -> "الخطوة 2: تحديد الموقع والقبلة 🧭"
                                    3 -> "الخطوة 3: تفعيل الإشعارات والمنبه 🔔"
                                    else -> "الخطوة 4: جاهز للانطلاق! 🚀"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Quick Language Switcher
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable {
                                val next = if (selectedLangCode == "ar") "en" else "ar"
                                selectedLangCode = next
                                viewModel.setAppLanguage(next)
                            }
                        ) {
                            Text(
                                text = if (selectedLangCode == "ar") "🌐 ع" else "🌐 EN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Quick Bypass / Skip Button for testing
                        FilledTonalButton(
                            onClick = {
                                viewModel.skipStudentAsGuest()
                                viewModel.completeOnboarding()
                                onFinishSetup()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تخطي للتجربة ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "$currentStep / $totalSteps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top Progress Step Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (stepIndex in 1..totalSteps) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (stepIndex <= currentStep) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1 && currentStep < totalSteps) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("السابق", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    when (currentStep) {
                        1 -> {
                            val nameValidation = validateFullName(studentNameInput)
                            val isNameValid = nameValidation.first
                            val telegramValidation = validatePhoneOrTelegramContact(studentTelegramInput)
                            val isTelegramValid = telegramValidation.first
                            val isFormValid = isNameValid && isTelegramValid

                            Button(
                                onClick = {
                                    if (!isNameValid) {
                                        Toast.makeText(context, nameValidation.second, Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    if (!isTelegramValid) {
                                        Toast.makeText(context, telegramValidation.second, Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    viewModel.updateComprehensiveStudentProfile(
                                        name = studentNameInput.trim(),
                                        age = studentAgeInput,
                                        stage = educationalStageInput,
                                        gradeYear = gradeYearInput,
                                        branch = "SCIENCE",
                                        gmail = studentGmailInput.trim(),
                                        whatsapp = studentTelegramInput.trim(),
                                        password = ""
                                    )
                                    currentStep = 2
                                    // Trigger location permission automatically on moving to step 2
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFormValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = if (isFormValid) "التالي (تحديد الموقع والقبلة 🧭)" else "اكتب الاسم ورقم التليجرام *",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                        2 -> {
                            Button(
                                onClick = {
                                    viewModel.updateLocation(detectedCity, "مصر", detectedLat, detectedLng)
                                    PrayerAlarmScheduler.rescheduleAll(context)
                                    currentStep = 3
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("الموقع مضبوط (التالي: الإشعارات 🔔)", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                        3 -> {
                            Button(
                                onClick = {
                                    currentStep = 4
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("التالي (إتمام الحساب والبدء 🚀)", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                        4 -> {
                            Button(
                                onClick = {
                                    viewModel.completeOnboarding()
                                    val firebaseUid = runCatching {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                    }.getOrNull() ?: ""
                                    if (studentTelegramInput.isNotBlank()) {
                                        DailyTelegramReportHelper.openTelegramBotForAccountLinking(
                                            context = context,
                                            phone = studentTelegramInput.trim(),
                                            appUserId = firebaseUid
                                        )
                                    }
                                    onFinishSetup()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("ابدأ رحلتك الآن مع رفيق الطالب 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentStep) {
                1 -> StudentAccountGamificationStep(
                    viewModel = viewModel,
                    userSettings = userSettings,
                    studentName = studentNameInput,
                    onStudentNameChange = { studentNameInput = it },
                    studentAge = studentAgeInput,
                    onAgeChange = { studentAgeInput = it },
                    stage = educationalStageInput,
                    onStageChange = {
                        educationalStageInput = it
                        gradeYearInput = when (it) {
                            "PRIMARY" -> "الصف السادس الابتدائي"
                            "MIDDLE" -> "الصف الثالث الإعدادي"
                            "HIGH_SCHOOL" -> "الصف الثالث الثانوي"
                            else -> "الفرقة الأولى جامعية"
                        }
                    },
                    gradeYear = gradeYearInput,
                    onGradeYearChange = { gradeYearInput = it },
                    gmail = studentGmailInput,
                    onGmailChange = { studentGmailInput = it },
                    telegram = studentTelegramInput,
                    onTelegramChange = { studentTelegramInput = it },
                    agreedToTerms = agreedToTerms,
                    onAgreedChange = { agreedToTerms = it },
                    onComplete = {
                        val nameValidation = validateFullName(studentNameInput)
                        val isNameValid = nameValidation.first
                        val telegramValidation = validatePhoneOrTelegramContact(studentTelegramInput)
                        val isTelegramValid = telegramValidation.first
                        if (!isNameValid) {
                            Toast.makeText(context, nameValidation.second, Toast.LENGTH_LONG).show()
                            return@StudentAccountGamificationStep
                        }
                        if (!isTelegramValid) {
                            Toast.makeText(context, telegramValidation.second, Toast.LENGTH_LONG).show()
                            return@StudentAccountGamificationStep
                        }
                        viewModel.updateComprehensiveStudentProfile(
                            name = studentNameInput.trim(),
                            age = studentAgeInput,
                            stage = educationalStageInput,
                            gradeYear = gradeYearInput,
                            branch = "SCIENCE",
                            gmail = studentGmailInput.trim(),
                            whatsapp = studentTelegramInput.trim(),
                            password = ""
                        )
                        currentStep = 2
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
                2 -> GpsLocationStep(
                    detectedCity = detectedCity,
                    detectedLat = detectedLat,
                    detectedLng = detectedLng,
                    isLocating = isLocating,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onOpenManualSearch = { showManualCitySearchDialog = true }
                )
                3 -> NotificationAndBatterySetupStep(
                    context = context,
                    userSettings = userSettings
                )
                4 -> ReadyToStartStep(
                    studentName = studentNameInput,
                    gradeYear = gradeYearInput,
                    city = detectedCity,
                    telegram = studentTelegramInput,
                    onStart = {
                        viewModel.completeOnboarding()
                        val firebaseUid = runCatching {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        }.getOrNull() ?: ""
                        if (studentTelegramInput.isNotBlank()) {
                            DailyTelegramReportHelper.openTelegramBotForAccountLinking(
                                context = context,
                                phone = studentTelegramInput.trim(),
                                appUserId = firebaseUid
                            )
                        }
                        onFinishSetup()
                    }
                )
            }
        }
    }

    // Manual City Search Dialog
    if (showManualCitySearchDialog) {
        val popularCities = remember {
            listOf(
                Triple("المحمودية - البحيرة - مصر", 31.1873, 30.5284),
                Triple("القاهرة - مصر", 30.0444, 31.2357),
                Triple("الإسكندرية - مصر", 31.2001, 29.9187),
                Triple("طنطا - الغربية - مصر", 30.7865, 31.0004),
                Triple("المنصورة - الدقهلية - مصر", 31.0409, 31.3785),
                Triple("مكة المكرمة - السعودية", 21.4225, 39.8262),
                Triple("المدينة المنورة - السعودية", 24.5247, 39.5692),
                Triple("الرياض - السعودية", 24.7136, 46.6753),
                Triple("دبي - الإمارات", 25.2048, 55.2708),
                Triple("القدس الشريف - فلسطين", 31.7683, 35.2137),
                Triple("إسطنبول - تركيا", 41.0082, 28.9784),
                Triple("باريس - فرنسا", 48.8566, 2.3522),
                Triple("لندن - بريطانيا", 51.5074, -0.1278),
                Triple("جاكرتا - إندونيسيا", -6.2088, 106.8456)
            )
        }

        val filteredCities = if (manualSearchQuery.isBlank()) {
            popularCities
        } else {
            popularCities.filter { it.first.contains(manualSearchQuery.trim(), ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showManualCitySearchDialog = false },
            title = {
                Text("اختر مدينتك يدوياً", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    OutlinedTextField(
                        value = manualSearchQuery,
                        onValueChange = { manualSearchQuery = it },
                        placeholder = { Text("ابحث عن المدينة أو المحافظة...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredCities.forEach { (city, lat, lng) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        detectedCity = city
                                        detectedLat = lat
                                        detectedLng = lng
                                        viewModel.updateLocation(city, "مصر", lat, lng)
                                        showManualCitySearchDialog = false
                                        Toast.makeText(context, "تم تحديد: $city", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(city, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManualCitySearchDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// STEP 1: LANGUAGE SELECTION (اختيار اللغة)
// -----------------------------------------------------------------------------------------

@Composable
private fun LanguageSelectionStep(
    languages: List<AppLanguageOption>,
    selectedLangCode: String,
    onSelectLanguage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Greeting Header Character Illustration & Bubble Text
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Speech Bubble
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "✨ السلام عليكم ورحمة الله وبركاته ✨",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Character Avatar & Greeting
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🧑‍🎓", fontSize = 44.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "مرحباً بك في تطبيق رفيق الطالب",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "اختر لغة التطبيق المفضلة لبدء رحلة التميز والتنظيم",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "اللغات المتاحة (7 لغات عالمية مع المعالم)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 7 Languages List with Landmark Icons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            languages.forEach { lang ->
                val isSelected = selectedLangCode == lang.code
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) lang.primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) lang.primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    shadowElevation = if (isSelected) 4.dp else 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLanguage(lang.code) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Landmark Emoji & Flag
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = lang.primaryColor.copy(alpha = 0.18f),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(lang.landmarkEmoji, fontSize = 22.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = lang.nameNative,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(lang.flagEmoji, fontSize = 16.sp)
                                }
                                Text(
                                    text = lang.landmarkName,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectLanguage(lang.code) },
                            colors = RadioButtonDefaults.colors(selectedColor = lang.primaryColor)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// STEP 2: GPS LOCATION & AUTO-QIBLA CALIBRATION (تحديد الموقع والقبلة)
// -----------------------------------------------------------------------------------------

@Composable
private fun GpsLocationStep(
    detectedCity: String,
    detectedLat: Double,
    detectedLng: Double,
    isLocating: Boolean,
    onRequestPermission: () -> Unit,
    onOpenManualSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location & Qibla Visual Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "تحديد الموقع والقبلة الدقيقة 🕋",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "نحتاج إذن الموقع الجغرافي (GPS) لحساب مواقيت الصلاة، الأذان بدقة المللي ثانية، وتوجيه بوصلة القبلة نحو الكعبة المشرفة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Detected Location Display
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الموقع المكتشف حالياً:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = detectedCity,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "إحداثيات: ${"%.3f".format(detectedLat)}° N , ${"%.3f".format(detectedLng)}° E",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLocating) {
                    Spacer(modifier = Modifier.height(10.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual search text link
        TextButton(
            onClick = onOpenManualSearch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.EditLocationAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "موقعك غير صحيح؟ اضغط هنا للبحث اليدوي",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("إعادة الكشف التلقائي عبر GPS")
        }
    }
}

// -----------------------------------------------------------------------------------------
// STEP 3: GAMIFIED STUDENT ACCOUNT & COMPETITION FLOW (تسجيل الدخول والتنافس)
// -----------------------------------------------------------------------------------------

@Composable
private fun StudentAccountGamificationStep(
    viewModel: MainViewModel,
    userSettings: UserSettings,
    studentName: String,
    onStudentNameChange: (String) -> Unit,
    studentAge: Int,
    onAgeChange: (Int) -> Unit,
    stage: String,
    onStageChange: (String) -> Unit,
    gradeYear: String,
    onGradeYearChange: (String) -> Unit,
    gmail: String,
    onGmailChange: (String) -> Unit,
    telegram: String,
    onTelegramChange: (String) -> Unit,
    agreedToTerms: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    val nameValidation = validateFullName(studentName)
    val isNameValid = nameValidation.first
    val telegramValidation = validatePhoneOrTelegramContact(telegram)
    val isTelegramValid = telegramValidation.first

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val telegramLinkState by viewModel.telegramLinkVerificationState.collectAsState()
    val isTelegramLinked = userSettings.isTelegramLinked || telegramLinkState is TelegramLinkState.Linked
    val firebaseUid = remember {
        runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        }.getOrNull() ?: ""
    }

    var hasAttemptedOpenBot by remember { mutableStateOf(false) }

    // Auto-check linking status when returning from Telegram app to the application
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cleanTel = telegram.trim()
                if (hasAttemptedOpenBot && cleanTel.isNotBlank()) {
                    viewModel.verifyTelegramLinkStatus(cleanTel, firebaseUid)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gamification Features Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🏆 ملف الطالب والتكامل مع التليجرام ✈️", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "أدخل بياناتك الإجبارية (الاسم ورقم/معرف التليجرام) لاستقبال الأذكار والمذكرات وجدول المذاكرة يومياً عبر البوت الرسمي!",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GamificationPill("⚡", "نقاط يومية")
                    GamificationPill("🥇", "أوسمة الشرف")
                    GamificationPill("🤖", "بوت التليجرام")
                    GamificationPill("🎁", "مكافآت التفوق")
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Student Name Field (MANDATORY)
        OutlinedTextField(
            value = studentName,
            onValueChange = onStudentNameChange,
            label = { Text("اسم الطالب بالكامل (إجباري) 🔴 *") },
            placeholder = { Text("مثال: عبد الرحمن صفوت") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (isNameValid) Color(0xFF10B981) else MaterialTheme.colorScheme.error) },
            isError = studentName.isNotBlank() && !isNameValid,
            supportingText = {
                if (studentName.isNotBlank() && !isNameValid) {
                    Text(nameValidation.second, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                } else if (!isNameValid) {
                    Text(nameValidation.second, color = Color(0xFFE65100), fontSize = 11.sp)
                } else {
                    Text(nameValidation.second, color = Color(0xFF10B981), fontSize = 11.sp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Telegram Number / Username Field (MANDATORY)
        OutlinedTextField(
            value = telegram,
            onValueChange = onTelegramChange,
            label = { Text("رقم أو معرف التليجرام (إجباري) 🔴 *") },
            placeholder = { Text("مثال: 01012345678 (11 رقماً بالضبط) أو @username") },
            leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, tint = if (isTelegramValid) Color(0xFF0284C7) else MaterialTheme.colorScheme.error) },
            isError = telegram.isNotBlank() && !isTelegramValid,
            supportingText = {
                if (telegram.isNotBlank() && !isTelegramValid) {
                    Text(telegramValidation.second, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                } else if (!isTelegramValid) {
                    Text(telegramValidation.second, color = Color(0xFFE65100), fontSize = 11.sp)
                } else {
                    Text(telegramValidation.second, color = Color(0xFF10B981), fontSize = 11.sp)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Telegram App Launch Button
        Button(
            onClick = {
                if (isTelegramValid) {
                    hasAttemptedOpenBot = true
                    DailyTelegramReportHelper.openTelegramBotForAccountLinking(
                        context = context,
                        phone = telegram.trim(),
                        appUserId = firebaseUid
                    )
                } else {
                    Toast.makeText(context, telegramValidation.second, Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTelegramLinked) Color(0xFF10B981) else Color(0xFF0284C7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTelegramLinked) "تطبيق Telegram مربوط ومفعل ✅" else "ربط Telegram (فتح التطبيق مباشرة) ✈️",
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Telegram Connection Status & Backend Verification Box
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isTelegramLinked) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (isTelegramLinked) Color(0xFF10B981).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isTelegramLinked) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isTelegramLinked) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isTelegramLinked) "🟢 Telegram Connected" else "🟡 Telegram Not Linked Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (isTelegramLinked) Color(0xFF047857) else Color(0xFFB45309)
                            )
                            val chatId = (telegramLinkState as? TelegramLinkState.Linked)?.chatId
                                ?: userSettings.telegramChatId.takeIf { it != 0L }
                            Text(
                                text = if (isTelegramLinked) {
                                    if (chatId != null && chatId != 0L) {
                                        "تم تأكيد الربط السحابي (Chat ID: $chatId) ✅"
                                    } else {
                                        "تم التحقق وتفعيل إشعارات وتذكيرات البوت بنجاح ✅"
                                    }
                                } else {
                                    "اضغط على الزر أعلاه، ثم اضغط Start في البوت وعد للتحقق"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            val cleanTel = telegram.trim()
                            if (cleanTel.isNotBlank()) {
                                viewModel.verifyTelegramLinkStatus(cleanTel, firebaseUid) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "يرجى كتابة رقم الهاتف أو معرف تليجرام أولاً", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = telegramLinkState !is TelegramLinkState.Checking
                    ) {
                        if (telegramLinkState is TelegramLinkState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تحقق 🔄", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Student Age Stepper
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "عمر الطالب: $studentAge سنة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { if (studentAge > 6) onAgeChange(studentAge - 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "$studentAge", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { if (studentAge < 40) onAgeChange(studentAge + 1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Educational Stage Chips
        Text(
            text = "المرحلة الدراسية:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "PRIMARY" to "الابتدائية",
                "MIDDLE" to "الإعدادية",
                "HIGH_SCHOOL" to "الثانوية",
                "UNIVERSITY" to "الجامعية"
            ).forEach { (sKey, sLabel) ->
                FilterChip(
                    selected = stage == sKey,
                    onClick = { onStageChange(sKey) },
                    label = { Text(sLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Grade Year Chips
        Text(
            text = "السنة الدراسية:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        val gradeOptions = when (stage) {
            "PRIMARY" -> listOf("الصف الرابع الابتدائي", "الصف الخامس الابتدائي", "الصف السادس الابتدائي")
            "MIDDLE" -> listOf("الصف الأول الإعدادي", "الصف الثاني الإعدادي", "الصف الثالث الإعدادي")
            "HIGH_SCHOOL" -> listOf("الصف الأول الثانوي", "الصف الثاني الثانوي", "الصف الثالث الثانوي")
            else -> listOf("الفرقة الأولى جامعية", "الفرقة الثانية", "الفرقة الثالثة", "الفرقة الرابعة")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            gradeOptions.forEach { opt ->
                FilterChip(
                    selected = gradeYear == opt,
                    onClick = { onGradeYearChange(opt) },
                    label = { Text(opt, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Gmail Account Field
        OutlinedTextField(
            value = gmail,
            onValueChange = onGmailChange,
            label = { Text("حساب الجيميل (اختياري للمزامنة)") },
            placeholder = { Text("example@gmail.com") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFEA4335)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Terms & Conditions Consent Note
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAgreedChange(!agreedToTerms) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = agreedToTerms,
                    onCheckedChange = onAgreedChange,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "أوافق على ربط الحساب ببوت تليجرام رفيق الطالب واستقبال التذكيرات اليومية والأذكار.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun GamificationPill(emoji: String, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.16f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// -----------------------------------------------------------------------------------------
// STEP 2: NOTIFICATION & BACKGROUND BATTERY RELIABILITY SETUP (تهيئة الإشعارات والبطارية)
// -----------------------------------------------------------------------------------------

@Composable
private fun NotificationAndBatterySetupStep(
    context: Context,
    userSettings: UserSettings
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var diagnostics by remember {
        mutableStateOf(com.example.domain.notification.NotificationReliabilityManager.getFullDiagnostics(context))
    }
    var isRepairing by remember { mutableStateOf(false) }

    // Runtime permission launcher for POST_NOTIFICATIONS
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        diagnostics = com.example.domain.notification.NotificationReliabilityManager.getFullDiagnostics(context)
    }

    // Auto-update live state when user returns from any system settings screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                diagnostics = com.example.domain.notification.NotificationReliabilityManager.getFullDiagnostics(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Banner
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = if (diagnostics.isFullPass) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(
                1.dp,
                if (diagnostics.isFullPass) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (diagnostics.isFullPass) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (diagnostics.isFullPass) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "تهيئة الإشعارات واستمرارية الخلفية ⚡",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "لضمان تشغيل الأذان والأذكار وتذكيرات المذاكرة بدقة 100% حتى عند قفل الشاشة وفي وضع السكون (Android 13/14/15/16).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step-by-Step Permission Cards (تظهر واحدة تلو الأخرى بشكل منظم وتفاعلي)

        // 1. POST_NOTIFICATIONS Permission Card
        val isNotifGranted = diagnostics.notificationsStatus == com.example.domain.notification.NotificationReliabilityManager.HealthStatus.PASS
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isNotifGranted) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isNotifGranted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (isNotifGranted) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = if (isNotifGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("1. إذن التنبيهات والإشعارات", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("إظهار تنبيهات الصلاة والمذاكرة والأذكار", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isNotifGranted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isNotifGranted) "مفعل ✅" else "معطل ❌",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNotifGranted) Color(0xFF047857) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!isNotifGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                com.example.domain.notification.NotificationReliabilityManager.openNotificationSettings(context)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تفعيل إذن الإشعارات الآن 🔔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Exact Alarms Card (SCHEDULE_EXACT_ALARM)
        val isExactGranted = diagnostics.exactAlarmsStatus == com.example.domain.notification.NotificationReliabilityManager.HealthStatus.PASS ||
                diagnostics.exactAlarmsStatus == com.example.domain.notification.NotificationReliabilityManager.HealthStatus.NOT_REQUIRED
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isExactGranted) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isExactGranted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (isExactGranted) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (isExactGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("2. إذن المنبهات الدقيقة (Exact Alarms)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("تشغيل الأذان في نفس اللحظة بالثانية", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isExactGranted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isExactGranted) "مسموح ✅" else "مقيد ⚠️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExactGranted) Color(0xFF047857) else Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!isExactGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            com.example.domain.notification.NotificationReliabilityManager.openExactAlarmSettings(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AlarmOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("منح إذن المنبه الدقيق ⏰", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Ignore Battery Optimizations (Doze Mode Exemption)
        val isBatteryIgnored = diagnostics.batteryOptimizationStatus == com.example.domain.notification.NotificationReliabilityManager.HealthStatus.PASS
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isBatteryIgnored) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (isBatteryIgnored) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = if (isBatteryIgnored) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = if (isBatteryIgnored) Color(0xFF10B981) else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("3. استثناء توفير البطارية (Doze Mode)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text("منع إيقاف التنبيهات عند إغلاق الشاشة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBatteryIgnored) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isBatteryIgnored) "مستثنى ✅" else "محسّن ⚠️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBatteryIgnored) Color(0xFF047857) else Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!isBatteryIgnored) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            com.example.domain.notification.NotificationReliabilityManager.requestIgnoreBatteryOptimizations(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استثناء التطبيق من توفير الطاقة ⚡", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. OEM AutoStart Settings Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("4. التشغيل التلقائي للمصنّع (OEM)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("لأجهزة شاومي، هواوي، سامسونج، أوبو...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val opened = com.example.domain.notification.NotificationReliabilityManager.openOemAutoStartSettings(context)
                        if (!opened) {
                            Toast.makeText(context, "يرجى التحقق من التشغيل التلقائي من إعدادات الهاتف", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("فتح 📱", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Instant Test & Verification Action Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "اختبار وتأكيد وصول الإشعارات بنسبة 100% 🎯",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "اضغط على الزر أدناه لإرسال إشعار تجريبي فوري والتأكد من ظهوره وصوته.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            com.example.domain.notification.NotificationReliabilityManager.sendTestNotification(context)
                            Toast.makeText(context, "تم إرسال إشعار تجريبي فوري! تأكد من شريط الإشعارات 🔔", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إرسال إشعار تجريبي فوري 🔔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isRepairing = true
                            diagnostics = com.example.domain.notification.NotificationReliabilityManager.oneTapRepairAndReschedule(context)
                            isRepairing = false
                            Toast.makeText(context, "تم فحص وإعادة جدولة كافة التنبيهات بنجاح ✅", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// STEP 4: READY TO START CELEBRATION (إتمام التهيئة والانطلاق)
// -----------------------------------------------------------------------------------------

@Composable
private fun ReadyToStartStep(
    studentName: String,
    gradeYear: String,
    city: String,
    telegram: String,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("🎓", fontSize = 42.sp)
        }

        Text(
            text = "أهلاً بك يا ${if (studentName.isNotBlank()) studentName else "بطل"}!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Text(
            text = "تم ضبط بيانات حسابك وموقعك وجدول إشعاراتك بنجاح. أنت الآن جاهز لبدء تنظيم دراستك ومتابعة عاداتك وتحدياتك اليومية.",
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Summary Cards
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("مكافأة البداية: +100 نقطة خبرة (XP)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("تمت إضافتها لحسابك لتبدأ بقوة في لوحة شرف الطلاب", fontSize = 11.5.sp, color = Color(0xFF10B981))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("الموقع المعتمد للحساب", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(if (city.isNotBlank()) city else "المحمودية - مصر", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("منظومة الإشعارات والمنبه الدقيق", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text("جاهزة لتنبيهك بالعادات ومواقيت الصلاة حتى والتطبيق مغلق", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (telegram.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("ربط تليجرام وبوت المتابعة", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            Text(telegram, fontSize = 11.5.sp, color = Color(0xFF0284C7))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onStart,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(6.dp, RoundedCornerShape(18.dp))
        ) {
            Text("ابدأ رحلتك الآن مع رفيق الطالب 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
