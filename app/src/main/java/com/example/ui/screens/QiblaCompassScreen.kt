package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.prayer.CalculationMethod
import com.example.domain.prayer.CountryCityInfo
import com.example.domain.prayer.PrayerDataConstants
import com.example.ui.MainViewModel
import com.example.ui.screens.islamic.IslamicTopBar
import com.example.ui.screens.islamic.KaabaLineIcon
import com.example.ui.theme.DisplayArabicFontFamily
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

private const val KAABA_LAT = 21.422487
private const val KAABA_LNG = 39.826206

private fun calculateQiblaBearing(latDeg: Double, lngDeg: Double): Float {
    val lat1 = Math.toRadians(latDeg)
    val lon1 = Math.toRadians(lngDeg)
    val lat2 = Math.toRadians(KAABA_LAT)
    val lon2 = Math.toRadians(KAABA_LNG)
    val dLon = lon2 - lon1
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x)).toFloat()
    return (bearing + 360f) % 360f
}

private fun calculateHaversineKm(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): Int {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2Deg - lat1Deg)
    val dLon = Math.toRadians(lon2Deg - lon1Deg)
    val a = sin(dLat / 2).pow(2.0) +
        cos(Math.toRadians(lat1Deg)) * cos(Math.toRadians(lat2Deg)) *
        sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadiusKm * c).roundToInt()
}

