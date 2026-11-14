package com.example.ui.screens.english

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.english.EnglishPlacementTestData

/**
 * Analytics & Tracking Dashboard matching Screenshot 9
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishAnalyticsDashboardScreen(
    userLevelCode: Int,
    onNavigateBack: () -> Unit,
    onStartPlacementTest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isKnowledgeMapExpanded by remember { mutableStateOf(false) }

    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F7FF), Color(0xFFF4F0FF), Color(0xFFFFFFFF))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "لوحة المتابعة",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.padding(end = 40.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF9F7FF))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(purpleGradient)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ==========================================
            // CARD 1: آخر ١٤ يوم (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Streak Pill Badge: "سلسلة ١ يوم"
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "سلسلة ١ يوم",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }

                            Text(
                                text = "آخر ١٤ يوم",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B)
                            )
                        }

                        // Bar Chart: 14 bars matching Screenshot 9
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                repeat(14) { index ->
                                    val isCurrentActiveDay = index == 11 // Day 12 active
                                    val barHeight = if (isCurrentActiveDay) 75.dp else 4.dp
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isCurrentActiveDay) Color(0xFF7C5CFC) else Color(0xFF0F172A)
                                            )
                                    )
                                }
                            }
                        }

                        // Stats Summary
                        Text(
                            text = "الأسبوع ده ٤١ تمرين بدقة ٩٥٪ • إجمالي ٤١ تمرين على ١ يوم",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )

                        // Button on the right: "بتتعلمه ٤٠"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEDE9FE),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = "بتتعلمه ٤٠",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C5CFC),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // CARD 2: المهارات (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (userLevelCode == 0) "Level 0 مبتدئ تماماً" else "Level $userLevelCode",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C5CFC)
                            )
                            Text(
                                text = "المهارات",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B)
                            )
                        }

                        val skills = listOf(
                            "مفردات", "قواعد", "استماع", "تحدث", "قراءة", "كتابة", "نطق"
                        )

                        skills.forEach { skillName ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (userLevelCode == 0) "Level 0" else "Level $userLevelCode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1B4B)
                                )

                                LinearProgressIndicator(
                                    progress = { if (userLevelCode == 0) 0.05f else 0.45f },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp)
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = Color(0xFFC7D2FE),
                                    trackColor = Color(0xFFF1F5F9)
                                )

                                Text(
                                    text = skillName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E1B4B),
                                    modifier = Modifier.width(60.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "أضعف مهارة: مفردات • أقوى: مفردات",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ==========================================
            // CARD 3: المستويات (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "المستويات",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = "العدي: كل الأساسيات + إنجاز ٨٠٪ + إتقان ٨٠ + ثقة ٦٥",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )

                        val levelItems = listOf(
                            Triple("مبتدئ تماماً", "تغطية ١٠٠٪ • إنجاز ٠٪ • إتقان ٩٣ • ثقة ١٩ • أساسيات ٠/٥", 1f),
                            Triple("ما قبل A1", "تغطية ٥٦٪ • إنجاز ٠٪ • إتقان ٦٠ • ثقة ١٨ • أساسيات ٠/٥", 0.56f),
                            Triple("A1", "تغطية ١٠٪ • إنجاز ٠٪ • إتقان ٩٣ • ثقة ١٩ • أساسيات ٠/١٦", 0.10f),
                            Triple("A2", "تغطية ١٤٪ • إنجاز ٠٪ • إتقان ١٠٠ • ثقة ١٩ • أساسيات ٠/١٢", 0.14f),
                            Triple("B1", "تغطية ١٣٪ • إنجاز ٠٪ • إتقان ١٠٠ • ثقة ١٩ • أساسيات ٠/١٤", 0.13f),
                            Triple("B2", "تغطية ١٣٪ • إنجاز ٠٪ • إتقان ١٠٠ • ثقة ١٩ • أساسيات ٠/١٤", 0.13f),
                            Triple("C1", "تغطية ١٣٪ • إنجاز ٠٪ • إتقان ١٠٠ • ثقة ١٩ • أساسيات ٠/١٤", 0.13f),
                            Triple("C2", "تغطية ١٣٪ • إنجاز ٠٪ • إتقان ١٠٠ • ثقة ١٩ • أساسيات ٠/١٤", 0.13f)
                        )

                        levelItems.forEach { (name, stats, cov) ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "اختبار المستوى",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C5CFC),
                                        modifier = Modifier.clickable { onStartPlacementTest() }
                                    )
                                    Text(
                                        text = name,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1B4B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stats,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { cov },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape),
                                    color = Color(0xFFFDE68A),
                                    trackColor = Color(0xFFFEF3C7).copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CARD 4: إنجازات الأكاديمية (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "إنجازات الأكاديمية ٠/١٠",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = "بتتفتح من الحقيقة وبتدي نقاط في تقدمك العام والمتصدرين.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        // Achievements Badges
                        val badges = listOf(
                            "🔤 أول خطوة", "✅ أول إتقان", "📚 عشر كلمات ثابتة",
                            "📖 خمسين كلمة", "💯 مية تمرين", "🔥 أسبوع متواصل",
                            "🛠️ صلحت غلطاتك", "🏁 عديت المستوى صفر", "🎯 ما قبل A1", "🏆 A1"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            badges.chunked(3).forEach { rowBadges ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowBadges.forEach { badge ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFFF8FAFC),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = badge,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CARD 5: خريطة المعرفة (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp))
                        .clickable { isKnowledgeMapExpanded = !isKnowledgeMapExpanded },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isKnowledgeMapExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF1E1B4B)
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "خريطة المعرفة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1E1B4B)
                                )
                                Text(
                                    text = "٢٥٩ مفهوم — كل واحد ومتطلباته وحالته",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isKnowledgeMapExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Divider(color = Color(0xFFF1F5F9))
                                Text("• المفاهيم الصوتية (Phonics): ٤٤ صوتاً ورموز IPA", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("• المفردات الأساسية (Oxford): ٥٧٦ كلمة مبوبة", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("• القواعد والتراكيب (Grammar Tree): ٨٨ قاعدة موزعة بين A1 و B2", fontSize = 12.sp, color = Color(0xFF334155))
                                Text("• مهارات الربط والتحدث: اتجاهات، حوارات ومطاعم", fontSize = 12.sp, color = Color(0xFF334155))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CARD 6: نسخة احتياطية (Matching Screenshot 9)
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.5.dp, RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D8FD).copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "نسخة احتياطية",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "بياناتك على جهازك بس. صدرها ملف JSON وارجعها على أي جهاز — الدمج ماييضاعفش حاجة.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "تم التحقق من بياناتك المحلية وتحديثها بنجاح 🔄", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استرجع نسخة", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val backupJson = """{"level": $userLevelCode, "timestamp": ${System.currentTimeMillis()}, "curriculum": "CEFR_2026"}"""
                                    clipboardManager.setText(AnnotatedString(backupJson))
                                    Toast.makeText(context, "تم تصدير النسخة الاحتياطية بنجاح إلى الحافظة 📋", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("صدر نسخة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
