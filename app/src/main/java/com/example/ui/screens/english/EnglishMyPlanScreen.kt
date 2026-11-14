package com.example.ui.screens.english

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Screen matching Screenshot 2: "خطتي وجلسة النهارده"
 */
@Composable
fun EnglishMyPlanScreen(
    onNavigateBack: () -> Unit,
    onOpenReviewDue: () -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(10) }
    var selectedTargetLevelIndex by remember { mutableIntStateOf(0) } // 0: ما قبل A1, 1: A1, 2: A2
    var isSessionCompleted by remember { mutableStateOf(false) }

    val minuteOptions = listOf(10, 15, 20, 30, 45)
    val targetLevelOptions = listOf("ما قبل A1", "A1", "A2")

    // Dynamic study estimation calculation matching Screenshot 2
    val targetName = targetLevelOptions[selectedTargetLevelIndex]
    val conceptsRemaining = when (selectedTargetLevelIndex) {
        0 -> 14
        1 -> 28
        else -> 46
    }
    val minutesTotal = conceptsRemaining * 11
    val daysCalculated = (minutesTotal / selectedMinutes).coerceAtLeast(1)
    val weeksCalculated = (daysCalculated / 7).coerceAtLeast(1)

    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F7FF), Color(0xFFF3EEFF), Color(0xFFFFFFFF))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(purpleGradient)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Navigation Bar matching Screenshot 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp)) // balance
            Text(
                text = "خطتي وجلسة النهارده",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1E1B4B)
            )
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = Color(0xFF1E1B4B)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: "جلسة النهارده" matching Screenshot 2
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, Color(0xFFDDD6FE).copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header row with "جلسة النهارده" and purple pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEDE9FE)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF7C5CFC),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$selectedMinutes دقيقة",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C5CFC)
                                    )
                                }
                            }

                            Text(
                                text = "جلسة النهارده",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1B4B)
                            )
                        }

                        // Inner interactive card for "راجع اللي استحق"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenReviewDue() },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB).copy(alpha = 0.85f)),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$selectedMinutes د",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "مراجعة مستحقة",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                        Text(
                                            text = "راجع اللي استحق",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1E1B4B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Green Status Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clickable { isSessionCompleted = !isSessionCompleted }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSessionCompleted) "تم إنهاء جلسة اليوم بنجاح!" else "خلصت جلسة النهارده ✓",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: "خطتي" matching Screenshot 2
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, Color(0xFFFED7AA).copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "خطتي",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )

                        // 1. Minutes Selector
                        Text(
                            text = "دقايق في اليوم",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            minuteOptions.forEach { minutes ->
                                val isSelected = selectedMinutes == minutes
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clickable { selectedMinutes = minutes },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF00BFA5) else Color.White,
                                    border = BorderStroke(
                                        width = if (isSelected) 0.dp else 1.2.dp,
                                        color = if (isSelected) Color.Transparent else Color(0xFF1E293B)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "$minutes",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color(0xFF1E1B4B)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Target Level Selector
                        Text(
                            text = "المستوى الهدف",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            targetLevelOptions.forEachIndexed { index, levelName ->
                                val isSelected = selectedTargetLevelIndex == index
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clickable { selectedTargetLevelIndex = index },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF00BFA5) else Color.White,
                                    border = BorderStroke(
                                        width = if (isSelected) 0.dp else 1.2.dp,
                                        color = if (isSelected) Color.Transparent else Color(0xFF1E293B)
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = levelName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color(0xFF1E1B4B)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Dynamic Calculation Text matching Screenshot 2
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "باقي $conceptsRemaining مفهوم لحد $targetName ≈ $minutesTotal دقيقة = حوالي $daysCalculated يوم ($weeksCalculated أسبوع) بمعدل $selectedMinutes دقيقة في اليوم.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E1B4B),
                                lineHeight = 20.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "تقدير من إتقانك الحقيقي، بيتحدث كل يوم — مش وعد.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
