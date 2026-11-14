package com.example.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.notification.NotificationReliabilityManager
import com.example.domain.notification.NotificationReliabilityManager.HealthStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationReliabilityCheckScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var diagnostics by remember { mutableStateOf(NotificationReliabilityManager.getFullDiagnostics(context)) }
    var volumeResult by remember { mutableStateOf(NotificationReliabilityManager.checkAudioVolumes(context)) }

    fun refreshAllChecks() {
        diagnostics = NotificationReliabilityManager.getFullDiagnostics(context)
        volumeResult = NotificationReliabilityManager.checkAudioVolumes(context)
    }

    // Auto refresh when returning from system settings to screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllChecks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalPass = (if (diagnostics.notificationsStatus == HealthStatus.PASS || diagnostics.notificationsStatus == HealthStatus.NOT_REQUIRED) 1 else 0) +
            (if (diagnostics.exactAlarmsStatus == HealthStatus.PASS || diagnostics.exactAlarmsStatus == HealthStatus.NOT_REQUIRED) 1 else 0) +
            (if (diagnostics.batteryOptimizationStatus == HealthStatus.PASS || diagnostics.batteryOptimizationStatus == HealthStatus.NOT_REQUIRED) 1 else 0) +
            (if (volumeResult.healthStatus == HealthStatus.PASS) 1 else 0) +
            (if (diagnostics.backgroundExecutionStatus != HealthStatus.RESTRICTED) 1 else 0)

    val totalItems = 5
    val isFullyReliable = totalPass == totalItems

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تقرير موثوقية الأذان والتنبيهات 📋",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshAllChecks()
                        Toast.makeText(context, "تمت إعادة الفحص بنجاح 🔄", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة التحقق"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Overall Status Diagnostic Header Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isFullyReliable)
                            Color(0xFFE8F5E9)
                        else
                            Color(0xFFFFF3E0)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isFullyReliable) Color(0xFF4CAF50).copy(alpha = 0.5f)
                        else Color(0xFFFF9800).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFullyReliable) Color(0xFF2E7D32)
                                    else Color(0xFFE65100)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFullyReliable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFullyReliable)
                                    "جميع بنود الموثوقية مكتملة 100% ✅"
                                else
                                    "يوجد بند أو أكثر يستدعي الانتباه ⚠️",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isFullyReliable) Color(0xFF1B5E20) else Color(0xFFBF360C)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "تم اجتياز $totalPass من أصل $totalItems بنود أساسية لضمان عمل الأذان والمنبه بانتظام.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "فحص بنود الاستقرار الـ 5 (Checklist)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }

            // Item 1: System Notifications Permission
            item {
                val isOk = diagnostics.notificationsStatus == HealthStatus.PASS || diagnostics.notificationsStatus == HealthStatus.NOT_REQUIRED
                ChecklistItemCard(
                    title = "إذن إشعارات النظام (System Notifications)",
                    description = "يسمح بنشر إشعارات أوقات الصلاة والأذان المباشر على الشاشة.",
                    statusMessage = if (isOk) "مُفعل ومصرح به ✅" else "غير مفعّل! لن تظهر الإشعارات.",
                    isPass = isOk,
                    fixButtonText = "إصلاح الآن ⚙️",
                    onFixClick = {
                        NotificationReliabilityManager.openNotificationSettings(context)
                    }
                )
            }

            // Item 2: Exact Alarm Permission (SCHEDULE_EXACT_ALARM)
            item {
                val isOk = diagnostics.exactAlarmsStatus == HealthStatus.PASS || diagnostics.exactAlarmsStatus == HealthStatus.NOT_REQUIRED
                ChecklistItemCard(
                    title = "إذن المنبهات والإنذارات الدقيقة (Exact Alarm)",
                    description = "يسمح للتطبيق بإطلاق الأذان والتنبيهات في الثواني والدقائق المحددة بالضبط.",
                    statusMessage = if (isOk) "مُصرح به بدقة 100% ✅" else "مقيد! قد يتأخر الأذان عدة دقائق.",
                    isPass = isOk,
                    fixButtonText = "إصلاح الآن ⏱️",
                    onFixClick = {
                        NotificationReliabilityManager.openExactAlarmSettings(context)
                    }
                )
            }

            // Item 3: Battery Optimization Exemption
            item {
                val isOk = diagnostics.batteryOptimizationStatus == HealthStatus.PASS || diagnostics.batteryOptimizationStatus == HealthStatus.NOT_REQUIRED
                ChecklistItemCard(
                    title = "استثناء توفير الطاقة والبطارية (Battery Saver)",
                    description = "يمنع وضع الحفظ العميق من إيقاف عملية الأذان أثناء نوم الهاتف.",
                    statusMessage = if (isOk) "مستثنى من الحفظ العميق ✅" else "مقيد بتحسين البطارية! قد يمنع الأذان.",
                    isPass = isOk,
                    fixButtonText = "إصلاح الآن 🔋",
                    onFixClick = {
                        NotificationReliabilityManager.openBatteryOptimizationSettings(context)
                    }
                )
            }

            // Item 4: STREAM_ALARM Volume Check (NEW CRITICAL ITEM)
            item {
                val isOk = volumeResult.healthStatus == HealthStatus.PASS
                val isWarning = volumeResult.healthStatus == HealthStatus.RESTRICTED
                ChecklistItemCard(
                    title = "فحص مستوى صوت المنبه (STREAM_ALARM)",
                    description = "فحص هل صوت المنبه مكتوم أو منخفض — السبب الأكثر شيوعاً لشكوى «الأذان مابيسمعش» رغم تفعيل الإشعارات.",
                    statusMessage = volumeResult.descriptionMessage,
                    isPass = isOk,
                    isWarning = isWarning,
                    fixButtonText = "ضبط الصوت 🔊",
                    onFixClick = {
                        NotificationReliabilityManager.openSoundSettings(context)
                    }
                )
            }

            // Item 5: OEM Auto-Start & Background Restrictions
            item {
                val isOk = diagnostics.backgroundExecutionStatus != HealthStatus.RESTRICTED
                ChecklistItemCard(
                    title = "إدارة التشغيل التلقائي (OEM Auto-Start)",
                    description = "إعادة التشغيل التلقائي خلف كواليس أجهزة شاومي وهواوي وسامسونج وغيرها.",
                    statusMessage = if (isOk) "غير مقيد بالخلفية ✅" else "مقيد! يجب السماح بالتشغيل التلقائي.",
                    isPass = isOk,
                    fixButtonText = "إصلاح الآن 🚀",
                    onFixClick = {
                        NotificationReliabilityManager.openOemAutoStartSettings(context)
                    }
                )
            }

            // Action Buttons Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val res = NotificationReliabilityManager.oneTapRepairAndReschedule(context)
                            diagnostics = res
                            volumeResult = NotificationReliabilityManager.checkAudioVolumes(context)
                            Toast.makeText(context, "تمت إعادة الجدولة والإصلاح الشامل ⚡", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إصلاح شامل وإعادة جدولة بنقرة واحدة ⚡",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                refreshAllChecks()
                                Toast.makeText(context, "تم تحديث التقرير بنجاح 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إعادة التحقق 🔄", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                NotificationReliabilityManager.showInstantTestNotification(context)
                                Toast.makeText(context, "تم إرسال إشعار اختبار الأذان 🔔", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تجربة إشعار 🔔", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemCard(
    title: String,
    description: String,
    statusMessage: String,
    isPass: Boolean,
    isWarning: Boolean = false,
    fixButtonText: String,
    onFixClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                isPass -> Color(0xFF4CAF50).copy(alpha = 0.35f)
                isWarning -> Color(0xFFFF9800).copy(alpha = 0.5f)
                else -> Color(0xFFE53935).copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Icon(
                        imageVector = when {
                            isPass -> Icons.Default.CheckCircle
                            isWarning -> Icons.Default.Warning
                            else -> Icons.Default.Cancel
                        },
                        contentDescription = null,
                        tint = when {
                            isPass -> Color(0xFF2E7D32)
                            isWarning -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!isPass) {
                    Button(
                        onClick = onFixClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isWarning) Color(0xFFEF6C00) else Color(0xFFD32F2F)
                        )
                    ) {
                        Text(
                            text = fixButtonText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Text(
                text = description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isPass -> Color(0xFFE8F5E9)
                    isWarning -> Color(0xFFFFF3E0)
                    else -> Color(0xFFFFEBEE)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isPass -> Color(0xFF1B5E20)
                        isWarning -> Color(0xFFE65100)
                        else -> Color(0xFFB71C1C)
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
