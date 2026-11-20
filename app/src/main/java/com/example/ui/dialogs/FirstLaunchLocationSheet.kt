package com.example.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.prayer.CityLocationPreset
import com.example.domain.prayer.LocationPrayerManager
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import kotlinx.coroutines.launch

private val LocationSheetCharcoal = Color(0xFF1E1B18)
private val LocationSheetRoyalBlue = Color(0xFF1A6FE3)
private val LocationSheetAmberGold = Color(0xFFD97706)
private val LocationSheetTealTrack = Color(0xFF0F6B75)

@Composable
fun FirstLaunchLocationSheet(
    currentCalculationMethod: String,
    onLocationConfigured: (cityDisplay: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { UserPreferencesRepository(context) }

    var autoLockLocation by remember { mutableStateOf(true) }
    var isDetectingGps by remember { mutableStateOf(false) }
    var showManualCities by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var citySearchQuery by remember { mutableStateOf("") }

    fun triggerGpsCapture() {
        if (isDetectingGps) return
        scope.launch {
            isDetectingGps = true
            errorMessage = null
            val res = LocationPrayerManager.detectGpsAndSave(
                context = context,
                prefsRepo = prefsRepo,
                currentCalcMethod = currentCalculationMethod,
                autoLock = autoLockLocation
            )
            isDetectingGps = false
            if (res.isSuccess) {
                val loc = res.getOrThrow()
                Toast.makeText(
                    context,
                    "تم تثبيت موقعك: ${loc.cityName}، ${loc.countryName} ✅",
                    Toast.LENGTH_LONG
                ).show()
                onLocationConfigured("${loc.cityName}، ${loc.countryName}")
            } else {
                errorMessage = res.exceptionOrNull()?.localizedMessage
                    ?: "تعذر التقاط الموقع تلقائياً. اختر مدينتك من القائمة بالأسفل."
                showManualCities = true
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            triggerGpsCapture()
        } else {
            errorMessage = "تم رفض إذن GPS. يمكنك اختيار مدينتك يدوياً في ضغطة واحدة أدناه:"
            showManualCities = true
        }
    }

    val filteredCities = remember(citySearchQuery) {
        val q = citySearchQuery.trim()
        if (q.isEmpty()) {
            LocationPrayerManager.presetCities
        } else {
            LocationPrayerManager.presetCities.filter {
                it.cityNameAr.contains(q, ignoreCase = true) ||
                    it.countryNameAr.contains(q, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Mandatory first-launch city/GPS setup ensures accurate Adhan */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocationSheetCharcoal.copy(alpha = 0.72f))
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // Main Elevated Surface Card (starts 52.dp down to let glowing pin badge overlap cleanly)
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 18.dp,
                        border = BorderStroke(1.5.dp, LocationSheetAmberGold.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 52.dp)
                            .testTag("first_launch_location_sheet")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 64.dp, start = 20.dp, end = 20.dp, bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "تَحْدِيدُ الْمَوْقِعِ الْجُغْرَافِيِّ:",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "لتشغيل مواقيت الصلاة والأذان التلقائي بدقة في مدينتك مرة واحدة فقط",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 14.5.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // Auto-lock toggle pill matching reference UI
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "تثبيت الموقع تلقائياً دائماً",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Switch(
                                        checked = autoLockLocation,
                                        onCheckedChange = { autoLockLocation = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = LocationSheetTealTrack
                                        ),
                                        modifier = Modifier.testTag("first_launch_auto_lock_switch")
                                    )
                                }
                            }

                            if (!errorMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = LocationSheetAmberGold.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, LocationSheetAmberGold.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            // Primary Royal Blue Pill CTA with Amber Gold Border
                            Button(
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasFine || hasCoarse) {
                                        triggerGpsCapture()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                enabled = !isDetectingGps,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocationSheetRoyalBlue,
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.8.dp, LocationSheetAmberGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("first_launch_detect_gps_button")
                            ) {
                                if (isDetectingGps) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "جاري التقاط موقعك...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "تحديد الموقع الآن",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "تحديد الموقع الآن",
                                        fontFamily = DisplayArabicFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            TextButton(
                                onClick = { showManualCities = !showManualCities },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("first_launch_manual_toggle")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = LocationSheetRoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showManualCities) "إخفاء قائمة المدن" else "أو اختر مدينتك / محافظتك يدوياً",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = LocationSheetRoyalBlue
                                )
                            }

                            // Manual City Fallback Grid
                            AnimatedVisibility(visible = showManualCities) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = citySearchQuery,
                                        onValueChange = { citySearchQuery = it },
                                        placeholder = { Text("ابحث عن مدينتك (القاهرة، مكة، الإسكندرية...)", fontSize = 12.5.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "بحث المدينة",
                                                tint = LocationSheetRoyalBlue
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                    ) {
                                        items(filteredCities) { preset ->
                                            ManualCityChip(
                                                preset = preset,
                                                onSelect = {
                                                    scope.launch {
                                                        isDetectingGps = true
                                                        LocationPrayerManager.saveManualCityAndSchedule(
                                                            context = context,
                                                            prefsRepo = prefsRepo,
                                                            preset = preset,
                                                            autoLock = autoLockLocation
                                                        )
                                                        isDetectingGps = false
                                                        Toast.makeText(
                                                            context,
                                                            "تم تثبيت موقعك: ${preset.cityNameAr}، ${preset.countryNameAr} ✅",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        onLocationConfigured("${preset.cityNameAr}، ${preset.countryNameAr}")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Top Overlapping Glowing Halo Pin Emblem (matches reference screenshot)
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .shadow(14.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color(0xFFEBF3FE),
                                        Color(0xFFDBEAFE)
                                    )
                                )
                            )
                            .border(2.dp, LocationSheetAmberGold.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = LocationSheetRoyalBlue.copy(alpha = 0.10f),
                                radius = size.minDimension * 0.44f,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                                    )
                                )
                                .border(2.dp, LocationSheetAmberGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "موقع الصلاة",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualCityChip(
    preset: CityLocationPreset,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, LocationSheetRoyalBlue.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = preset.cityNameAr,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.countryNameAr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
