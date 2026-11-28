package com.example.ui.screens.ai

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.ExcalidrawElementData
import com.example.domain.ai.ExcalidrawElementType
import com.example.domain.ai.FahimWhiteboardLesson
import com.example.domain.ai.StructuredTableData
import com.example.domain.ai.WhiteboardGeometryScene
import com.example.domain.ai.WhiteboardStep
import com.example.ui.theme.ReadexProFontFamily
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

val ExcalidrawCharcoal = Color(0xFF1E1B18)
val ExcalidrawRoyalBlue = Color(0xFF1A6FE3)
val ExcalidrawAmberGold = Color(0xFFD97706)
val ExcalidrawEmerald = Color(0xFF10B981)
val ExcalidrawGridLine = Color(0xFFE2E8F0)
val ExcalidrawTableBorder = Color(0xFFCBD5E1)
val ExcalidrawRowAlt = Color(0xFFF8FAFC)

/**
 * أدوات الرسم التفاعلي اليدوي بأسلوب Excalidraw مباشرة فوق السبورة.
 */
enum class ExcalidrawDrawTool(val labelAr: String, val icon: ImageVector) {
    PAN_SCROLL("تصفح", Icons.Default.PanTool),
    FREEHAND_PEN("قلم", Icons.Default.Create),
    ARROW("سهم", Icons.Default.ArrowOutward),
    RECT("مربع", Icons.Default.CropSquare),
    CIRCLE("دائرة", Icons.Default.RadioButtonUnchecked),
    ERASER("ممحاة", Icons.Default.AutoFixNormal)
}

/**
 * عنصر حبر يدوي يرسمه الطالب بإصبعه فوق سبورة Excalidraw.
 */
data class StudentInkElement(
    val tool: ExcalidrawDrawTool,
    val colorHex: Long,
    val points: List<Offset>,
    val strokeWidthDp: Float = 2.8f
)

private class LivePenTipTracker {
    var tip: Offset? = null
    var color: Color = ExcalidrawRoyalBlue
}

/**
 * خلفية كراسة مربعات بيضاء نظيفة (#FFFFFF مع شبكة #E2E8F0) مطابقة لمحرك Excalidraw.
 */
@Composable
fun ExcalidrawGridPaperBox(
    modifier: Modifier = Modifier,
    gridSpacing: Dp = 22.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(Color.White)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val step = gridSpacing.toPx().coerceAtLeast(16f)
            val strokePx = 0.85.dp.toPx()
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = ExcalidrawGridLine,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokePx
                )
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = ExcalidrawGridLine,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokePx
                )
                y += step
            }
        }
        content()
    }
}

/**
 * شريط أدوات Excalidraw التفاعلي للطالب: تبديل التصفح/الرسم + قلم/سهم/مربع/دائرة/ممحاة + 4 ألوان + تراجع ومسح.
 */
