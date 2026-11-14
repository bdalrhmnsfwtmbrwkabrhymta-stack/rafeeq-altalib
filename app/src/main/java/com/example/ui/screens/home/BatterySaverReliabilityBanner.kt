package com.example.ui.screens.home

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.domain.localization.AppLanguageManager
import com.example.domain.notification.NotificationReliabilityManager

@Composable
fun BatterySaverReliabilityBanner(
    lang: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnored by remember {
        mutableStateOf(NotificationReliabilityManager.isBatteryOptimizationIgnored(context))
    }
    var isDismissed by remember { mutableStateOf(false) }

    // Re-check whenever returning to app from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnored = NotificationReliabilityManager.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AnimatedVisibility(
        visible = !isIgnored && !isDismissed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val titleText = AppLanguageManager.getText(
            lang = lang,
            ar = "استقرار التنبيهات وتوفير الطاقة ⚡",
            en = "Alarms Reliability & Battery ⚡",
            fr = "Fiabilité & Économie d'Énergie ⚡",
            de = "Zuverlässigkeit & Energiesparmodus ⚡",
            es = "Fiabilidad & Ahorro de Batería ⚡"
        )
        val descText = AppLanguageManager.getText(
            lang = lang,
            ar = "وضع توفير الطاقة بهاتفك قد يوقف الأذان والتنبيهات عند إغلاق التطبيق. يرجى استثناء التطبيق لضمان وصول التنبيهات دائماً دون تأخير.",
            en = "Battery saver mode may suppress prayer alarms when the app is closed. Please grant exemption to guarantee alerts always ring on time.",
            fr = "Le mode économiseur peut bloquer les alarmes quand l'app est fermée. Exemptez l'application pour garantir les alertes à temps.",
            de = "Der Energiesparmodus kann Alarme verzögern. Bitte Ausnahme erlauben für zuverlässige Benachrichtigungen.",
            es = "El ahorro de batería puede retrasar alarmas. Concede la excepción para que las alertas funcionen siempre."
        )
        val fixBtnText = AppLanguageManager.getText(
            lang = lang,
            ar = "استثناء التطبيق من توفير الطاقة ⚡",
            en = "Bypass Battery Restrictions ⚡",
            fr = "Exempter de la batterie ⚡",
            de = "Ausnahme erlauben ⚡",
            es = "Eximir de ahorro de batería ⚡"
        )
        val oemBtnText = AppLanguageManager.getText(
            lang = lang,
            ar = "التشغيل التلقائي بالخلفية 📲",
            en = "Background Auto-Start 📲",
            fr = "Démarrage automatique 📲"
        )

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
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
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    IconButton(
                        onClick = { isDismissed = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = descText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            NotificationReliabilityManager.requestIgnoreBatteryOptimizations(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = fixBtnText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            NotificationReliabilityManager.openOemAutoStartSettings(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = oemBtnText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
