package com.example.ui.dialogs

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.notification.NotificationReliabilityManager
import com.example.domain.prayer.PrayerAlarmScheduler

/**
 * Universal Android Notification & Alarm Reliability Dialog
 * 
 * Provides the 5 essential Android permissions to guarantee that Alarms, Adhan,
 * Study Timers, and WhatsApp/Azkar reminders never stop or get killed in the background:
 * 
 * 1. Schedule Exact Alarms (إذن جدولة التنبيهات بدقة)
 * 2. Display Over Other Apps / System Alert Window (إذن نافذة تنبيه النظام لعرض نافذة الإنذار)
 * 3. Post Notifications (اسمح بتلقي الإشعارات لتلقي تحديثات التنبيهات المهمة)
 * 4. Ignore Battery Optimization (تجاهل تحسين البطارية حتى يتمكن المنبه من العمل في الخلفية)
 * 5. Do Not Disturb Access (اسمح بتشغيل جهاز الإنذار في وضع عدم الإزعاج)
 */
@Composable
fun UniversalNotificationPermissionDialog(
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Helper functions for real-time permission status checking
    fun checkExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun checkOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun checkPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.areNotificationsEnabled() ?: true
        }
    }

    fun checkBatteryOptimization(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    fun checkDndAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.isNotificationPolicyAccessGranted ?: true
        } else {
            true
        }
    }

    var isExactAlarmsGranted by remember { mutableStateOf(checkExactAlarms()) }
    var isFullScreenIntentGranted by remember {
        mutableStateOf(NotificationReliabilityManager.canUseFullScreenIntent(context))
    }
    var isOverlayGranted by remember { mutableStateOf(checkOverlay()) }
    var isPostNotificationsGranted by remember { mutableStateOf(checkPostNotifications()) }
    var isBatteryIgnored by remember { mutableStateOf(checkBatteryOptimization()) }
    var isDndGranted by remember { mutableStateOf(checkDndAccess()) }
    var showRestrictedOverlayGuide by remember { mutableStateOf(false) }

    fun refreshAllStates() {
        isExactAlarmsGranted = checkExactAlarms()
        isFullScreenIntentGranted = NotificationReliabilityManager.canUseFullScreenIntent(context)
        isOverlayGranted = checkOverlay()
        isPostNotificationsGranted = checkPostNotifications()
        isBatteryIgnored = checkBatteryOptimization()
        isDndGranted = checkDndAccess()
    }

    // Auto refresh whenever returning from Android system settings screens
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isPostNotificationsGranted = granted
        if (granted) {
            PrayerAlarmScheduler.rescheduleAll(context)
        }
    }

    val isAllFullyConfigured = isExactAlarmsGranted &&
            isFullScreenIntentGranted &&
            isPostNotificationsGranted &&
            isBatteryIgnored &&
            isDndGranted

    fun grantNextMissingPermission() {
        when {
            !isExactAlarmsGranted -> {
                NotificationReliabilityManager.openExactAlarmSettings(context)
            }
            !isFullScreenIntentGranted -> {
                NotificationReliabilityManager.openFullScreenIntentSettings(context)
            }
            !isPostNotificationsGranted -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    NotificationReliabilityManager.openNotificationSettings(context)
                }
            }
            !isBatteryIgnored -> {
                NotificationReliabilityManager.openBatteryOptimizationSettings(context)
            }
            !isDndGranted -> {
                NotificationReliabilityManager.openNotificationPolicySettings(context)
            }
            else -> {
                PrayerAlarmScheduler.rescheduleAll(context)
                onPermissionGranted()
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Title & Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.AlarmOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "نظام إنذار ذكي وتثبيت الإشعارات",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "لكي يعمل جهاز الإنذار والأذان بشكل صحيح ودون أن يتوقف في الخلفية، يجب عليك منح الأذونات اللازمة.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable List of 5 Essential Permissions
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Item 1: Schedule Exact Alarm
                    item {
                        PermissionItemCard(
                            title = "إذن جدولة التنبيهات بدقة",
                            subtitle = "لتشغيل مواعيد الأذان والمذاكرة في وقتها المحدد بالثانية",
                            icon = Icons.Default.Alarm,
                            isGranted = isExactAlarmsGranted,
                            onClick = {
                                NotificationReliabilityManager.openExactAlarmSettings(context)
                            }
                        )
                    }

                    // Item 2: Full-Screen Intent (Android 14+ lockscreen Adhan without Restricted Settings)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        item {
                            PermissionItemCard(
                                title = "عرض شاشة الأذان على شاشة القفل (Full-Screen)",
                                subtitle = "يسمح بفتح شاشة الأذان الكاملة تلقائياً دون الحاجة لإذن الظهور المقيد",
                                icon = Icons.Default.Fullscreen,
                                isGranted = isFullScreenIntentGranted,
                                onClick = {
                                    NotificationReliabilityManager.openFullScreenIntentSettings(context)
                                }
                            )
                        }
                    }

                    // Item 3: System Alert Window / Overlay (Optional — uses Android 13+ Restricted Guide)
                    item {
                        PermissionItemCard(
                            title = "الظهور فوق التطبيقات الأخرى (اختياري لقفل التركيز)",
                            subtitle = if (NotificationReliabilityManager.isOverlayRestrictedOnAndroid13Plus(context))
                                "يتطلب خطوتين لفك «إعداد مقيد» على أندرويد 13+ (اضغط للشرح)"
                            else
                                "مفيد لقفل شاشة المذاكرة الصارم فوق التطبيقات",
                            icon = Icons.Default.Layers,
                            isGranted = isOverlayGranted,
                            onClick = {
                                if (NotificationReliabilityManager.isOverlayRestrictedOnAndroid13Plus(context)) {
                                    showRestrictedOverlayGuide = true
                                } else {
                                    NotificationReliabilityManager.openOverlaySettings(context)
                                }
                            }
                        )
                    }

                    // Item 3: Post Notifications
                    item {
                        PermissionItemCard(
                            title = "اسمح بتلقي الإشعارات لتلقي تحديثات التنبيهات المهمة",
                            subtitle = "إظهار الإشعارات والتقارير في شريط التنبيهات العلوي",
                            icon = Icons.Default.NotificationsActive,
                            isGranted = isPostNotificationsGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    NotificationReliabilityManager.openNotificationSettings(context)
                                }
                            }
                        )
                    }

                    // Item 4: Ignore Battery Optimization
                    item {
                        PermissionItemCard(
                            title = "تجاهل تحسين البطارية حتى يتمكن المنبه من العمل في الخلفية عند إيقاف تشغيل الشاشة",
                            subtitle = "حماية التطبيق من الإغلاق التلقائي لتوفير الطاقة (شاومي / سامسونج)",
                            icon = Icons.Default.BatteryChargingFull,
                            isGranted = isBatteryIgnored,
                            onClick = {
                                NotificationReliabilityManager.openBatteryOptimizationSettings(context)
                            }
                        )
                    }

                    // Item 5: Do Not Disturb Policy Access
                    item {
                        PermissionItemCard(
                            title = "اسمح بتشغيل جهاز الإنذار في وضع عدم الإزعاج",
                            subtitle = "تجاوز وضع الصامت وعدم الإزعاج لصلاتي الفجر والصلوات المكتوبة",
                            icon = Icons.Default.DoNotDisturbOff,
                            isGranted = isDndGranted,
                            onClick = {
                                NotificationReliabilityManager.openNotificationPolicySettings(context)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons: "منح الأذونات" & "إغلاق / تم"
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isAllFullyConfigured) {
                                PrayerAlarmScheduler.rescheduleAll(context)
                                onPermissionGranted()
                                onDismiss()
                            } else {
                                grantNextMissingPermission()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAllFullyConfigured) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isAllFullyConfigured) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جميع الأذونات مكتملة ومحمية ✅", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("منح الأذونات وحماية النظام ⚡", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAllFullyConfigured) "إغلاق" else "تخطي الآن والمتابعة",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }

    if (showRestrictedOverlayGuide) {
        RestrictedOverlayGuideDialog(
            context = context,
            onDismiss = {
                showRestrictedOverlayGuide = false
                refreshAllStates()
            }
        )
    }
}

