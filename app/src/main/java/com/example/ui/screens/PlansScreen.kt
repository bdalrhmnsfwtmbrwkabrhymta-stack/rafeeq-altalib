package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

data class PlanFeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val badge: String,
    val pastelColor: Color
)

@Composable
fun PlansScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val features = listOf(
        PlanFeatureItem(
            title = "الجدول الدراسي الأسبوعي 📅",
            description = "تنظيم مواعيد المحاضرات والدروس مع تنبيهات تلقائية قبل كل حصة",
            icon = Icons.Default.CalendarMonth,
            route = "schedule",
            badge = "جدولك",
            pastelColor = PastelMint
        ),
        PlanFeatureItem(
            title = "مؤقت بومودورو المتقدم 🍅",
            description = "تقنية 25 دقيقة تركيز مع فترات راحة ذكية لزيادة الإنتاجية",
            icon = Icons.Default.Timer,
            route = "pomodoro",
            badge = "تقنية التركيز",
            pastelColor = PastelYellow
        ),
        PlanFeatureItem(
            title = "بناء ومتابعة العادات 🎯",
            description = "تثبيت عادات المذاكرة الإيجابية مع منبهات تعمل حتى والتطبيق مغلق",
            icon = Icons.Default.Loop,
            route = "habits",
            badge = "استمرارية",
            pastelColor = PastelCyan
        ),
        PlanFeatureItem(
            title = "قائمة المهام اليومية 📝",
            description = "تحديد أولويات اليوم وشطب المهام المنجزة خطوة بخطوة",
            icon = Icons.Default.Checklist,
            route = "tasks",
            badge = "إنجاز يومي",
            pastelColor = PastelBlue
        ),
        PlanFeatureItem(
            title = "خطة المذاكرة مع فهيم 📅",
            description = "توليد جدول مخصص تلقائياً حسب عدد المواد وأيام الامتحانات",
            icon = Icons.Default.TipsAndUpdates,
            route = "study_schedule_ai",
            badge = "منظم",
            pastelColor = PastelPurple
        )
    )

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
                    title = "خطتي وجداولي 📋",
                    subtitle = "نظم وقتك، التزم بعاداتك، وانجز مهامك الدراسية بأعلى إنتاجية",
                    gradientColors = listOf(PlansTealDark, PlansTealPrimary),
                    onMenuClick = onOpenDrawer
                )
            }

            // Motivational quote banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PlansTealContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PlansTealLight.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Premium3DIcon(
                            icon = Icons.Default.Lightbulb,
                            primaryColor = PlansTealPrimary,
                            containerSize = 44.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "«قليل دائم خير من كثير منقطع» ✨",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PlansTealDark
                            )
                            Text(
                                text = "التزامك بجدول يومي ثابت وعادات منتظمة هو السر الحقيقي للتفوق.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Features list
            items(features.size) { idx ->
                val feat = features[idx]
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clickable { onNavigate(feat.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Premium3DIcon(
                            icon = feat.icon,
                            primaryColor = PlansTealPrimary,
                            containerSize = 46.dp,
                            iconSize = 22.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = feat.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = feat.pastelColor
                                ) {
                                    Text(
                                        text = feat.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PlansTealPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = feat.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 17.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = PlansTealLight,
                            modifier = Modifier.size(18.dp).padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
