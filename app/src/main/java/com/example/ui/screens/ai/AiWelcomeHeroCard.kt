package com.example.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiWelcomeHeroCard(
    selectedTab: Int,
    isEnglish: Boolean,
    onOpenStudyPlanner: () -> Unit,
    onAnalyzeStudyPlan: () -> Unit,
    onOpenTopicClarifier: () -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Clean Educational Icon Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when (selectedTab) {
                            0 -> Color(0xFF3B82F6)
                            1 -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                    )
            ) {
                Icon(
                    imageVector = when (selectedTab) {
                        0 -> Icons.Default.CalendarMonth
                        1 -> Icons.Default.Lightbulb
                        else -> Icons.Default.TipsAndUpdates
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Title and Subtitle
            Text(
                text = when (selectedTab) {
                    0 -> if (isEnglish) "Study Plan & Scheduling Mentor" else "فهيم لتنظيم وخطة المذاكرة"
                    1 -> if (isEnglish) "Complex Topic Clarifier" else "مبسط ومفكك المفاهيم المعقدة"
                    else -> if (isEnglish) "Faheem Study Assistant" else "فهيم • اسأل واشرح"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = when (selectedTab) {
                    0 -> if (isEnglish)
                        "Create personalized revision roadmaps, balance study blocks with Pomodoro intervals, and optimize your upcoming exam prep with Gemini."
                    else
                        "صمم جدولك وجلسات مذاكرتك، قسّم أوقاتك بطريقة بومودورو، ونظّم مراجعة امتحاناتك المقبلة بخطة مخصصة وواقعية."
                    1 -> if (isEnglish)
                        "Deconstruct challenging concepts in Math, Physics, Chemistry, and Biology using step-by-step logic, Feynman analogies, and self-quizzes."
                    else
                        "حوّل أصعب مسائل وقوانين الرياضيات، الفيزياء، الكيمياء، والأحياء إلى شروحات مبسطة بالخطوات، وتشبيهات فاينمان، واختبارات ذاتية فورية."
                    else -> if (isEnglish)
                        "Snap a photo of your textbook problem, attach voice recordings for summarization, or tap Google Search grounding for verified live answers."
                    else
                        "التقط صورة لصفحة كتاب أو مسألة بالكاميرا، فرّغ ولخص التسجيلات الصوتية، أو اسأل Gemini مع بحث جوجل المباشر."
                },
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            // Primary Interactive Action Buttons
            when (selectedTab) {
                0 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenStudyPlanner,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Build Plan 🗓️" else "صمّم خطة دراسية 🗓️", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onAnalyzeStudyPlan,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Analyze Tasks 📊" else "تحليل مهامي 📊", fontSize = 12.sp)
                        }
                    }
                }
                1 -> {
                    Button(
                        onClick = onOpenTopicClarifier,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEnglish) "Clarify a Difficult Concept 💡✨" else "اشرح لي مفهوماً أو مسألة صعبة الآن 💡✨", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onQuickPrompt(if (isEnglish) "Explain and solve the question in this photo step-by-step:" else "قم بتحليل وحل المسألة الرياضية في هذه الصورة خطوة بخطوة:")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Camera Question" else "حل من صورة 📷", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onQuickPrompt(if (isEnglish) "Summarize the key points of this lesson:" else "لخص النقاط الأساسية المستفادة من هذا الدرس:")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEnglish) "Lesson Summary" else "تلخيص درس 📝", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
