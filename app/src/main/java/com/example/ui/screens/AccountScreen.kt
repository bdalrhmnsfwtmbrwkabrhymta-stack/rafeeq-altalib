package com.example.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.CurvedHeader
import com.example.ui.components.Premium3DIcon
import com.example.ui.theme.*

@Composable
fun AccountScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val userSettings by viewModel.userSettings.collectAsState()

    Scaffold(
        containerColor = BackgroundLight
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                CurvedHeader(
                    title = "حسابي وإعداداتي 👤",
                    subtitle = "إدارة البيانات الدراسية، الأمان، والتخصيص",
                    gradientColors = listOf(Color(0xFF3B0764), NavPurple),
                    onMenuClick = onOpenDrawer,
                    extraContent = {
                        // User Profile Summary Card inside header
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🎓", fontSize = 26.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = userSettings.userName.ifBlank { "طالب مجتهد" },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${userSettings.studentGradeYear} • ${userSettings.cityLocation}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Stats Cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccountStatCard("النقاط", "⭐ ${userSettings.studentPoints}", PastelYellow, ToolsOrangePrimary, Modifier.weight(1f))
                    AccountStatCard("المستوى", "طالب متميز 🌟", PastelMint, EmeraldPrimary, Modifier.weight(1f))
                    AccountStatCard("الاستمرار", "🔥 نشط", PastelPurple, NavPurple, Modifier.weight(1f))
                }
            }

            // Settings sections
            item {
                AccountSectionTitle("الإعدادات الشخصية والدراسية")
            }

            item {
                AccountOptionCard(
                    title = "تعديل بيانات الطالب والمرحلة",
                    subtitle = "الاسم، العمر، السنة الدراسية، ورقم التليجرام",
                    icon = Icons.Default.Person,
                    color = EmeraldPrimary,
                    onClick = { onNavigate("onboarding") }
                )
            }

            item {
                AccountOptionCard(
                    title = "مواقيت الصلاة وتحديد الموقع 🧭",
                    subtitle = "المدينة: ${userSettings.cityLocation} - طريقة الحساب",
                    icon = Icons.Default.LocationOn,
                    color = Color(0xFF0284C7),
                    onClick = { onNavigate("prayer") }
                )
            }

            item {
                AccountOptionCard(
                    title = "ربط بوت التليجرام والتقارير 🤖",
                    subtitle = if (userSettings.isTelegramLinked) "تم ربط الحساب بنجاح ✓" else "ربط البوت لتلقي التقارير اليومية",
                    icon = Icons.Default.Send,
                    color = Color(0xFF0088CC),
                    onClick = {
                        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        com.example.domain.telegram.DailyTelegramReportHelper.openTelegramBotForAccountLinking(
                            context = viewModel.getApplication(),
                            phone = userSettings.telegramNumber,
                            appUserId = uid
                        )
                    }
                )
            }

            item {
                AccountSectionTitle("المظهر والأمان")
            }

            item {
                AccountOptionCard(
                    title = "ألوان وثيمات التطبيق 🎨",
                    subtitle = "تخصيص ألوان الواجهة والوضع الداكن / الفاتح",
                    icon = Icons.Default.Palette,
                    color = NavPurple,
                    onClick = { onNavigate("app_theme") }
                )
            }

            item {
                AccountOptionCard(
                    title = "قفل التطبيق برمز PIN أو البصمة 🔒",
                    subtitle = "حماية ملاحظاتك وجداولك ودرجاتك برمز سري",
                    icon = Icons.Default.Lock,
                    color = ToolsOrangePrimary,
                    onClick = { onNavigate("settings") }
                )
            }

            item {
                AccountOptionCard(
                    title = "فحص موثوقية التنبيهات والإشعارات 🔔",
                    subtitle = "التأكد من دقة مواعيد المنبه حتى بعد إغلاق التطبيق",
                    icon = Icons.Default.NotificationsActive,
                    color = EmeraldPrimary,
                    onClick = { onNavigate("notification_reliability_check") }
                )
            }

            item {
                AccountSectionTitle("عن التطبيق")
            }

            item {
                AccountOptionCard(
                    title = "عن مُتعلِّم والمطور ℹ️",
                    subtitle = "تطوير: عبد الرحمن صفوت عطا • الإصدار 3.0",
                    icon = Icons.Default.Info,
                    color = TextSecondary,
                    onClick = { onNavigate("settings") }
                )
            }
        }
    }
}

@Composable
private fun AccountSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun AccountOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Premium3DIcon(
                icon = icon,
                primaryColor = color,
                containerSize = 42.dp,
                iconSize = 20.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 11.5.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AccountStatCard(
    title: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 11.sp, color = textColor.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}