@Composable
fun InteractiveExcalidrawToolbar(
    activeTool: ExcalidrawDrawTool,
    selectedColorHex: Long,
    canUndo: Boolean,
    onSelectTool: (ExcalidrawDrawTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onUndo: () -> Unit,
    onClearInk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = listOf(0xFF1E1B18L, 0xFF1A6FE3L, 0xFFD97706L, 0xFF10B981L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("excalidraw_interactive_toolbar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExcalidrawDrawTool.entries.forEach { tool ->
            val selected = activeTool == tool
            Surface(
                onClick = { onSelectTool(tool) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) ExcalidrawRoyalBlue else Color.White,
                border = BorderStroke(
                    1.dp,
                    if (selected) ExcalidrawRoyalBlue else ExcalidrawTableBorder
                ),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("excalidraw_tool_${tool.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.labelAr,
                        tint = if (selected) Color.White else ExcalidrawCharcoal,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = tool.labelAr,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else ExcalidrawCharcoal
                    )
                }
            }
        }

        // 4 Brand Ink Color Swatches
        palette.forEach { hex ->
            val chosen = selectedColorHex == hex
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(hex))
                    .clickable { onSelectColor(hex) }
                    .padding(2.dp)
                    .testTag("excalidraw_color_${hex.toString(16)}"),
                contentAlignment = Alignment.Center
            ) {
                if (chosen) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        if (canUndo) {
            Surface(
                onClick = onUndo,
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ExcalidrawTableBorder),
                modifier = Modifier
                    .size(34.dp)
                    .testTag("excalidraw_undo_ink")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "تراجع",
                        tint = ExcalidrawCharcoal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Surface(
                onClick = onClearInk,
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .size(34.dp)
                    .testTag("excalidraw_clear_ink")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "مسح رسمي",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * عارض الرسومات المتجهية بأسلوب Excalidraw RoughJS اليدوي على كراسة المربعات:
 * - يرسم كل ضلع ومنحنى وسهم بأسلوب الخط اليدوي المزدوج (RoughJS Organic Hand-Drawn Strokes).
 * - يعرض قلماً ذكياً متوهجاً يتحرك حياً على رأس كل خط أثناء الرسم عبر `PathMeasure.getPosTan`.
 * - يدعم الرسم اليدوي المباشر للطالب (قلم حر، سهم، مربع، دائرة، ممحاة) فوق المخطط.
 */
@Composable
fun ExcalidrawBoardRenderer(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    modifier: Modifier = Modifier,
    activeTool: ExcalidrawDrawTool = ExcalidrawDrawTool.PAN_SCROLL,
    selectedColorHex: Long = 0xFF1A6FE3L,
    studentStrokes: List<StudentInkElement> = emptyList(),
    onAddStudentStroke: (StudentInkElement) -> Unit = {},
    onEraseNearPoint: (Offset) -> Unit = {}
) {
    var activeDraftStroke by remember { mutableStateOf<StudentInkElement?>(null) }

    val drawGestureModifier = if (activeTool == ExcalidrawDrawTool.PAN_SCROLL) {
        Modifier
    } else {
        Modifier.pointerInput(activeTool, selectedColorHex) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    if (activeTool == ExcalidrawDrawTool.ERASER) {
                        onEraseNearPoint(startOffset)
                    } else {
                        activeDraftStroke = StudentInkElement(
                            tool = activeTool,
                            colorHex = selectedColorHex,
                            points = listOf(startOffset)
                        )
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val pos = change.position
                    if (activeTool == ExcalidrawDrawTool.ERASER) {
                        onEraseNearPoint(pos)
                    } else {
                        val current = activeDraftStroke ?: return@detectDragGestures
                        activeDraftStroke = if (activeTool == ExcalidrawDrawTool.FREEHAND_PEN) {
                            current.copy(points = current.points + pos)
                        } else {
                            current.copy(points = listOf(current.points.first(), pos))
                        }
                    }
                },
                onDragEnd = {
                    val finished = activeDraftStroke
                    activeDraftStroke = null
                    if (finished != null && finished.points.size >= 2) {
                        onAddStudentStroke(finished)
                    }
                },
                onDragCancel = {
                    activeDraftStroke = null
                }
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(drawGestureModifier)
            .testTag("excalidraw_board_canvas")
    ) {
        val w = size.width
        val h = size.height
        val strokePx = 2.4.dp.toPx()
        val tracker = LivePenTipTracker()

        if (lesson.excalidrawElements.size >= 3) {
            drawDynamicExcalidrawElements(
                elements = lesson.excalidrawElements,
                progress = progress,
                w = w,
                h = h,
                tracker = tracker
            )
        } else {
            when (lesson.sceneType) {
                WhiteboardGeometryScene.ELECTRIC_CIRCUIT ->
                    drawElectricCircuitScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.SHADED_CIRCLES_RECT ->
                    drawShadedCirclesRectScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.RIGHT_TRIANGLE_PYTHAGORAS ->
                    drawRightTriangleScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.CIRCLE_PROPERTIES ->
                    drawCirclePropertiesScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.PHYSICS_FORCE_MOTION ->
                    drawPhysicsForceScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.COORDINATE_GRAPH ->
                    drawCoordinateGraphScene(lesson, progress, w, h, strokePx, tracker)
                WhiteboardGeometryScene.OPTICS_LENS ->
                    drawOpticsLensScene(lesson, progress, w, h, strokePx, tracker)
            }
        }

        // Draw live moving glowing Smart Stylus Pen at the active ink tip while sketching
        if (progress in 0.01f..0.985f) {
            tracker.tip?.let { tipPos ->
                drawLiveSmartStylusCursor(tipPos, tracker.color)
            }
        }

        // Draw Student Interactive Excalidraw Ink Layer
        studentStrokes.forEach { drawStudentInkElement(it) }
        activeDraftStroke?.let { drawStudentInkElement(it) }
    }
}

private fun DrawScope.drawStudentInkElement(el: StudentInkElement) {
    if (el.points.size < 2) return
    val color = Color(el.colorHex)
    val stroke = el.strokeWidthDp.dp.toPx()
    when (el.tool) {
        ExcalidrawDrawTool.FREEHAND_PEN -> {
            val path = Path().apply {
                moveTo(el.points.first().x, el.points.first().y)
                for (i in 1 until el.points.size) {
                    val prev = el.points[i - 1]
                    val curr = el.points[i]
                    quadraticTo(prev.x, prev.y, (prev.x + curr.x) * 0.5f, (prev.y + curr.y) * 0.5f)
                }
                lineTo(el.points.last().x, el.points.last().y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        ExcalidrawDrawTool.ARROW -> {
            val p1 = el.points.first()
            val p2 = el.points.last()
            val rough = buildRoughLinePath(p1, p2, seed = (p1.x + p2.y).toInt())
            drawPath(rough, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawArrowHead(p1, p2, color, stroke)
        }
        ExcalidrawDrawTool.RECT -> {
            val p1 = el.points.first()
            val p2 = el.points.last()
            val rough = buildRoughRectPath(
                minOf(p1.x, p2.x),
                minOf(p1.y, p2.y),
                maxOf(p1.x, p2.x),
                maxOf(p1.y, p2.y),
                seed = (p1.x + p2.x).toInt()
            )
            drawPath(rough, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        ExcalidrawDrawTool.CIRCLE -> {
            val p1 = el.points.first()
            val p2 = el.points.last()
            val r = hypot(p2.x - p1.x, p2.y - p1.y).coerceAtLeast(8f)
            val rough = buildRoughCirclePath(p1, r, seed = p1.x.toInt())
            drawPath(rough, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        else -> Unit
    }
}

/**
 * مؤشر القلم الذكي الحي المتوهج الذي يتحرك فعلياً على مسار الرسم لحظة بلحظة.
 */
private fun DrawScope.drawLiveSmartStylusCursor(tip: Offset, inkColor: Color) {
    val glowR = 14.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.55f), Color.Transparent),
            center = tip,
            radius = glowR
        ),
        radius = glowR,
        center = tip
    )
    drawCircle(
        color = inkColor,
        radius = 3.2.dp.toPx(),
        center = tip
    )
    // Angled stylus body extending up-right from the exact ink tip
    val shaftStart = Offset(tip.x + 3.dp.toPx(), tip.y - 3.dp.toPx())
    val shaftEnd = Offset(tip.x + 24.dp.toPx(), tip.y - 24.dp.toPx())
    val capEnd = Offset(tip.x + 29.dp.toPx(), tip.y - 29.dp.toPx())

    // Nib
    drawLine(
        color = ExcalidrawCharcoal,
        start = tip,
        end = shaftStart,
        strokeWidth = 2.6.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Metallic navy stylus body
    drawLine(
        color = Color(0xFF1E3A8A),
        start = shaftStart,
        end = shaftEnd,
        strokeWidth = 6.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Glowing cyan smart ring
    drawLine(
        color = Color(0xFF38BDF8),
        start = Offset(tip.x + 17.dp.toPx(), tip.y - 17.dp.toPx()),
        end = Offset(tip.x + 20.dp.toPx(), tip.y - 20.dp.toPx()),
        strokeWidth = 6.8.dp.toPx(),
        cap = StrokeCap.Butt
    )
    // Stylus top cap
    drawLine(
        color = ExcalidrawRoyalBlue,
        start = shaftEnd,
        end = capEnd,
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private fun roughJitter(seed: Int, amplitude: Float): Float {
    val s = sin(seed * 12.9898 + 78.233) * 43758.5453
    val frac = (s - kotlin.math.floor(s)).toFloat()
    return (frac - 0.5f) * 2f * amplitude
}

/**
 * بناء مسار خط يدوي عضوي بأسلوب Excalidraw RoughJS (بضربتين طفيفتي الاهتزاز).
 */
private fun buildRoughLinePath(start: Offset, end: Offset, seed: Int, wobblePx: Float = 2.4f): Path {
    val midX = (start.x + end.x) * 0.5f
    val midY = (start.y + end.y) * 0.5f
    return Path().apply {
        // Stroke 1
        moveTo(start.x + roughJitter(seed + 1, wobblePx * 0.45f), start.y + roughJitter(seed + 2, wobblePx * 0.45f))
        quadraticTo(
            midX + roughJitter(seed + 3, wobblePx),
            midY + roughJitter(seed + 4, wobblePx),
            end.x + roughJitter(seed + 5, wobblePx * 0.45f),
            end.y + roughJitter(seed + 6, wobblePx * 0.45f)
        )
        // Subtle organic Excalidraw second pass
        moveTo(start.x + roughJitter(seed + 7, wobblePx * 0.5f), start.y + roughJitter(seed + 8, wobblePx * 0.5f))
        quadraticTo(
            midX + roughJitter(seed + 9, wobblePx * 0.85f),
            midY + roughJitter(seed + 10, wobblePx * 0.85f),
            end.x + roughJitter(seed + 11, wobblePx * 0.5f),
            end.y + roughJitter(seed + 12, wobblePx * 0.5f)
        )
    }
}

private fun buildRoughRectPath(left: Float, top: Float, right: Float, bottom: Float, seed: Int, wobblePx: Float = 2.2f): Path {
    val tl = Offset(left, top)
    val tr = Offset(right, top)
    val br = Offset(right, bottom)
    val bl = Offset(left, bottom)
    return Path().apply {
        addPath(buildRoughLinePath(tl, tr, seed + 10, wobblePx))
        addPath(buildRoughLinePath(tr, br, seed + 25, wobblePx))
        addPath(buildRoughLinePath(br, bl, seed + 40, wobblePx))
        addPath(buildRoughLinePath(bl, tl, seed + 55, wobblePx))
    }
}

private fun buildRoughCirclePath(center: Offset, radius: Float, seed: Int, wobblePx: Float = 2.2f): Path {
    val steps = 28
    return Path().apply {
        for (pass in 0..1) {
            val startAngle = pass * 0.18
            for (i in 0..steps) {
                val angle = startAngle + (2.0 * PI * i / steps) + (pass * 0.05)
                val rJitter = radius + roughJitter(seed + pass * 100 + i * 3, wobblePx)
                val x = center.x + (rJitter * cos(angle)).toFloat()
                val y = center.y + (rJitter * sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
    }
}

private fun DrawScope.drawDynamicExcalidrawElements(
    elements: List<ExcalidrawElementData>,
    progress: Float,
    w: Float,
    h: Float,
    tracker: LivePenTipTracker
) {
    val total = elements.size.coerceAtLeast(1)
    elements.forEachIndexed { idx, el ->
        val startFrac = idx.toFloat() / total.toFloat()
        val endFrac = ((idx + 1).toFloat() / total.toFloat()).coerceAtMost(1f)
        val localFrac = ((progress - startFrac) / (endFrac - startFrac).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
        if (localFrac <= 0f) return@forEachIndexed

        val isCurrentActive = localFrac < 0.995f
        val color = Color(el.colorHex)
        val stroke = el.strokeWidthDp.dp.toPx()
        val p1 = Offset(w * el.x1, h * el.y1)
        val p2 = Offset(w * el.x2, h * el.y2)
        val seed = (idx + 1) * 37

        when (el.type) {
            ExcalidrawElementType.LINE -> {
                val path = buildRoughLinePath(p1, p2, seed)
                drawMeasuredPath(path, localFrac, color, stroke, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.ARROW -> {
                val path = buildRoughLinePath(p1, p2, seed)
                drawMeasuredPath(path, localFrac, color, stroke, if (isCurrentActive) tracker else null)
                if (localFrac > 0.85f) {
                    drawArrowHead(p1, p2, color, stroke)
                }
            }
            ExcalidrawElementType.RECT -> {
                val left = minOf(p1.x, p2.x)
                val top = minOf(p1.y, p2.y)
                val right = maxOf(p1.x, p2.x)
                val bottom = maxOf(p1.y, p2.y)
                drawRoundRect(
                    color = color.copy(alpha = 0.08f * localFrac),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(4f), (bottom - top).coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
                val path = buildRoughRectPath(left, top, right, bottom, seed)
                drawMeasuredPath(path, localFrac, color, stroke, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.CIRCLE -> {
                val r = (minOf(w, h) * el.radius).coerceIn(14.dp.toPx(), minOf(w, h) * 0.38f)
                drawCircle(
                    color = color.copy(alpha = 0.09f * localFrac),
                    radius = r,
                    center = p1
                )
                val path = buildRoughCirclePath(p1, r, seed)
                drawMeasuredPath(path, localFrac, color, stroke, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.RESISTOR -> {
                drawZigZagResistor(p1, p2, color, stroke, localFrac, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.BATTERY -> {
                drawBatterySymbol(p1, color, stroke, horizontal = el.x2 > el.x1)
                if (isCurrentActive) {
                    tracker.tip = p1
                    tracker.color = color
                }
            }
            ExcalidrawElementType.TRIANGLE -> {
                val p3 = Offset(w * el.x3, h * el.y3)
                val path = Path().apply {
                    addPath(buildRoughLinePath(p1, p2, seed + 1))
                    addPath(buildRoughLinePath(p2, p3, seed + 13))
                    addPath(buildRoughLinePath(p3, p1, seed + 27))
                }
                drawMeasuredPath(path, localFrac, color, stroke, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.NODE -> {
                val halfW = (minOf(w, h) * el.radius * 1.48f).coerceIn(36.dp.toPx(), w * 0.34f)
                val halfH = (minOf(w, h) * el.radius * 0.68f).coerceIn(16.dp.toPx(), h * 0.16f)
                val left = (p1.x - halfW).coerceIn(6.dp.toPx(), w - halfW * 2 - 6.dp.toPx())
                val top = (p1.y - halfH).coerceIn(6.dp.toPx(), h - halfH * 2 - 6.dp.toPx())
                val right = left + halfW * 2
                val bottom = top + halfH * 2
                drawRoundRect(
                    color = color.copy(alpha = 0.11f * localFrac),
                    topLeft = Offset(left, top),
                    size = Size(halfW * 2, halfH * 2),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                val roughBox = buildRoughRectPath(left, top, right, bottom, seed, wobblePx = 1.9f)
                drawMeasuredPath(roughBox, localFrac, color, stroke, if (isCurrentActive) tracker else null)
            }
            ExcalidrawElementType.LABEL -> {
                if (isCurrentActive) {
                    tracker.tip = p1
                    tracker.color = color
                }
            }
        }

        if (el.label.isNotBlank() && localFrac > 0.35f) {
            // Live typewriter reveal for labels inside the canvas too!
            val labelReveal = ((localFrac - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val visibleCount = (el.label.length * labelReveal).toInt().coerceIn(1, el.label.length)
            val visibleLabel = el.label.take(visibleCount)
            val centerOffset = when (el.type) {
                ExcalidrawElementType.LABEL,
                ExcalidrawElementType.NODE,
                ExcalidrawElementType.CIRCLE -> Offset(p1.x, p1.y + 4.dp.toPx())
                ExcalidrawElementType.RECT -> Offset((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f + 4.dp.toPx())
                else -> Offset((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f - 10.dp.toPx())
            }
            drawCanvasLabel(visibleLabel, centerOffset, color, 12.sp.toPx())
        }
    }
}

/**
 * مشهد الدائرة الكهربائية والمقاومات الملونة بأسلوب Excalidraw اليدوي + حركة القلم الحي.
 */
private fun DrawScope.drawElectricCircuitScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val leftX = w * 0.17f
    val rightX = w * 0.80f
    val topY = h * 0.24f
    val bottomY = h * 0.78f
    val midTopX = w * 0.45f
    val triLeftX = w * 0.27f

    val wireFrac = (progress / 0.45f).coerceIn(0f, 1f)
    val outerWires = Path().apply {
        addPath(buildRoughLinePath(Offset(leftX, topY), Offset(leftX, h * 0.45f), 11, 1.4f))
        addPath(buildRoughLinePath(Offset(leftX, h * 0.57f), Offset(leftX, bottomY), 19, 1.4f))
        addPath(buildRoughLinePath(Offset(leftX, bottomY), Offset(w * 0.44f, bottomY), 23, 1.4f))
        addPath(buildRoughLinePath(Offset(w * 0.52f, bottomY), Offset(rightX, bottomY), 29, 1.4f))
        addPath(buildRoughLinePath(Offset(rightX, bottomY), Offset(rightX, h * 0.62f), 31, 1.4f))
        addPath(buildRoughLinePath(Offset(rightX, topY), Offset(rightX, h * 0.39f), 37, 1.4f))
        addPath(buildRoughLinePath(Offset(leftX, topY), Offset(w * 0.25f, topY), 41, 1.4f))
        addPath(buildRoughLinePath(Offset(w * 0.37f, topY), Offset(w * 0.54f, topY), 43, 1.4f))
        addPath(buildRoughLinePath(Offset(w * 0.68f, topY), Offset(rightX, topY), 47, 1.4f))
    }
    drawMeasuredPath(outerWires, wireFrac, ExcalidrawCharcoal, strokePx, if (wireFrac < 0.99f) tracker else null)

    if (progress > 0.18f) {
        drawBatterySymbol(Offset(leftX, h * 0.51f), ExcalidrawCharcoal, strokePx, horizontal = false)
        drawBatterySymbol(Offset(w * 0.48f, bottomY), ExcalidrawCharcoal, strokePx, horizontal = true)
    }

    if (progress > 0.28f) {
        val r1Frac = ((progress - 0.28f) / 0.22f).coerceIn(0f, 1f)
        drawZigZagResistor(
            start = Offset(w * 0.25f, topY),
            end = Offset(w * 0.37f, topY),
            color = ExcalidrawRoyalBlue,
            strokeWidth = strokePx,
            fraction = r1Frac,
            tracker = if (r1Frac < 0.99f) tracker else null
        )
    }

    if (progress > 0.42f) {
        val r2Frac = ((progress - 0.42f) / 0.22f).coerceIn(0f, 1f)
        drawZigZagResistor(
            start = Offset(w * 0.54f, topY),
            end = Offset(w * 0.68f, topY),
            color = ExcalidrawAmberGold,
            strokeWidth = strokePx,
            fraction = r2Frac,
            tracker = if (r2Frac < 0.99f) tracker else null
        )
    }

    if (progress > 0.54f) {
        val r3Frac = ((progress - 0.54f) / 0.22f).coerceIn(0f, 1f)
        drawZigZagResistor(
            start = Offset(rightX, h * 0.39f),
            end = Offset(rightX, h * 0.62f),
            color = ExcalidrawEmerald,
            strokeWidth = strokePx,
            fraction = r3Frac,
            tracker = if (r3Frac < 0.99f) tracker else null
        )
    }

    if (progress > 0.58f) {
        val triFrac = ((progress - 0.58f) / 0.32f).coerceIn(0f, 1f)
        val diagPath = Path().apply {
            addPath(buildRoughLinePath(Offset(triLeftX, bottomY), Offset(midTopX, topY), 71, 1.5f))
            addPath(buildRoughLinePath(Offset(triLeftX, bottomY), Offset(rightX, topY), 79, 1.5f))
            addPath(buildRoughLinePath(Offset(midTopX, topY), Offset(w * 0.55f, h * 0.50f), 83, 1.5f))
        }
        drawMeasuredPath(diagPath, triFrac, Color(0xFF1E3A8A), strokePx * 0.92f, if (triFrac < 0.99f) tracker else null)

        if (triFrac > 0.8f) {
            val footX = w * 0.55f
            val footY = h * 0.50f
            val sq = 7.dp.toPx()
            drawRect(
                color = ExcalidrawRoyalBlue.copy(alpha = 0.2f),
                topLeft = Offset(footX - sq, footY - sq),
                size = Size(sq, sq)
            )
            drawRect(
                color = ExcalidrawRoyalBlue,
                topLeft = Offset(footX - sq, footY - sq),
                size = Size(sq, sq),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }

    if (progress > 0.72f) {
        val textSize = 12.5.sp.toPx()
        val vLabel = lesson.dimensionLabel.ifBlank { "3 V" }
        val r1Label = lesson.radiusLabel.ifBlank { "R₁ = 2 Ω" }
        val r2Label = lesson.secondaryLabel.ifBlank { "R₂ = 4 Ω" }
        drawCanvasLabel(vLabel, Offset(w * 0.085f, h * 0.525f), ExcalidrawRoyalBlue, textSize)
        drawCanvasLabel(r1Label, Offset(w * 0.31f, topY - 14.dp.toPx()), ExcalidrawRoyalBlue, textSize)
        drawCanvasLabel(r2Label, Offset(w * 0.61f, topY - 14.dp.toPx()), ExcalidrawAmberGold, textSize)
        drawCanvasLabel("R_eq", Offset(w * 0.90f, h * 0.52f), ExcalidrawEmerald, textSize)
        drawCanvasLabel("I = V / R", Offset(w * 0.48f, bottomY + 18.dp.toPx()), ExcalidrawCharcoal, textSize)
    }
}

private fun DrawScope.drawShadedCirclesRectScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val rectW = w * 0.72f
    val circleCount = 3
    val radius = rectW / (circleCount * 2f)
    val rectH = radius * 2f
    val left = (w - rectW) / 2f
    val top = (h - rectH) / 2f - 4.dp.toPx()

    val fullShapePath = Path().apply {
        addPath(buildRoughRectPath(left, top, left + rectW, top + rectH, 101, 1.8f))
        for (i in 0 until circleCount) {
            val cx = left + radius + i * radius * 2f
            val cy = top + radius
            addPath(buildRoughCirclePath(Offset(cx, cy), radius, 130 + i * 29, 1.6f))
        }
    }

    if (progress > 0.35f) {
        val hatchProgress = ((progress - 0.35f) / 0.4f).coerceIn(0f, 1f)
        drawCornerHatching(left, top, rectW, rectH, radius, circleCount, hatchProgress)
    }

    val shapeFrac = (progress / 0.65f).coerceIn(0f, 1f)
    drawMeasuredPath(fullShapePath, shapeFrac, ExcalidrawCharcoal, strokePx, if (shapeFrac < 0.99f) tracker else null)

    if (progress > 0.55f) {
        val labelFraction = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val cy = top + radius
        val cRightX = left + rectW - radius
        val rEnd = Offset(cRightX + radius * labelFraction, cy)
        drawLine(ExcalidrawRoyalBlue, Offset(cRightX, cy), rEnd, 2.2.dp.toPx(), StrokeCap.Round)
        drawLine(ExcalidrawAmberGold, Offset(left + radius, cy), Offset(left + radius, cy - radius * labelFraction), 2.2.dp.toPx(), StrokeCap.Round)
        val dimY = top + rectH + 16.dp.toPx()
        val endX = left + rectW * labelFraction
        drawLine(ExcalidrawEmerald, Offset(left, dimY), Offset(endX, dimY), 2.4.dp.toPx(), StrokeCap.Round)
        if (labelFraction < 0.98f) {
            tracker.tip = Offset(endX, dimY)
            tracker.color = ExcalidrawEmerald
        }
        drawLine(ExcalidrawEmerald, Offset(left, dimY - 5.dp.toPx()), Offset(left, dimY + 5.dp.toPx()), 2.dp.toPx())
        if (labelFraction > 0.9f) {
            drawLine(ExcalidrawEmerald, Offset(left + rectW, dimY - 5.dp.toPx()), Offset(left + rectW, dimY + 5.dp.toPx()), 2.dp.toPx())
            val ts = 12.sp.toPx()
            drawCanvasLabel(lesson.dimensionLabel.ifBlank { "d = 4" }, Offset(w * 0.5f, dimY + 14.dp.toPx()), ExcalidrawEmerald, ts)
            drawCanvasLabel(lesson.radiusLabel.ifBlank { "r = 2" }, Offset(cRightX + radius * 0.45f, cy - 8.dp.toPx()), ExcalidrawRoyalBlue, ts)
        }
    }
}

private fun DrawScope.drawRightTriangleScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val ax = w * 0.28f
    val ay = h * 0.16f
    val bx = w * 0.28f
    val by = h * 0.80f
    val cx = w * 0.74f
    val cy = h * 0.80f

    val triPath = Path().apply {
        addPath(buildRoughLinePath(Offset(ax, ay), Offset(bx, by), 211))
        addPath(buildRoughLinePath(Offset(bx, by), Offset(cx, cy), 223))
        addPath(buildRoughLinePath(Offset(cx, cy), Offset(ax, ay), 239))
    }
    if (progress > 0.4f) {
        drawRect(
            color = ExcalidrawRoyalBlue.copy(alpha = 0.20f),
            topLeft = Offset(bx, by - 20.dp.toPx()),
            size = Size(20.dp.toPx(), 20.dp.toPx())
        )
        drawRect(
            color = ExcalidrawRoyalBlue,
            topLeft = Offset(bx, by - 20.dp.toPx()),
            size = Size(20.dp.toPx(), 20.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
    val f = (progress / 0.7f).coerceIn(0f, 1f)
    drawMeasuredPath(triPath, f, ExcalidrawCharcoal, strokePx, if (f < 0.99f) tracker else null)
    if (progress > 0.65f) {
        val hf = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
        val hypPath = buildRoughLinePath(Offset(ax, ay), Offset(cx, cy), 251)
        drawMeasuredPath(hypPath, hf, ExcalidrawEmerald, 3.dp.toPx(), if (hf < 0.99f) tracker else null)
        val ts = 12.5.sp.toPx()
        drawCanvasLabel(lesson.secondaryLabel.ifBlank { "a = 3" }, Offset(bx - 26.dp.toPx(), (ay + by) * 0.5f), ExcalidrawRoyalBlue, ts)
        drawCanvasLabel(lesson.radiusLabel.ifBlank { "b = 4" }, Offset((bx + cx) * 0.5f, by + 16.dp.toPx()), ExcalidrawAmberGold, ts)
        drawCanvasLabel(lesson.dimensionLabel.ifBlank { "c = 5" }, Offset((ax + cx) * 0.5f + 22.dp.toPx(), (ay + cy) * 0.5f - 8.dp.toPx()), ExcalidrawEmerald, ts)
    }
}

private fun DrawScope.drawCirclePropertiesScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val r = minOf(w, h) * 0.35f
    val cx = w * 0.5f
    val cy = h * 0.48f
    val circlePath = buildRoughCirclePath(Offset(cx, cy), r, 307)
    if (progress > 0.4f) {
        drawCircle(ExcalidrawRoyalBlue.copy(alpha = 0.10f * ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)), r, Offset(cx, cy))
    }
    val cf = (progress / 0.7f).coerceIn(0f, 1f)
    drawMeasuredPath(circlePath, cf, ExcalidrawCharcoal, strokePx, if (cf < 0.99f) tracker else null)
    if (progress > 0.6f) {
        val f = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val endPt = Offset(cx + r * f, cy)
        drawLine(ExcalidrawAmberGold, Offset(cx, cy), endPt, 2.6.dp.toPx(), StrokeCap.Round)
        if (f < 0.98f) {
            tracker.tip = endPt
            tracker.color = ExcalidrawAmberGold
        }
        drawCircle(ExcalidrawRoyalBlue, radius = 4.dp.toPx(), center = Offset(cx, cy))
        if (f > 0.85f) {
            drawCanvasLabel(lesson.radiusLabel.ifBlank { "r = 7" }, Offset(cx + r * 0.5f, cy - 10.dp.toPx()), ExcalidrawAmberGold, 12.5.sp.toPx())
            drawCanvasLabel(lesson.dimensionLabel.ifBlank { "d = 14" }, Offset(cx, cy + r + 16.dp.toPx()), ExcalidrawEmerald, 12.5.sp.toPx())
        }
    }
}

private fun DrawScope.drawPhysicsForceScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val boxW = w * 0.25f
    val boxH = h * 0.34f
    val left = w * 0.30f
    val top = h * 0.38f
    val floorY = top + boxH
    val boxPath = buildRoughRectPath(left, top, left + boxW, floorY, 401)
    drawPath(buildRoughLinePath(Offset(w * 0.12f, floorY), Offset(w * 0.88f, floorY), 409), ExcalidrawCharcoal.copy(alpha = 0.55f), style = Stroke(2.dp.toPx()))
    val bf = (progress / 0.6f).coerceIn(0f, 1f)
    drawMeasuredPath(boxPath, bf, ExcalidrawCharcoal, strokePx, if (bf < 0.99f) tracker else null)
    if (progress > 0.5f) {
        val f = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val arrowStart = Offset(left + boxW, top + boxH * 0.5f)
        val arrowEnd = Offset(left + boxW + w * 0.26f * f, top + boxH * 0.5f)
        drawLine(ExcalidrawEmerald, arrowStart, arrowEnd, 3.2.dp.toPx(), StrokeCap.Round)
        if (f < 0.98f) {
            tracker.tip = arrowEnd
            tracker.color = ExcalidrawEmerald
        }
        if (f > 0.85f) {
            drawArrowHead(arrowStart, arrowEnd, ExcalidrawEmerald, 3.dp.toPx())
            val ts = 12.5.sp.toPx()
            drawCanvasLabel(lesson.radiusLabel.ifBlank { "m = 5 kg" }, Offset(left + boxW * 0.5f, top + boxH * 0.55f), ExcalidrawRoyalBlue, ts)
            drawCanvasLabel(lesson.dimensionLabel.ifBlank { "F = 20 N" }, Offset((arrowStart.x + arrowEnd.x) * 0.5f, arrowStart.y - 12.dp.toPx()), ExcalidrawEmerald, ts)
        }
    }
}

private fun DrawScope.drawCoordinateGraphScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val cx = w * 0.5f
    val cy = h * 0.72f
    drawPath(buildRoughLinePath(Offset(w * 0.15f, cy), Offset(w * 0.85f, cy), 503), ExcalidrawCharcoal.copy(alpha = 0.65f), style = Stroke(2.dp.toPx()))
    drawPath(buildRoughLinePath(Offset(cx, h * 0.12f), Offset(cx, h * 0.88f), 509), ExcalidrawCharcoal.copy(alpha = 0.65f), style = Stroke(2.dp.toPx()))
    val curvePath = Path().apply {
        moveTo(w * 0.25f, h * 0.18f)
        quadraticTo(cx, cy + h * 0.40f, w * 0.75f, h * 0.18f)
    }
    val f = (progress / 0.85f).coerceIn(0f, 1f)
    drawMeasuredPath(curvePath, f, ExcalidrawRoyalBlue, 3.dp.toPx(), if (f < 0.99f) tracker else null)
    if (progress > 0.8f) {
        drawCircle(ExcalidrawAmberGold, radius = 5.dp.toPx(), center = Offset(cx, cy))
        drawCanvasLabel("y = x²", Offset(w * 0.76f, h * 0.24f), ExcalidrawRoyalBlue, 12.5.sp.toPx())
        drawCanvasLabel(lesson.radiusLabel.ifBlank { "(0, 0)" }, Offset(cx + 22.dp.toPx(), cy + 16.dp.toPx()), ExcalidrawAmberGold, 11.5.sp.toPx())
    }
}

private fun DrawScope.drawOpticsLensScene(
    lesson: FahimWhiteboardLesson,
    progress: Float,
    w: Float,
    h: Float,
    strokePx: Float,
    tracker: LivePenTipTracker
) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    drawLine(ExcalidrawCharcoal.copy(alpha = 0.5f), Offset(w * 0.10f, cy), Offset(w * 0.90f, cy), 1.8.dp.toPx())
    val lensPath = Path().apply {
        moveTo(cx, h * 0.15f)
        quadraticTo(cx + w * 0.09f, cy, cx, h * 0.85f)
        quadraticTo(cx - w * 0.09f, cy, cx, h * 0.15f)
    }
    val lf = (progress / 0.5f).coerceIn(0f, 1f)
    drawMeasuredPath(lensPath, lf, ExcalidrawRoyalBlue, strokePx, if (lf < 0.99f) tracker else null)
    if (progress > 0.45f) {
        val f = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val focusX = w * 0.74f
        val topRay = Path().apply {
            moveTo(w * 0.14f, h * 0.30f)
            lineTo(cx, h * 0.30f)
            lineTo(focusX, cy)
            lineTo(w * 0.86f, h * 0.62f)
        }
        val botRay = Path().apply {
            moveTo(w * 0.14f, h * 0.70f)
            lineTo(cx, h * 0.70f)
            lineTo(focusX, cy)
            lineTo(w * 0.86f, h * 0.38f)
        }
        drawMeasuredPath(topRay, f, ExcalidrawAmberGold, 2.2.dp.toPx(), if (f < 0.99f) tracker else null)
        drawMeasuredPath(botRay, f, ExcalidrawEmerald, 2.2.dp.toPx())
        if (f > 0.8f) {
            drawCircle(ExcalidrawRoyalBlue, 4.5.dp.toPx(), Offset(focusX, cy))
            drawCanvasLabel("F (${lesson.dimensionLabel.ifBlank { "f = 10 cm" }})", Offset(focusX, cy + 18.dp.toPx()), ExcalidrawRoyalBlue, 12.sp.toPx())
        }
    }
}

private fun DrawScope.drawZigZagResistor(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float,
    fraction: Float,
    tracker: LivePenTipTracker? = null
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dist = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / dist
    val uy = dy / dist
    val px = -uy
    val py = ux
    val amp = 6.dp.toPx()
    val peaks = 6
    val path = Path().apply {
        moveTo(start.x, start.y)
        for (i in 1 until peaks) {
            val t = i.toFloat() / peaks.toFloat()
            val sign = if (i % 2 == 1) 1f else -1f
            lineTo(start.x + dx * t + px * amp * sign, start.y + dy * t + py * amp * sign)
        }
        lineTo(end.x, end.y)
    }
    drawMeasuredPath(path, fraction, color, strokeWidth, tracker)
}

private fun DrawScope.drawBatterySymbol(
    center: Offset,
    color: Color,
    strokeWidth: Float,
    horizontal: Boolean
) {
    val longHalf = 11.dp.toPx()
    val shortHalf = 6.dp.toPx()
    val gap = 4.dp.toPx()
    if (!horizontal) {
        drawLine(color, Offset(center.x - longHalf, center.y - gap), Offset(center.x + longHalf, center.y - gap), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(center.x - shortHalf, center.y + gap), Offset(center.x + shortHalf, center.y + gap), strokeWidth * 1.25f, StrokeCap.Round)
    } else {
        drawLine(color, Offset(center.x - gap, center.y - longHalf), Offset(center.x - gap, center.y + longHalf), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(center.x + gap, center.y - shortHalf), Offset(center.x + gap, center.y + shortHalf), strokeWidth * 1.25f, StrokeCap.Round)
    }
}

private fun DrawScope.drawArrowHead(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val headLen = 10.dp.toPx()
    val spread = Math.toRadians(28.0)
    val p1 = Offset(
        end.x - (headLen * cos(angle - spread)).toFloat(),
        end.y - (headLen * sin(angle - spread)).toFloat()
    )
    val p2 = Offset(
        end.x - (headLen * cos(angle + spread)).toFloat(),
        end.y - (headLen * sin(angle + spread)).toFloat()
    )
    drawLine(color, p1, end, strokeWidth, StrokeCap.Round)
    drawLine(color, p2, end, strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawCanvasLabel(
    text: String,
    center: Offset,
    color: Color,
    textSizePx: Float
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            this.textSize = textSizePx
            this.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            this.textAlign = Paint.Align.CENTER
        }
        canvas.nativeCanvas.drawText(text, center.x, center.y, paint)
    }
}

private fun DrawScope.drawCornerHatching(
    left: Float,
    top: Float,
    rectW: Float,
    rectH: Float,
    radius: Float,
    circleCount: Int,
    fraction: Float
) {
    val rectPath = Path().apply { addRect(Rect(left, top, left + rectW, top + rectH)) }
    val circlesPath = Path().apply {
        for (i in 0 until circleCount) {
            val cx = left + radius + i * radius * 2f
            val cy = top + radius
            addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
        }
    }
    val shadedRegion = Path.combine(PathOperation.Difference, rectPath, circlesPath)
    val step = 6.dp.toPx()
    val maxReach = rectW * fraction

    clipRect(left = left, top = top, right = left + maxReach, bottom = top + rectH) {
        var x = left - rectH
        while (x < left + rectW + rectH) {
            val strokeColor = if (x < left + rectW * 0.5f) {
                ExcalidrawRoyalBlue.copy(alpha = 0.42f)
            } else {
                ExcalidrawAmberGold.copy(alpha = 0.42f)
            }
            drawPath(path = shadedRegion, color = Color.Transparent)
            drawLine(strokeColor, Offset(x, top + rectH), Offset(x + rectH, top), 1.6.dp.toPx())
            x += step
        }
    }

    for (i in 0 until circleCount) {
        val cx = left + radius + i * radius * 2f
        val cy = top + radius
        drawCircle(Color.White, radius = radius - 1.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * يرسم المسار تدريجياً حسب [fraction] (0f..1f) ويُرجع موضع سن القلم الحي [PathMeasure.getPosTan].
 */
private fun DrawScope.drawMeasuredPath(
    fullPath: Path,
    fraction: Float,
    color: Color,
    strokeWidth: Float,
    tracker: LivePenTipTracker? = null
) {
    if (fraction <= 0f) return
    val androidPath = fullPath.asAndroidPath()

    // Compute total length across contours so the pen flows sequentially contour-by-contour
    val countMeasure = android.graphics.PathMeasure(androidPath, false)
    var totalLen = 0f
    do {
        totalLen += countMeasure.length
    } while (countMeasure.nextContour())
    if (totalLen <= 0.01f) return

    val targetDist = totalLen * fraction.coerceIn(0f, 1f)
    val measure = android.graphics.PathMeasure(androidPath, false)
    val partial = android.graphics.Path()
    var remaining = targetDist
    val pos = FloatArray(2)
    val tan = FloatArray(2)

    do {
        val len = measure.length
        if (len <= 0f) continue
        if (remaining >= len) {
            measure.getSegment(0f, len, partial, true)
            if (tracker != null && measure.getPosTan(len, pos, tan)) {
                tracker.tip = Offset(pos[0], pos[1])
                tracker.color = color
            }
            remaining -= len
        } else if (remaining > 0f) {
            measure.getSegment(0f, remaining, partial, true)
            if (tracker != null && measure.getPosTan(remaining, pos, tan)) {
                tracker.tip = Offset(pos[0], pos[1])
                tracker.color = color
            }
            remaining = 0f
            break
        } else {
            break
        }
    } while (measure.nextContour())

    drawPath(
        path = partial.asComposePath(),
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * بطاقة خطوة شرح مفصولة الاتجاه تماماً مع كتابة حيّة بالقلم حرفاً بحرف (`revealFraction`):
 * - السطر العلوي: عنوان وشرح عربي فقط داخل LayoutDirection.Rtl بخط 16.sp Bold.
 * - السطر السفلي: بطاقة معادلة رياضية/فيزيائية صافية داخل LayoutDirection.Ltr بخط 16.sp Medium.
 */
@Composable
fun SeparatedEquationStepCard(
    step: WhiteboardStep,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    revealFraction: Float = 1f,
    isCurrentlyWriting: Boolean = false,
    onStepClick: (() -> Unit)? = null
) {
    val arabicTitle = step.displayArabicTitle()
    val ltrFormula = step.displayLtrFormula()

    val clampedReveal = revealFraction.coerceIn(0f, 1f)
    if (clampedReveal <= 0.01f) return

    // Split writing progress: first 72% writes Arabic explanation, remaining 28% writes LTR equation
    val hasFormula = ltrFormula.isNotBlank()
    val titleFrac = if (hasFormula) (clampedReveal / 0.72f).coerceIn(0f, 1f) else clampedReveal
    val formulaFrac = if (hasFormula) ((clampedReveal - 0.72f) / 0.28f).coerceIn(0f, 1f) else 0f

    val visibleTitleCount = (arabicTitle.length * titleFrac).toInt().coerceIn(1, arabicTitle.length)
    val visibleTitle = arabicTitle.take(visibleTitleCount)
    val visibleFormulaCount = if (hasFormula && formulaFrac > 0.01f) {
        (ltrFormula.length * formulaFrac).toInt().coerceIn(1, ltrFormula.length)
    } else 0
    val visibleFormula = if (visibleFormulaCount > 0) ltrFormula.take(visibleFormulaCount) else ""

    val showCursor = isCurrentlyWriting && clampedReveal < 0.99f
    val blinkTransition = rememberInfiniteTransition(label = "pen_blink")
    val cursorAlpha by blinkTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "pen_cursor_alpha"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            step.isFinalResult -> Color(0xFFECFDF5)
            isActive || showCursor -> Color(0xFFEFF6FF)
            else -> Color.White
        },
        border = BorderStroke(
            width = if (isActive || showCursor || step.isFinalResult) 1.4.dp else 1.dp,
            color = when {
                step.isFinalResult -> ExcalidrawEmerald.copy(alpha = 0.7f)
                isActive || showCursor -> ExcalidrawRoyalBlue.copy(alpha = 0.75f)
                else -> ExcalidrawTableBorder.copy(alpha = 0.7f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onStepClick != null) Modifier.clickable { onStepClick() } else Modifier)
            .testTag("whiteboard_step_${step.index}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Arabic Title & Explanation — Strictly RTL + Live Pen Writing Indicator
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (showCursor && titleFrac < 0.99f) "$visibleTitle ✎" else visibleTitle,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (step.isFinalResult) Color(0xFF065F46) else ExcalidrawCharcoal,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (showCursor) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "فهيم يكتب الآن",
                            tint = ExcalidrawRoyalBlue.copy(alpha = cursorAlpha),
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .size(16.dp)
                        )
                    }
                }
            }

            // 2. Pure Mathematical / Physical Formula — Strictly LTR (Zero overlap!)
            if (hasFormula && visibleFormula.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (step.isFinalResult) Color.White else ExcalidrawRowAlt,
                        border = BorderStroke(
                            1.dp,
                            if (step.isFinalResult) ExcalidrawEmerald.copy(alpha = 0.45f) else ExcalidrawGridLine
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showCursor && formulaFrac < 0.99f) "$visibleFormula ▎" else visibleFormula,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (step.isFinalResult) ExcalidrawEmerald else ExcalidrawRoyalBlue,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * جدول موحد ونظيف للسبورة:
 * - حدود ناعمة 1.dp (#CBD5E1)
 * - رأس أزرق ملكي (#1A6FE3) بنص أبيض 15.sp Bold
 * - صفوف متبادلة (#FFFFFF / #F8FAFC) بنص 14.5.sp وحشوة 12.dp بدون أي اقتطاع للكلمات
 */
@Composable
fun ExcalidrawCleanTable(
    table: StructuredTableData,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ExcalidrawTableBorder),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("excalidraw_structured_table")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (table.title.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = table.title,
                        fontFamily = ReadexProFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ExcalidrawCharcoal
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ExcalidrawTableBorder)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(ExcalidrawRoyalBlue)
            ) {
                table.headers.forEachIndexed { idx, header ->
                    Box(
                        modifier = Modifier
                            .weight(if (idx == 0) 1.05f else 1f)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = header,
                            fontFamily = ReadexProFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                    if (idx < table.headers.lastIndex) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.25f)
                        )
                    }
                }
            }

            table.rows.forEachIndexed { rowIdx, cells ->
                val bg = if (rowIdx % 2 == 0) Color.White else ExcalidrawRowAlt
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(bg)
                ) {
                    cells.forEachIndexed { colIdx, cell ->
                        val isPureMath = cell.any { it in 'A'..'Z' || it in 'a'..'z' || it in setOf('=', '×', '÷', 'Ω', 'π', '²') } &&
                            cell.none { it in '\u0600'..'\u06FF' }
                        Box(
                            modifier = Modifier
                                .weight(if (colIdx == 0) 1.05f else 1f)
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides if (isPureMath) LayoutDirection.Ltr else LocalLayoutDirection.current
                            ) {
                                Text(
                                    text = cell,
                                    fontFamily = if (isPureMath) FontFamily.Monospace else ReadexProFontFamily,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (colIdx == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (colIdx == 0) ExcalidrawCharcoal else Color(0xFF334155),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        if (colIdx < cells.lastIndex) {
                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                thickness = 1.dp,
                                color = ExcalidrawTableBorder.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
                if (rowIdx < table.rows.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = ExcalidrawTableBorder.copy(alpha = 0.7f))
                }
            }
        }
    }
}
