package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

data class ToolFeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val badge: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val tools = listOf(
        ToolFeatureItem(
            title = "المصحف الشريف والتلاوات 📖",
            description = "المصحف الإلكتروني الشامل مع التلاوات الصوتية وتفسير الآيات",
            icon = Icons.Default.MenuBook,
            route = "quran",
            badge = "قرآن كريم",
            color = Color(0xFF2E7D32)
        ),
        ToolFeatureItem(
            title = "القاموس واللغات 🌐",
            description = "ترجمة الكلمات والمصطلحات والبحث الفوري في قواميس اللغات",
            icon = Icons.Default.Translate,
            route = "dictionary",
            badge = "لغات ومعاجم",
            color = Color(0xFF673AB7)
        ),
        ToolFeatureItem(
            title = "بومودورو التركيز ⏱️",
            description = "جلسات تركيز دراسية بنظام 25 دقيقة عمل و5 دقائق راحة",
            icon = Icons.Default.Timer,
            route = "pomodoro",
            badge = "إدارة وقت",
            color = Color(0xFFE53935)
        ),
        ToolFeatureItem(
            title = "الآلة الحاسبة العلمية (fx-991) 🧮",
            description = "عمليات حسابية معقدة، دوال مثلثية، وتفاضل وتكامل ومصفوفات",
            icon = Icons.Default.Calculate,
            route = "calculator",
            badge = "علمية متقدمة",
            color = Color(0xFFFB8C00)
        ),
        ToolFeatureItem(
            title = "المهام والملاحظات الدراسية 📝",
            description = "تنظيم ومتابعة الواجبات والمذاكرة اليومية وتحديد الأولويات",
            icon = Icons.Default.CheckCircleOutline,
            route = "tasks",
            badge = "إنجاز مهام",
            color = Color(0xFF00897B)
        ),
        ToolFeatureItem(
            title = "المخزن السحابي وقناة المستندات ☁️",
            description = "حفظ ومزامنة المذكرات والملخصات وكتب الـ PDF الدراسية",
            icon = Icons.Default.CloudQueue,
            route = "documents",
            badge = "سحابي رقمي",
            color = Color(0xFF0088CC)
        ),
        ToolFeatureItem(
            title = "الرسالة • مركز الوسائط والمحاضرات الذكي ⬇️",
            description = "تحميل المحاضرات والفيديوهات وقوائم التشغيل بدون إعلانات من يوتيوب وبينتيريست وتيك توك",
            icon = Icons.Default.CloudDownload,
            route = "rafid",
            badge = "الرسالة",
            color = Color(0xFF1565C0)
        )
    )

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الأدوات الدراسية والإسلامية 🛠️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF1E293B)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tools.size) { index ->
                val tool = tools[index]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(tool.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(tool.color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.title,
                                tint = tool.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = tool.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = tool.color.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = tool.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tool.color,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = tool.description,
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