@Composable
fun RestrictedOverlayGuideDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "فك قيد «إظهار فوق التطبيقات الأخرى»",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "يُقيد أندرويد 13+ هذا الإذن مؤقتاً للتطبيقات المثبتة خارج المتجر (APK). ملحوظة: شاشة الأذان تعمل بدونه عبر إذن الشاشة الكاملة.",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Text(
                    text = "لتفعيله لقفل التركيز:\n" +
                        "١. اضغط «فتح معلومات التطبيق» بالأسفل ← اضغط الثلاث نقاط (⋮) بأعلى اليسار/اليمين ← اختر «السماح بالإعدادات المقيدة».\n" +
                        "٢. ارجع واضغط «فتح إذن الظهور مباشرة» وفعّل المفتاح.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { NotificationReliabilityManager.openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("١. فتح معلومات التطبيق (لفك القيد ⋮)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { NotificationReliabilityManager.openOverlaySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("٢. فتح إذن الظهور مباشرة", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("إغلاق")
                }
            }
        }
    )
}

@Composable
private fun PermissionItemCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.2.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon & Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Status Badge (Green Checkmark vs Red Cross like the user's screenshot)
            Surface(
                shape = CircleShape,
                color = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = if (isGranted) "مفعل" else "غير مفعل",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
