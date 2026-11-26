package com.example.ui.screens.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.FahimMindMapBranch
import com.example.domain.ai.FahimMindMapData
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun FahimMindMapCanvas(
    mindMap: FahimMindMapData,
    modifier: Modifier = Modifier
) {
    val branches = mindMap.branches
    val topRow = branches.take(2)
    val bottomRow = branches.drop(2).take(2)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fahim_mindmap_card"),
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
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "خريطة ذهنية",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "خريطة ذهنية ملخصة",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E1B18)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Central Node + Smooth Colored Bezier Connectors + Branch Cards
            Box(modifier = Modifier.fillMaxWidth()) {
                // Background Bezier Connector Lines
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    val w = size.width
                    val h = size.height
                    val centerBottom = Offset(w * 0.5f, 38.dp.toPx())
                    val branchTargets = listOf(
                        Offset(w * 0.25f, 76.dp.toPx()),
                        Offset(w * 0.75f, 76.dp.toPx()),
                        Offset(w * 0.25f, h * 0.62f),
                        Offset(w * 0.75f, h * 0.62f)
                    )

                    branches.take(4).forEachIndexed { idx, branch ->
                        val target = branchTargets[idx]
                        val lineColor = Color(branch.colorHex)
                        val bezier = Path().apply {
                            moveTo(centerBottom.x, centerBottom.y)
                            val midY = (centerBottom.y + target.y) * 0.55f
                            cubicTo(
                                centerBottom.x, midY,
                                target.x, midY,
                                target.x, target.y
                            )
                        }
                        drawPath(
                            path = bezier,
                            color = lineColor.copy(alpha = 0.75f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(
                            color = lineColor,
                            radius = 4.5.dp.toPx(),
                            center = target
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Central Hero Node
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131B2E),
                        border = BorderStroke(2.dp, Color(0xFF1A6FE3)),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = mindMap.centerTitle,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(26.dp))

                    if (topRow.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            topRow.forEach { branch ->
                                MindMapBranchCard(
                                    branch = branch,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (bottomRow.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            bottomRow.forEach { branch ->
                                MindMapBranchCard(
                                    branch = branch,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (bottomRow.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MindMapBranchCard(
    branch: FahimMindMapBranch,
    modifier: Modifier = Modifier
) {
    val accent = Color(branch.colorHex)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.2.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = branch.title,
                    fontFamily = ReadexProFontFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            branch.items.forEach { bullet ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "•",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(
                        text = bullet,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E1B18),
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