private fun smoothCircularAngle(previous: Float, target: Float, alpha: Float = 0.22f): Float {
    var diff = ((target - previous + 540f) % 360f) - 180f
    return (previous + diff * alpha + 360f) % 360f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaCompassScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isDetectingLocation by viewModel.isDetectingLocation.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.45f
    }

    var azimuth by remember { mutableFloatStateOf(0f) }
    var continuousDisplayAngle by remember { mutableFloatStateOf(0f) }
    var hasCompassSensor by remember { mutableStateOf(true) }
    var showLocationSheet by remember { mutableStateOf(false) }
    var showCityPickerDialog by remember { mutableStateOf(false) }
    var showManualCoordsDialog by remember { mutableStateOf(false) }

    val qiblaTargetAngle = remember(userSettings.latitude, userSettings.longitude) {
        calculateQiblaBearing(userSettings.latitude, userSettings.longitude)
    }
    val distanceKm = remember(userSettings.latitude, userSettings.longitude) {
        calculateHaversineKm(userSettings.latitude, userSettings.longitude, KAABA_LAT, KAABA_LNG)
    }
    val formattedDistance = remember(distanceKm) {
        NumberFormat.getIntegerInstance(Locale.US).format(distanceKm)
    }

    // GPS Permission launcher for "موقعي الحالي"
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.detectAndApplyGpsLocation(context) { success, msg ->
                Toast.makeText(
                    context,
                    if (success) "تم تحديث الموقع بنجاح: $msg" else msg,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                context,
                "يرجى السماح بصلاحية الموقع لتحديد اتجاه القبلة بدقة",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Real Android Magnetic / Rotation Vector Sensor Listener
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        hasCompassSensor = rotationSensor != null || (accelSensor != null && magnetSensor != null)

        val accelReadings = FloatArray(3)
        val magnetReadings = FloatArray(3)
        var hasAccel = false
        var hasMagnet = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                var newDeg: Float? = null
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        newDeg = ((Math.toDegrees(orientation[0].toDouble()).toFloat()) + 360f) % 360f
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelReadings, 0, 3)
                        hasAccel = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetReadings, 0, 3)
                        hasMagnet = true
                    }
                }
                if (newDeg == null && hasAccel && hasMagnet) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, i, accelReadings, magnetReadings)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)
                        newDeg = ((Math.toDegrees(orientation[0].toDouble()).toFloat()) + 360f) % 360f
                    }
                }
                if (newDeg != null) {
                    val smoothed = smoothCircularAngle(azimuth, newDeg, 0.25f)
                    azimuth = smoothed
                    val delta = ((smoothed - (continuousDisplayAngle % 360f) + 540f) % 360f) - 180f
                    continuousDisplayAngle += delta
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelSensor != null && magnetSensor != null) {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    val animatedAngle by animateFloatAsState(
        targetValue = continuousDisplayAngle,
        animationSpec = tween(durationMillis = 120),
        label = "qiblaDialAngle"
    )
    val displayAzimuthDeg = ((azimuth.roundToInt() % 360) + 360) % 360
    val diffToQibla = abs(((azimuth - qiblaTargetAngle + 540f) % 360f) - 180f)
    val isFacingQibla = hasCompassSensor && diffToQibla <= 5.5f

    var wasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(isFacingQibla) {
        if (isFacingQibla && !wasAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasAligned = isFacingQibla
    }

    val alignedEmerald = Color(0xFF059669)
    val alignedMintBg = if (isDark) Color(0xFF064E3B) else Color(0xFFDDF5E6)
    val compassCardBg by animateColorAsState(
        targetValue = if (isFacingQibla) {
            if (isDark) Color(0xFF082E23) else Color(0xFFF3FCF6)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "compassCardBg"
    )
    val compassBorderColor by animateColorAsState(
        targetValue = if (isFacingQibla) alignedEmerald.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        label = "compassBorderColor"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IslamicTopBar(
                title = "اتجاه القبلة",
                onBack = onNavigateBack,
                trailingAction = {
                    IconButton(
                        onClick = { showLocationSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("qibla_edit_location_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditLocationAlt,
                            contentDescription = "تغيير الموقع",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =================================================================
            // 1. LOCATION HEADER CARD (Image 3 & Image 5)
            // =================================================================
            Surface(
                onClick = { showLocationSheet = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qibla_location_header")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = primaryColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                KaabaLineIcon(
                                    tint = primaryColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "موقعك الحالي",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = userSettings.cityLocation.ifBlank { "القاهرة" },
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = if (isDetectingLocation) "جاري تحديد الموقع بالـ GPS..." else "${userSettings.countryLocation.ifBlank { "مصر" }} • GPS / مدينة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${qiblaTargetAngle.roundToInt()}°",
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "من الشمال",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // =================================================================
            // 2. LIVE INTERACTIVE QIBLA COMPASS CARD (Images 3 & 5)
            // =================================================================
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = compassCardBg,
                shadowElevation = 2.dp,
                border = BorderStroke(1.2.dp, compassBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qibla_compass_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Title + Compass Badge + Live Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = primaryColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Explore,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "البوصلة",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Status Pill: متوافق | مباشر | ثابت
                        val statusBg = when {
                            !hasCompassSensor -> MaterialTheme.colorScheme.surfaceVariant
                            isFacingQibla -> Color(0xFFD1FAE5)
                            else -> primaryColor.copy(alpha = 0.12f)
                        }
                        val statusFg = when {
                            !hasCompassSensor -> MaterialTheme.colorScheme.onSurfaceVariant
                            isFacingQibla -> alignedEmerald
                            else -> primaryColor
                        }
                        val statusLabel = when {
                            !hasCompassSensor -> "ثابت"
                            isFacingQibla -> "متوافق"
                            else -> "مباشر"
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusBg,
                            border = BorderStroke(1.dp, statusFg.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusFg)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusLabel,
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusFg
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Rotating Compass Dial (268.dp)
                    val dialTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    val dialMajorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    val dialLabelColor = MaterialTheme.colorScheme.onSurface.toArgb()
                    val needlePrimary = if (isFacingQibla) alignedEmerald else primaryColor
                    val dialRingFill = if (isFacingQibla) alignedMintBg else {
                        if (isDark) Color(0xFF1A253A) else Color(0xFFF3F6FA)
                    }

                    Box(
                        modifier = Modifier
                            .size(268.dp)
                            .testTag("qibla_compass_dial"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val outerRadius = size.minDimension / 2f - 10.dp.toPx()

                            // Soft radial gradient dial background
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        dialRingFill.copy(alpha = 0.65f),
                                        dialRingFill
                                    ),
                                    center = center,
                                    radius = outerRadius
                                ),
                                radius = outerRadius,
                                center = center
                            )

                            // Outer ring border
                            drawCircle(
                                color = if (isFacingQibla) alignedEmerald.copy(alpha = 0.35f) else dialTickColor.copy(alpha = 0.35f),
                                radius = outerRadius,
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // Fixed Top Indicator Triangle + Alignment Arc
                            val topTri = Path().apply {
                                val tipY = center.y - outerRadius + 2.dp.toPx()
                                moveTo(center.x, tipY)
                                lineTo(center.x - 6.dp.toPx(), tipY - 9.dp.toPx())
                                lineTo(center.x + 6.dp.toPx(), tipY - 9.dp.toPx())
                                close()
                            }
                            drawPath(
                                path = topTri,
                                color = if (isFacingQibla) alignedEmerald else Color(0xFF0F172A)
                            )

                            if (isFacingQibla) {
                                drawArc(
                                    color = alignedEmerald,
                                    startAngle = -102f,
                                    sweepAngle = 24f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                                    size = androidx.compose.ui.geometry.Size(outerRadius * 2f, outerRadius * 2f),
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            // Rotating Compass Rose (ticks + cardinal labels)
                            rotate(degrees = -animatedAngle, pivot = center) {
                                for (deg in 0 until 360 step 5) {
                                    val isMajor = deg % 90 == 0
                                    val isMid = deg % 30 == 0
                                    val tickLen = when {
                                        isMajor -> 14.dp.toPx()
                                        isMid -> 10.dp.toPx()
                                        else -> 6.dp.toPx()
                                    }
                                    val rad = Math.toRadians((deg - 90).toDouble())
                                    val startR = outerRadius - 3.dp.toPx()
                                    val endR = startR - tickLen
                                    drawLine(
                                        color = if (isMajor) dialMajorTickColor else dialTickColor,
                                        start = Offset(
                                            center.x + startR * cos(rad).toFloat(),
                                            center.y + startR * sin(rad).toFloat()
                                        ),
                                        end = Offset(
                                            center.x + endR * cos(rad).toFloat(),
                                            center.y + endR * sin(rad).toFloat()
                                        ),
                                        strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }

                                // Draw Arabic cardinal labels inside ring
                                val labelPaint = Paint().apply {
                                    color = dialLabelColor
                                    textSize = 14.sp.toPx()
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                    textAlign = Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                val cardinals = listOf(
                                    0f to "شمال",
                                    90f to "شرق",
                                    180f to "جنوب",
                                    270f to "غرب"
                                )
                                val labelRadius = outerRadius - 30.dp.toPx()
                                cardinals.forEach { (angleDeg, label) ->
                                    val rad = Math.toRadians((angleDeg - 90f).toDouble())
                                    val lx = center.x + labelRadius * cos(rad).toFloat()
                                    val ly = center.y + labelRadius * sin(rad).toFloat() + 5.dp.toPx()
                                    drawContext.canvas.nativeCanvas.drawText(label, lx, ly, labelPaint)
                                }
                            }

                            // Qibla Needle pointing toward Kaaba relative to device heading
                            val needleAngle = qiblaTargetAngle - animatedAngle
                            rotate(degrees = needleAngle, pivot = center) {
                                // Counter-tail
                                val tailPath = Path().apply {
                                    moveTo(center.x - 9.dp.toPx(), center.y)
                                    lineTo(center.x + 9.dp.toPx(), center.y)
                                    lineTo(center.x + 5.dp.toPx(), center.y + outerRadius * 0.28f)
                                    lineTo(center.x - 5.dp.toPx(), center.y + outerRadius * 0.28f)
                                    close()
                                }
                                drawPath(
                                    path = tailPath,
                                    color = dialTickColor.copy(alpha = 0.28f)
                                )

                                // Main sharp pointer needle toward Qibla
                                val tipY = center.y - outerRadius + 24.dp.toPx()
                                val pointerPath = Path().apply {
                                    moveTo(center.x, tipY)
                                    lineTo(center.x + 11.dp.toPx(), center.y)
                                    lineTo(center.x - 11.dp.toPx(), center.y)
                                    close()
                                }
                                drawPath(path = pointerPath, color = needlePrimary)

                                // Golden Kaaba Target Marker near Needle Tip
                                val tipDotY = tipY + 4.dp.toPx()
                                drawCircle(
                                    color = Color(0xFFF59E0B),
                                    radius = 9.dp.toPx(),
                                    center = Offset(center.x, tipDotY)
                                )
                                drawCircle(
                                    color = Color(0xFF1F2937),
                                    radius = 4.dp.toPx(),
                                    center = Offset(center.x, tipDotY)
                                )
                            }
                        }

                        // Central Kaaba Badge Emblem
                        Surface(
                            shape = CircleShape,
                            color = if (isFacingQibla) alignedEmerald else Color(0xFF111827),
                            border = BorderStroke(3.dp, Color.White),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                KaabaLineIcon(
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidthDp = 1.7.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Two-column Degrees Summary (137° القبلة من الشمال | 134° اتجاه الموبايل)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${qiblaTargetAngle.roundToInt()}°",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "القبلة من الشمال",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (hasCompassSensor) "${displayAzimuthDeg}°" else "--°",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isFacingQibla) alignedEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "اتجاه الموبايل",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Alignment Instruction Line
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isFacingQibla) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                !hasCompassSensor -> "الجهاز لا يحتوي على حساس بوصلة — استرشد بالزاوية ${qiblaTargetAngle.roundToInt()}° من الشمال."
                                isFacingQibla -> "أنت في اتجاه القبلة — كده تقدر تصلّي."
                                else -> "لفّ الموبايل ببطء لحد ما تبقى فوقه السهم."
                            },
                            fontFamily = DisplayArabicFontFamily,
                            fontSize = 14.sp,
                            fontWeight = if (isFacingQibla) FontWeight.Bold else FontWeight.Medium,
                            color = if (isFacingQibla) alignedEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // =================================================================
            // 3. DISTANCE & SHORTEST GREAT-CIRCLE PATH GLOBE CARD (Image 4)
            // =================================================================
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = primaryColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "المسافة والمسار",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "أقصر خط على سطح الأرض بينك وبين الكعبة",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formattedDistance,
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "كم للكعبة",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3D Orthographic Wireframe Globe with Geodesic Arc
                    GreatCircleGlobeView(
                        userLat = userSettings.latitude,
                        userLng = userSettings.longitude,
                        primaryColor = primaryColor,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Legend Row: موقعك | الكعبة | أقصر مسار
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("موقعك", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("الكعبة", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.width(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Route,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("أقصر مسار", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // =================================================================
            // 4. QUICK TIPS CARD (نصائح سريعة — Image 4)
            // =================================================================
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "نصائح سريعة",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val tips = listOf(
                        "الزاوية المحسوبة من الشمال وباتجاه عقارب الساعة، من موقعك الحالي سواء جاي من الـ GPS أو مدينة اخترتها.",
                        "البوصلة بتعتمد على مغناطيسية الموبايل. ابعد عن المعادن والأجهزة الكهربائية وقت الضبط، ولو الدقة منخفضة حرّك الموبايل على شكل رقم 8.",
                        "لو الجهاز مافيهوش حساس بوصلة، هتشوف الزاوية بالدرجات وسهم ثابت من الشمال بدل البوصلة الحية.",
                        "الاتجاه ده إرشادي — لو فيه فرق واضح، اتجاه المسجد هو المرجع."
                    )

                    tips.forEach { tip ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                            )
                            Text(
                                text = tip,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // =========================================================================
    // LOCATION PICKER BOTTOM SHEET («موقع الصلاة» — Image 3)
    // =========================================================================
    if (showLocationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLocationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "موقع الصلاة",
                    fontFamily = DisplayArabicFontFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LocationOptionRowCard(
                    title = "موقعي الحالي",
                    subtitle = "نحدد مدينتك بالـ GPS وبنحدّثها لو سافرت",
                    icon = Icons.Outlined.LocationOn,
                    accent = primaryColor,
                    onClick = {
                        showLocationSheet = false
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )

                LocationOptionRowCard(
                    title = "اختار مدينة",
                    subtitle = "كل محافظات مصر — من غير إنترنت",
                    icon = Icons.Outlined.LocationCity,
                    accent = primaryColor,
                    onClick = {
                        showLocationSheet = false
                        showCityPickerDialog = true
                    }
                )

                LocationOptionRowCard(
                    title = "إحداثيات يدوية",
                    subtitle = "لو عندك خط العرض والطول",
                    icon = Icons.Outlined.Explore,
                    accent = primaryColor,
                    onClick = {
                        showLocationSheet = false
                        showManualCoordsDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showCityPickerDialog) {
        OfflineCityPickerDialog(
            currentCity = userSettings.cityLocation,
            onCitySelected = { cityInfo ->
                viewModel.updateLocationAndCountry(
                    city = cityInfo.cityArabic,
                    country = cityInfo.countryArabic,
                    lat = cityInfo.latitude,
                    lng = cityInfo.longitude,
                    method = cityInfo.defaultMethod
                )
                showCityPickerDialog = false
                Toast.makeText(context, "تم ضبط الموقع على ${cityInfo.cityArabic}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCityPickerDialog = false }
        )
    }

    if (showManualCoordsDialog) {
        ManualCoordinatesDialog(
            initialCity = userSettings.cityLocation,
            initialLat = userSettings.latitude,
            initialLng = userSettings.longitude,
            onSave = { cityName, lat, lng ->
                val method = try {
                    CalculationMethod.valueOf(userSettings.calculationMethod)
                } catch (_: Exception) {
                    CalculationMethod.EGYPTIAN
                }
                viewModel.updateLocationAndCountry(
                    city = cityName.ifBlank { "موقع يدوي" },
                    country = userSettings.countryLocation.ifBlank { "مصر" },
                    lat = lat,
                    lng = lng,
                    method = method
                )
                showManualCoordsDialog = false
                Toast.makeText(context, "تم حفظ الإحداثيات وحساب اتجاه القبلة", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showManualCoordsDialog = false }
        )
    }
}

@Composable
private fun GreatCircleGlobeView(
    userLat: Double,
    userLng: Double,
    primaryColor: Color,
    isDark: Boolean
) {
    val globeBg = if (isDark) Color(0xFF152538) else Color(0xFFEAF4F5)
    val gridStroke = if (isDark) Color.White.copy(alpha = 0.12f) else Color(0xFF94A3B8).copy(alpha = 0.35f)
    val rimStroke = if (isDark) primaryColor.copy(alpha = 0.35f) else Color(0xFFCBD5E1)
    val goldAccent = Color(0xFFF59E0B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Compute midpoint center so both user & Kaaba appear prominently on the front hemisphere
        val centerLatRad = Math.toRadians((userLat + KAABA_LAT) / 2.0)
        val centerLngRad = Math.toRadians((userLng + KAABA_LNG) / 2.0)

        fun project(latDeg: Double, lngDeg: Double, cx: Float, cy: Float, r: Float): Triple<Float, Float, Boolean> {
            val phi = Math.toRadians(latDeg)
            val lam = Math.toRadians(lngDeg)
            val cosC = sin(centerLatRad) * sin(phi) + cos(centerLatRad) * cos(phi) * cos(lam - centerLngRad)
            val x = r * (cos(phi) * sin(lam - centerLngRad)).toFloat()
            val y = -r * (cos(centerLatRad) * sin(phi) - sin(centerLatRad) * cos(phi) * cos(lam - centerLngRad)).toFloat()
            return Triple(cx + x, cy + y, cosC >= -0.05)
        }

        Canvas(modifier = Modifier.size(216.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f - 6.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (isDark) 0.08f else 0.7f), globeBg),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r * 1.25f
                ),
                radius = r,
                center = Offset(cx, cy)
            )

            // Draw meridians
            for (lng in -180 until 180 step 20) {
                val path = Path()
                var started = false
                for (lat in -85..85 step 5) {
                    val (px, py, visible) = project(lat.toDouble(), lng.toDouble(), cx, cy, r)
                    if (visible) {
                        if (!started) {
                            path.moveTo(px, py)
                            started = true
                        } else {
                            path.lineTo(px, py)
                        }
                    } else {
                        started = false
                    }
                }
                drawPath(path = path, color = gridStroke, style = Stroke(width = 1.dp.toPx()))
            }

            // Draw parallels
            for (lat in -60..60 step 20) {
                val path = Path()
                var started = false
                for (lng in -180..180 step 6) {
                    val (px, py, visible) = project(lat.toDouble(), lng.toDouble(), cx, cy, r)
                    if (visible) {
                        if (!started) {
                            path.moveTo(px, py)
                            started = true
                        } else {
                            path.lineTo(px, py)
                        }
                    } else {
                        started = false
                    }
                }
                drawPath(path = path, color = gridStroke, style = Stroke(width = 1.dp.toPx()))
            }

            drawCircle(
                color = rimStroke,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Great-Circle Geodesic Path from User to Kaaba
            val arcPath = Path()
            val steps = 28
            val phi1 = Math.toRadians(userLat)
            val lam1 = Math.toRadians(userLng)
            val phi2 = Math.toRadians(KAABA_LAT)
            val lam2 = Math.toRadians(KAABA_LNG)
            val d = 2 * asin(
                sqrt(
                    sin((phi2 - phi1) / 2).pow(2.0) +
                        cos(phi1) * cos(phi2) * sin((lam2 - lam1) / 2).pow(2.0)
                )
            ).coerceAtLeast(1e-5)

            for (i in 0..steps) {
                val f = i.toDouble() / steps.toDouble()
                val a = sin((1 - f) * d) / sin(d)
                val b = sin(f * d) / sin(d)
                val x3 = a * cos(phi1) * cos(lam1) + b * cos(phi2) * cos(lam2)
                val y3 = a * cos(phi1) * sin(lam1) + b * cos(phi2) * sin(lam2)
                val z3 = a * sin(phi1) + b * sin(phi2)
                val pLat = Math.toDegrees(atan2(z3, sqrt(x3 * x3 + y3 * y3)))
                val pLng = Math.toDegrees(atan2(y3, x3))
                val (px, py, _) = project(pLat, pLng, cx, cy, r)
                if (i == 0) arcPath.moveTo(px, py) else arcPath.lineTo(px, py)
            }
            drawPath(
                path = arcPath,
                color = goldAccent,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            val (ux, uy, _) = project(userLat, userLng, cx, cy, r)
            val (kx, ky, _) = project(KAABA_LAT, KAABA_LNG, cx, cy, r)

            // User Pin Dot
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(ux, uy))
            drawCircle(color = primaryColor, radius = 6.dp.toPx(), center = Offset(ux, uy))

            // Kaaba Pin Dot
            drawCircle(color = goldAccent.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(kx, ky))
            drawCircle(color = goldAccent, radius = 9.dp.toPx(), center = Offset(kx, ky))
            drawCircle(color = Color(0xFF1F2937), radius = 4.dp.toPx(), center = Offset(kx, ky))
        }

        // Crisp Floating Label Pills
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "الكعبة",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(34.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "موقعك",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationOptionRowCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accent,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfflineCityPickerDialog(
    currentCity: String,
    onCitySelected: (CountryCityInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val allCities = remember {
        val extraEgypt = listOf(
            CountryCityInfo("مصر", "Egypt", "المحمودية (البحيرة)", "El Mahmoudiyah", 31.1825, 30.5228, CalculationMethod.EGYPTIAN, 5)
        )
        extraEgypt + PrayerDataConstants.COUNTRIES_CITIES
    }
    val filtered = remember(query) {
        if (query.isBlank()) allCities
        else allCities.filter {
            it.cityArabic.contains(query, ignoreCase = true) ||
                it.countryArabic.contains(query, ignoreCase = true) ||
                it.cityEnglish.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.78f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختار مدينة",
                        fontFamily = DisplayArabicFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن مدينتك (القاهرة، المحمودية، الإسكندرية...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { item ->
                        val isSelected = currentCity.contains(item.cityArabic.substringBefore(" ("))
                        Surface(
                            onClick = { onCitySelected(item) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${item.cityArabic} — ${item.countryArabic}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.3f", item.latitude)}°, ${String.format(Locale.US, "%.3f", item.longitude)}°",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
private fun ManualCoordinatesDialog(
    initialCity: String,
    initialLat: Double,
    initialLng: Double,
    onSave: (String, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var cityName by remember { mutableStateOf(initialCity) }
    var latText by remember { mutableStateOf(String.format(Locale.US, "%.4f", initialLat)) }
    var lngText by remember { mutableStateOf(String.format(Locale.US, "%.4f", initialLng)) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إحداثيات يدوية",
                fontFamily = DisplayArabicFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("اسم المنطقة / المدينة") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("خط العرض (Latitude)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("خط الطول (Longitude)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.trim().toDoubleOrNull()
                    val lng = lngText.trim().toDoubleOrNull()
                    if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                        onSave(cityName, lat, lng)
                    } else {
                        Toast.makeText(context, "يرجى إدخال إحداثيات صحيحة", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("حفظ وتحديث", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
