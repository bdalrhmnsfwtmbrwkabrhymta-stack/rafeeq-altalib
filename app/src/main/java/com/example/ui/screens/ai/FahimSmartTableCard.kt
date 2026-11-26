package com.example.ui.screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.FahimStudyTableData
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun FahimSmartTableCard(
    table: FahimStudyTableData,
    isAddedToSchedule: Boolean,
    onAddToSchedule: () -> Unit,
    onOpenTasks: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val headers = table.columnHeaders.ifEmpty { listOf("الوقت", "المهمة", "المدة") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fahim_study_table_card"),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFE2E8F0)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (table.isComparisonOnly) Color(0xFFF3E8FF) else Color(0xFFEFF6FF),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (table.isComparisonOnly) Icons.Default.CompareArrows else Icons.Default.CalendarMonth,
                            contentDescription = table.title,
                            tint = if (table.isComparisonOnly) Color(0xFF7C3AED) else Color(0xFF1A6FE3),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = table.title,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E1B18),
                    lineHeight = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Colored Table Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF8FAFC))
            ) {
                // Header Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (table.isComparisonOnly) Color(0xFF1E1B18) else Color(0xFF1A6FE3)
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headers.getOrElse(0) { "الوقت" },
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(0.95f)
                    )
                    Text(
                        text = headers.getOrElse(1) { "المهمة" },
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1.45f)
                    )
                    Text(
                        text = headers.getOrElse(2) { "المدة" },
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                // Tinted Rows
                table.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(row.tintHex))
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.timeOrDay,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A6FE3),
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .weight(0.95f)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = row.taskOrSubject,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E1B18),
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .weight(1.45f)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = row.durationOrDetail,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.End,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    if (index < table.rows.lastIndex) {
                        HorizontalDivider(thickness = 0.8.dp, color = Color(0xFFE2E8F0))
                    }
                }
            }

            if (!table.isComparisonOnly) {
                Spacer(modifier = Modifier.height(12.dp))

                if (isAddedToSchedule) {
                    Button(
                        onClick = onOpenTasks,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fahim_table_added_badge")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تمت إضافة الجدول تلقائياً لقسم مهامي ✓",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onAddToSchedule,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A6FE3),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fahim_add_table_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "إضافة الجدول لمهامي بضغطة واحدة +",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
