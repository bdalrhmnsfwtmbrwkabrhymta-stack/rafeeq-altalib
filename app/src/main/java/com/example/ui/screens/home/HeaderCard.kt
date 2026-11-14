package com.example.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserSettings
import com.example.ui.MainViewModel

/**
 * Compact, Sleek & Modern Header Card
 * Dynamically displays the registered/logged-in student name without hardcoding,
 * with a streamlined, space-saving layout for the quote ticker.
 */
@Composable
fun HeaderCard(
    studentName: String,
    isEnglish: Boolean,
    userSettings: UserSettings? = null,
    viewModel: MainViewModel? = null,
    onNavigateToGradeSelection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val todayQuotes = remember {
        listOf(
            "حصاد الجهود يكتمل بالتخطيط المستمر والعمل.",
            "النجاح ليس صدفة، بل هو ثمرة التعب والمثابرة والتعلم المستمر.",
            "ابدأ بما هو ضروري، ثم ما هو ممكن، وفجأة ستجد نفسك تصنع المستحيل.",
            "كل دقيقة تقضيها في المذاكرة تقربك خطوة نحو حلمك الكبير.",
            "لا تستسلم أبداً، فالأشياء العظيمة تحتاج وقتاً لتتحقق.",
            "التركيز والهمة هما سر التفوق في كل مرحلة دراسية.",
            "تنظيم الوقت نصف النجاح، والاجتهاد نصفه الآخر.",
            "ثقتك بنفسك وتوكلك على الله هما مفتاح كل باب مغلق."
        )
    }
    var currentQuoteIndex by remember { mutableIntStateOf(0) }

    val gradeYear = userSettings?.studentGradeYear?.ifBlank { "الصف الثالث الثانوي" } ?: "الصف الثالث الثانوي"
    val studentAge = userSettings?.studentAge ?: 17
    val branch = when (userSettings?.highSchoolBranch) {
        "SCIENCE", "علمي علوم" -> "علمي"
        "MATH", "علمي رياضة" -> "علمي رياضة"
        "LITERATURE", "أدبي" -> "أدبي"
        else -> "علمي"
    }

    // Dynamic user name from actual user account
    val displayName = studentName.trim().ifBlank { "الطالب المتفوق" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFF1E5DE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Section: Compact Greetings + Dev Badge + Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Pill: Compact Dev Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFDECE8),
                    border = BorderStroke(1.dp, Color(0xFFFCD3C1))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dev: Abd El-Rahman",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFFE2574C),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Right: Compact Greeting Name & Subtitle + Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "👋",
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "مرحباً بك، $displayName",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1B4B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = "رحلتك نحو التفوق والتميز الدراسي 🚀",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Coral Red Avatar Circle (Compact)
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE2574C),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "الصورة الشخصية",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 2. Middle Clickable Subcard: "الصف الثالث الثانوي • 17 سنة • علمي"
            Surface(
                onClick = onNavigateToGradeSelection,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: "تعديل >"
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = null,
                            tint = Color(0xFFE2574C),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "تعديل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2574C)
                        )
                    }

                    // Right: Grade Year Tag
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$gradeYear • $studentAge سنة • $branch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "🎓",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 3. Bottom Slim Quote Ticker (Compact & space-saving)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFAF7F5),
                border = BorderStroke(1.dp, Color(0xFFF5E6DD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Navigation buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                currentQuoteIndex = if (currentQuoteIndex > 0) currentQuoteIndex - 1 else todayQuotes.size - 1
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "السابق",
                                tint = Color(0xFFE2574C),
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        Text(
                            text = "${currentQuoteIndex + 1}/${todayQuotes.size}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )

                        IconButton(
                            onClick = {
                                currentQuoteIndex = (currentQuoteIndex + 1) % todayQuotes.size
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = "التالي",
                                tint = Color(0xFFE2574C),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quote Text inside single row / compact layout
                    AnimatedContent(
                        targetState = currentQuoteIndex,
                        label = "CompactQuoteAnim",
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        val quote = todayQuotes[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "”$quote“",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E1B4B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "✨", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
