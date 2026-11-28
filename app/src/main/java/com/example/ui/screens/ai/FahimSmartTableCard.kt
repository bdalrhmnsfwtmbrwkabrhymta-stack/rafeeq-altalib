package com.example.ui.screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.VerticalDivider
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

private val TableHeaderBlue = Color(0xFF1A6FE3)
private val TableBorderGray = Color(0xFFCBD5E1)
private val TableAltRowBg = Color(0xFFF8FAFC)
private val TableInk = Color(0xFF1E1B18)
private val TableEmerald = Color(0xFF10B981)

/**
 * بطاقة الجداول الموحدة والمنظمة في «فهيم»:
 * - حدود ناعمة 1.dp (#CBD5E1)
 * - رأس أزرق ملكي (#1A6FE3) بنص أبيض 15.sp Bold
 * - صفوف متبادلة (#FFFFFF / #F8FAFC) بنص 14.5.sp وحشوة 12.dp بدون اقتطاع
 */
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
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, TableBorderGray),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Card Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (table.isComparisonOnly) Icons.Default.CompareArrows else Icons.Default.CalendarMonth,
                            contentDescription = table.title,
                            tint = TableHeaderBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = table.title,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TableInk,
                    lineHeight = 23.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Bordered Table Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, TableBorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Royal Blue Header Row (#1A6FE3, White 15.sp Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(TableHeaderBlue),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.95f)
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = headers.getOrElse(0) { "الوقت" },
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.25f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1.45f)
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = headers.getOrElse(1) { "المهمة" },
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.25f)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.85f)
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = headers.getOrElse(2) { "المدة" },
                                    fontFamily = ReadexProFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Alternating Clean Rows (#FFFFFF / #F8FAFC, 14.5.sp, 12.dp padding)
                        table.rows.forEachIndexed { index, row ->
                            val rowBg = if (index % 2 == 0) Color.White else TableAltRowBg
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(rowBg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.95f)
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = row.timeOrDay,
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TableHeaderBlue,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                }
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    thickness = 1.dp,
                                    color = TableBorderGray.copy(alpha = 0.7f)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1.45f)
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = row.taskOrSubject,
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TableInk,
                                        lineHeight = 21.sp
                                    )
                                }
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight(),
                                    thickness = 1.dp,
                                    color = TableBorderGray.copy(alpha = 0.7f)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.85f)
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = row.durationOrDetail,
                                        fontFamily = ReadexProFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF475569),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                            if (index < table.rows.lastIndex) {
                                HorizontalDivider(thickness = 1.dp, color = TableBorderGray.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }

            if (!table.isComparisonOnly) {
                Spacer(modifier = Modifier.height(12.dp))

                if (isAddedToSchedule) {
                    Button(
                        onClick = onOpenTasks,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TableEmerald,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
                            text = "تمت إضافة الجدول لقسم مهامي ✓",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onAddToSchedule,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TableHeaderBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
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
                            text = "إضافة الجدول لمهامي بضغطة واحدة",
                            fontFamily = ReadexProFontFamily,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
