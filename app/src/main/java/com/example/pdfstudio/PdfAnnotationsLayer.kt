package com.example.pdfstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF as AndroidRectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ألوان التظليل الأربعة المحددة في المواصفات (الشكل 2):
 * الأصفر للتعريفات، الأخضر النعناعي للقوانين، الوردي للأسئلة، الأزرق السماوي
 */
val HIGHLIGHTER_4_COLORS = listOf(
    0xFFFDE047L to "أصفر",
    0xFF6EE7B7L to "نعناعي",
    0xFFF9A8D4L to "وردي",
    0xFF93C5FDL to "سماوي"
)

val ANNOTATION_INK_COLORS = listOf(
    0xFFEF4444L, // أحمر
    0xFF1A6FE3L, // أزرق ملكي
    0xFF10B981L, // أخضر
    0xFFFACC15L, // أصفر
    0xFF111827L  // أسود حبر
)

val ANNOTATION_STROKE_WIDTHS = listOf(2.5f, 5f, 9f)

class AnnotationDocumentState {
    private var undoStack = mutableListOf<Map<Int, PageAnnotations>>()
    private var redoStack = mutableListOf<Map<Int, PageAnnotations>>()

    var pagesMap by mutableStateOf<Map<Int, PageAnnotations>>(emptyMap())
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val hasAnyAnnotations: Boolean get() = pagesMap.values.any { !it.isEmpty() }

    private fun pushSnapshot() {
        undoStack.add(pagesMap)
        if (undoStack.size > 40) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(pagesMap)
        pagesMap = undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(pagesMap)
        pagesMap = redoStack.removeAt(redoStack.lastIndex)
    }

    fun addStroke(pageIndex: Int, stroke: PdfStrokePath) {
        if (stroke.points.size < 2) return
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: PageAnnotations()
        pagesMap = pagesMap + (pageIndex to current.copy(strokes = current.strokes + stroke))
    }

    fun addShape(pageIndex: Int, shape: PdfShapeItem) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: PageAnnotations()
        pagesMap = pagesMap + (pageIndex to current.copy(shapes = current.shapes + shape))
    }

    fun addTextNote(pageIndex: Int, note: PdfTextNote) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: PageAnnotations()
        pagesMap = pagesMap + (pageIndex to current.copy(notes = current.notes + note))
    }

    fun updateTextNote(pageIndex: Int, noteId: String, newText: String) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: return
        val updated = current.notes.map { if (it.id == noteId) it.copy(text = newText) else it }
        pagesMap = pagesMap + (pageIndex to current.copy(notes = updated))
    }

    fun removeTextNote(pageIndex: Int, noteId: String) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: return
        pagesMap = pagesMap + (pageIndex to current.copy(notes = current.notes.filterNot { it.id == noteId }))
    }

    fun addTextBox(pageIndex: Int, box: PdfTextBoxItem) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: PageAnnotations()
        pagesMap = pagesMap + (pageIndex to current.copy(textBoxes = current.textBoxes + box))
    }

    fun addStamp(pageIndex: Int, stamp: PdfStampItem) {
        pushSnapshot()
        val current = pagesMap[pageIndex] ?: PageAnnotations()
        pagesMap = pagesMap + (pageIndex to current.copy(stamps = current.stamps + stamp))
    }

    fun eraseNear(pageIndex: Int, normX: Float, normY: Float, radius: Float = 0.045f) {
        val current = pagesMap[pageIndex] ?: return
        val filteredStrokes = current.strokes.filterNot { stroke ->
            stroke.points.any { hypot(it.x - normX, it.y - normY) <= radius }
        }
        val filteredShapes = current.shapes.filterNot { shape ->
            val cx = (shape.start.x + shape.end.x) / 2f
            val cy = (shape.start.y + shape.end.y) / 2f
            hypot(cx - normX, cy - normY) <= radius * 1.6f ||
                hypot(shape.start.x - normX, shape.start.y - normY) <= radius ||
                hypot(shape.end.x - normX, shape.end.y - normY) <= radius
        }
        val filteredNotes = current.notes.filterNot { hypot(it.x - normX, it.y - normY) <= radius * 1.4f }
        val filteredBoxes = current.textBoxes.filterNot { hypot(it.x - normX, it.y - normY) <= radius * 1.6f }
        val filteredStamps = current.stamps.filterNot { hypot(it.x - normX, it.y - normY) <= radius * 1.8f }

        if (filteredStrokes.size != current.strokes.size ||
            filteredShapes.size != current.shapes.size ||
            filteredNotes.size != current.notes.size ||
            filteredBoxes.size != current.textBoxes.size ||
            filteredStamps.size != current.stamps.size
        ) {
            pushSnapshot()
            pagesMap = pagesMap + (
                pageIndex to current.copy(
                    strokes = filteredStrokes,
                    shapes = filteredShapes,
                    notes = filteredNotes,
                    textBoxes = filteredBoxes,
                    stamps = filteredStamps
                )
            )
        }
    }
}

@Composable
fun PageAnnotationCanvasOverlay(
    pageIndex: Int,
    annotations: PageAnnotations,
    activeTool: AnnotationTool,
    activeColorArgb: Long,
    activeWidthDp: Float,
    activeStamp: StudyStampType,
    onAddStroke: (PdfStrokePath) -> Unit,
    onAddShape: (PdfShapeItem) -> Unit,
    onTapAddNote: (x: Float, y: Float) -> Unit,
    onTapAddTextBox: (x: Float, y: Float) -> Unit,
    onTapNoteBubble: (PdfTextNote) -> Unit,
    onTapAddStamp: (x: Float, y: Float) -> Unit,
    onEraseAt: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var shapeCurrent by remember { mutableStateOf<NormalizedPoint?>(null) }

    val interactive = activeTool != AnnotationTool.NONE

    Box(modifier = modifier) {
        val gestureModifier = if (interactive) {
            Modifier
                .fillMaxSize()
                .pointerInput(activeTool, activeColorArgb, activeWidthDp, activeStamp) {
                    detectTapGestures { offset ->
                        val nx = (offset.x / max(1f, size.width.toFloat())).coerceIn(0f, 1f)
                        val ny = (offset.y / max(1f, size.height.toFloat())).coerceIn(0f, 1f)
                        when (activeTool) {
                            AnnotationTool.TEXT_NOTE -> onTapAddNote(nx, ny)
                            AnnotationTool.TEXT_BOX -> onTapAddTextBox(nx, ny)
                            AnnotationTool.STAMP -> onTapAddStamp(nx, ny)
                            AnnotationTool.ERASER -> onEraseAt(nx, ny)
                            else -> {}
                        }
                    }
                }
                .pointerInput(activeTool, activeColorArgb, activeWidthDp) {
                    if (activeTool in listOf(
                            AnnotationTool.PEN,
                            AnnotationTool.HIGHLIGHTER,
                            AnnotationTool.ERASER,
                            AnnotationTool.SHAPE_RECT,
                            AnnotationTool.SHAPE_OVAL,
                            AnnotationTool.SHAPE_ARROW,
                            AnnotationTool.REDACT
                        )
                    ) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val nx = (startOffset.x / max(1f, size.width.toFloat())).coerceIn(0f, 1f)
                                val ny = (startOffset.y / max(1f, size.height.toFloat())).coerceIn(0f, 1f)
                                when (activeTool) {
                                    AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                                        activePoints = listOf(NormalizedPoint(nx, ny))
                                    }
                                    AnnotationTool.SHAPE_RECT,
                                    AnnotationTool.SHAPE_OVAL,
                                    AnnotationTool.SHAPE_ARROW,
                                    AnnotationTool.REDACT -> {
                                        val pt = NormalizedPoint(nx, ny)
                                        shapeStart = pt
                                        shapeCurrent = pt
                                    }
                                    AnnotationTool.ERASER -> onEraseAt(nx, ny)
                                    else -> {}
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val nx = (change.position.x / max(1f, size.width.toFloat())).coerceIn(0f, 1f)
                                val ny = (change.position.y / max(1f, size.height.toFloat())).coerceIn(0f, 1f)
                                when (activeTool) {
                                    AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                                        activePoints = activePoints + NormalizedPoint(nx, ny)
                                    }
                                    AnnotationTool.SHAPE_RECT,
                                    AnnotationTool.SHAPE_OVAL,
                                    AnnotationTool.SHAPE_ARROW,
                                    AnnotationTool.REDACT -> {
                                        shapeCurrent = NormalizedPoint(nx, ny)
                                    }
                                    AnnotationTool.ERASER -> onEraseAt(nx, ny)
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                when (activeTool) {
                                    AnnotationTool.PEN, AnnotationTool.HIGHLIGHTER -> {
                                        if (activePoints.size >= 2) {
                                            onAddStroke(
                                                PdfStrokePath(
                                                    id = UUID.randomUUID().toString(),
                                                    points = activePoints,
                                                    colorArgb = activeColorArgb,
                                                    strokeWidthDp = if (activeTool == AnnotationTool.HIGHLIGHTER) activeWidthDp * 3.4f else activeWidthDp,
                                                    isHighlighter = activeTool == AnnotationTool.HIGHLIGHTER
                                                )
                                            )
                                        }
                                        activePoints = emptyList()
                                    }
                                    AnnotationTool.SHAPE_RECT,
                                    AnnotationTool.SHAPE_OVAL,
                                    AnnotationTool.SHAPE_ARROW,
                                    AnnotationTool.REDACT -> {
                                        val st = shapeStart
                                        val en = shapeCurrent
                                        if (st != null && en != null) {
                                            val kind = when (activeTool) {
                                                AnnotationTool.SHAPE_RECT -> AnnotationShapeKind.RECT
                                                AnnotationTool.SHAPE_OVAL -> AnnotationShapeKind.OVAL
                                                AnnotationTool.REDACT -> AnnotationShapeKind.REDACT
                                                else -> AnnotationShapeKind.ARROW
                                            }
                                            onAddShape(
                                                PdfShapeItem(
                                                    id = UUID.randomUUID().toString(),
                                                    kind = kind,
                                                    start = st,
                                                    end = en,
                                                    colorArgb = if (kind == AnnotationShapeKind.REDACT) 0xFF0F172AL else activeColorArgb,
                                                    strokeWidthDp = activeWidthDp
                                                )
                                            )
                                        }
                                        shapeStart = null
                                        shapeCurrent = null
                                    }
                                    else -> {}
                                }
                            },
                            onDragCancel = {
                                activePoints = emptyList()
                                shapeStart = null
                                shapeCurrent = null
                            }
                        )
                    }
                }
        } else {
            Modifier.fillMaxSize()
        }

        androidx.compose.foundation.Canvas(modifier = gestureModifier) {
            annotations.strokes.forEach { stroke ->
                drawStrokePath(stroke)
            }
            if (activePoints.size >= 2) {
                drawStrokePath(
                    PdfStrokePath(
                        id = "preview",
                        points = activePoints,
                        colorArgb = activeColorArgb,
                        strokeWidthDp = if (activeTool == AnnotationTool.HIGHLIGHTER) activeWidthDp * 3.4f else activeWidthDp,
                        isHighlighter = activeTool == AnnotationTool.HIGHLIGHTER
                    )
                )
            }
            annotations.shapes.forEach { shape ->
                drawShapeItem(shape)
            }
            val st = shapeStart
            val en = shapeCurrent
            if (st != null && en != null) {
                val kind = when (activeTool) {
                    AnnotationTool.SHAPE_RECT -> AnnotationShapeKind.RECT
                    AnnotationTool.SHAPE_OVAL -> AnnotationShapeKind.OVAL
                    AnnotationTool.REDACT -> AnnotationShapeKind.REDACT
                    else -> AnnotationShapeKind.ARROW
                }
                drawShapeItem(
                    PdfShapeItem(
                        id = "preview_shape",
                        kind = kind,
                        start = st,
                        end = en,
                        colorArgb = if (kind == AnnotationShapeKind.REDACT) 0xFF0F172AL else activeColorArgb,
                        strokeWidthDp = activeWidthDp
                    )
                )
            }
        }

        // الأختام والملاحظات اللاصقة ومربعات النص
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxWidth = constraints.maxWidth
            val maxHeight = constraints.maxHeight

            annotations.stamps.forEach { stamp ->
                val pxX = (stamp.x * maxWidth).roundToInt()
                val pxY = (stamp.y * maxHeight).roundToInt()
                Surface(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pxX - 65).coerceIn(0, max(0, maxWidth - 150)),
                                (pxY - 24).coerceIn(0, max(0, maxHeight - 52))
                            )
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(stamp.stampType.badgeColor).copy(alpha = 0.14f),
                    border = BorderStroke(1.8.dp, Color(stamp.stampType.badgeColor))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = stamp.stampType.textAr,
                            tint = Color(stamp.stampType.badgeColor),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stamp.stampType.textAr,
                            style = PdfTypography.uiLabel(11.sp, FontWeight.Bold),
                            color = Color(stamp.stampType.badgeColor)
                        )
                    }
                }
            }

            annotations.textBoxes.forEach { boxItem ->
                val pxX = (boxItem.x * maxWidth).roundToInt()
                val pxY = (boxItem.y * maxHeight).roundToInt()
                Text(
                    text = boxItem.text,
                    style = PdfTypography.uiTitle(13.sp, FontWeight.Bold),
                    color = Color(boxItem.colorArgb),
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pxX - 10).coerceIn(0, max(0, maxWidth - 160)),
                                (pxY - 12).coerceIn(0, max(0, maxHeight - 40))
                            )
                        }
                        .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            annotations.notes.forEach { note ->
                val pxX = (note.x * maxWidth).roundToInt()
                val pxY = (note.y * maxHeight).roundToInt()
                Surface(
                    onClick = { onTapNoteBubble(note) },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pxX - 20).coerceIn(0, max(0, maxWidth - 180)),
                                (pxY - 20).coerceIn(0, max(0, maxHeight - 60))
                            )
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF08A),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = "ملاحظة",
                            tint = Color(0xFF854D0E),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = note.text.take(26) + if (note.text.length > 26) "..." else "",
                            style = PdfTypography.uiLabel(11.sp, FontWeight.SemiBold),
                            color = Color(0xFF713F12),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStrokePath(stroke: PdfStrokePath) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * size.width, first.y * size.height)
        for (i in 1 until stroke.points.size) {
            val pt = stroke.points[i]
            lineTo(pt.x * size.width, pt.y * size.height)
        }
    }
    val baseColor = Color(stroke.colorArgb)
    drawPath(
        path = path,
        color = if (stroke.isHighlighter) baseColor.copy(alpha = 0.36f) else baseColor,
        style = Stroke(
            width = stroke.strokeWidthDp.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawShapeItem(shape: PdfShapeItem) {
    val sx = shape.start.x * size.width
    val sy = shape.start.y * size.height
    val ex = shape.end.x * size.width
    val ey = shape.end.y * size.height
    val color = Color(shape.colorArgb)
    val strokePx = shape.strokeWidthDp.dp.toPx()

    when (shape.kind) {
        AnnotationShapeKind.RECT -> {
            drawRect(
                color = color,
                topLeft = Offset(min(sx, ex), min(sy, ey)),
                size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                style = Stroke(width = strokePx)
            )
        }
        AnnotationShapeKind.REDACT -> {
            drawRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(min(sx, ex), min(sy, ey)),
                size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy))
            )
        }
        AnnotationShapeKind.OVAL -> {
            drawOval(
                color = color,
                topLeft = Offset(min(sx, ex), min(sy, ey)),
                size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                style = Stroke(width = strokePx)
            )
        }
        AnnotationShapeKind.ARROW -> {
            drawLine(
                color = color,
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )
            val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
            val arrowLen = max(16f, strokePx * 4f)
            val wing1 = Offset(
                (ex - arrowLen * cos(angle - Math.PI / 6)).toFloat(),
                (ey - arrowLen * sin(angle - Math.PI / 6)).toFloat()
            )
            val wing2 = Offset(
                (ex - arrowLen * cos(angle + Math.PI / 6)).toFloat(),
                (ey - arrowLen * sin(angle + Math.PI / 6)).toFloat()
            )
            drawLine(color = color, start = Offset(ex, ey), end = wing1, strokeWidth = strokePx, cap = StrokeCap.Round)
            drawLine(color = color, start = Offset(ex, ey), end = wing2, strokeWidth = strokePx, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun FloatingAnnotationToolbar(
    activeTool: AnnotationTool,
    activeColorArgb: Long,
    activeWidthDp: Float,
    activeStamp: StudyStampType,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTool: (AnnotationTool) -> Unit,
    onSelectColor: (Long) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onSelectStamp: (StudyStampType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCloseToolbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = PdfStudioColors.cardSurface().copy(alpha = 0.98f),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, PdfStudioColors.cardBorder())
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolChipButton(
                    icon = Icons.Default.AutoFixHigh,
                    label = "تظليل",
                    selected = activeTool == AnnotationTool.HIGHLIGHTER,
                    onClick = { onSelectTool(AnnotationTool.HIGHLIGHTER) },
                    tag = "annot_tool_highlighter"
                )
                ToolChipButton(
                    icon = Icons.Default.NoteAdd,
                    label = "ملاحظة",
                    selected = activeTool == AnnotationTool.TEXT_NOTE,
                    onClick = { onSelectTool(AnnotationTool.TEXT_NOTE) },
                    tag = "annot_tool_note"
                )
                ToolChipButton(
                    icon = Icons.Default.TextFields,
                    label = "نص T",
                    selected = activeTool == AnnotationTool.TEXT_BOX,
                    onClick = { onSelectTool(AnnotationTool.TEXT_BOX) },
                    tag = "annot_tool_textbox"
                )
                ToolChipButton(
                    icon = Icons.Default.Draw,
                    label = "قلم",
                    selected = activeTool == AnnotationTool.PEN,
                    onClick = { onSelectTool(AnnotationTool.PEN) },
                    tag = "annot_tool_pen"
                )
                ToolChipButton(
                    icon = Icons.Default.Brush,
                    label = "ممحاة",
                    selected = activeTool == AnnotationTool.ERASER,
                    onClick = { onSelectTool(AnnotationTool.ERASER) },
                    tag = "annot_tool_eraser"
                )
                ToolChipButton(
                    icon = Icons.Default.CropSquare,
                    label = "مربع",
                    selected = activeTool == AnnotationTool.SHAPE_RECT,
                    onClick = { onSelectTool(AnnotationTool.SHAPE_RECT) },
                    tag = "annot_tool_rect"
                )
                ToolChipButton(
                    icon = Icons.Default.Circle,
                    label = "دائرة",
                    selected = activeTool == AnnotationTool.SHAPE_OVAL,
                    onClick = { onSelectTool(AnnotationTool.SHAPE_OVAL) },
                    tag = "annot_tool_oval"
                )
                ToolChipButton(
                    icon = Icons.Default.ArrowForward,
                    label = "سهم",
                    selected = activeTool == AnnotationTool.SHAPE_ARROW,
                    onClick = { onSelectTool(AnnotationTool.SHAPE_ARROW) },
                    tag = "annot_tool_arrow"
                )
                ToolChipButton(
                    icon = Icons.Default.Verified,
                    label = "ختم",
                    selected = activeTool == AnnotationTool.STAMP,
                    onClick = { onSelectTool(AnnotationTool.STAMP) },
                    tag = "annot_tool_stamp"
                )
                ToolChipButton(
                    icon = Icons.Default.Block,
                    label = "طمس",
                    selected = activeTool == AnnotationTool.REDACT,
                    onClick = { onSelectTool(AnnotationTool.REDACT) },
                    tag = "annot_tool_redact"
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("annot_undo_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("annot_redo_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "إعادة")
                }
                IconButton(
                    onClick = onCloseToolbar,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("annot_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق التحرير")
                }
            }

            AnimatedVisibility(visible = activeTool == AnnotationTool.STAMP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudyStampType.entries.forEach { stamp ->
                        val isSel = stamp == activeStamp
                        Surface(
                            onClick = { onSelectStamp(stamp) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) Color(stamp.badgeColor).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (isSel) 1.5.dp else 1.dp,
                                color = if (isSel) Color(stamp.badgeColor) else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Text(
                                text = stamp.textAr,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = PdfTypography.uiLabel(12.sp),
                                color = Color(stamp.badgeColor)
                            )
                        }
                    }
                }
            }

            // ألوان التظليل الأربعة المخصصة أو ألوان القلم
            AnimatedVisibility(visible = activeTool == AnnotationTool.HIGHLIGHTER) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("لون التظليل:", style = PdfTypography.uiLabel(11.sp), color = PdfStudioColors.subtitleText())
                    HIGHLIGHTER_4_COLORS.forEach { (argb, label) ->
                        val selected = argb == activeColorArgb
                        Surface(
                            onClick = { onSelectColor(argb) },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(argb).copy(alpha = if (selected) 0.35f else 0.18f),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) PdfStudioColors.RoyalBlue else Color(argb)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(argb)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, style = PdfTypography.uiLabel(11.sp), color = PdfStudioColors.titleText())
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = activeTool in listOf(
                    AnnotationTool.PEN,
                    AnnotationTool.SHAPE_RECT,
                    AnnotationTool.SHAPE_OVAL,
                    AnnotationTool.SHAPE_ARROW,
                    AnnotationTool.TEXT_BOX
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ANNOTATION_INK_COLORS.forEach { argb ->
                            val selected = argb == activeColorArgb
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) PdfStudioColors.RoyalBlue else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectColor(argb) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ANNOTATION_STROKE_WIDTHS.forEachIndexed { idx, w ->
                            val selected = w == activeWidthDp
                            Surface(
                                onClick = { onSelectWidth(w) },
                                shape = CircleShape,
                                color = if (selected) PdfStudioColors.RoyalBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = listOf("رفيع", "وسط", "عريض")[idx],
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    style = PdfTypography.uiLabel(11.sp),
                                    color = if (selected) PdfStudioColors.RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChipButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PdfStudioColors.RoyalBlue.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = if (selected) BorderStroke(1.2.dp, PdfStudioColors.RoyalBlue) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) PdfStudioColors.RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = PdfTypography.uiLabel(12.sp),
                color = if (selected) PdfStudioColors.RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TextNoteEditorDialog(
    title: String = "إضافة ملاحظة لاصقة",
    placeholder: String = "اكتب شرح المعلم أو ملاحظات التلخيص...",
    initialText: String,
    isEditingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var noteText by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditingExisting) "تعديل الملاحظة" else title,
                style = PdfTypography.uiTitle(16.sp)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text(placeholder, style = PdfTypography.uiBody()) },
                    modifier = Modifier.fillMaxWidth().testTag("annot_note_input"),
                    minLines = 2,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (noteText.isNotBlank()) onConfirm(noteText.trim()) },
                enabled = noteText.isNotBlank(),
                modifier = Modifier.testTag("annot_note_save_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حفظ", style = PdfTypography.uiLabel())
            }
        },
        dismissButton = {
            Row {
                if (isEditingExisting && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("حذف", color = MaterialTheme.colorScheme.error, style = PdfTypography.uiLabel())
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("إلغاء", style = PdfTypography.uiLabel())
                }
            }
        }
    )
}

/**
 * محرك التصدير الفعلي للشروحات عبر PdfBox-Android:
 * يرسم التظليل، الأقلام، الأشكال، مربعات الطمس الحساسة (Redact)، مربعات النص T،
 * الملاحظات اللاصقة، والأختام العربية عبر StaticLayout ثم يدمجها داخل صفحات PDF.
 */
object PdfAnnotationsExporter {
    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceFile: File,
        password: String?,
        annotationsByPage: Map<Int, PageAnnotations>,
        flatten: Boolean,
        outFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        PdfEngineHelper.ensurePdfBoxInit(context)
        try {
            val doc = if (password.isNullOrEmpty()) PDDocument.load(sourceFile) else PDDocument.load(sourceFile, password)
            val typeface = PdfEngineHelper.getArabicUiTypeface(context)
            doc.use { pdDoc ->
                if (flatten) {
                    pdDoc.isAllSecurityToBeRemoved = true
                }
                val totalPages = pdDoc.numberOfPages
                for (pageIndex in 0 until totalPages) {
                    val page = pdDoc.getPage(pageIndex)
                    val pageAnn = annotationsByPage[pageIndex]
                    if (pageAnn != null && !pageAnn.isEmpty()) {
                        val box = page.cropBox ?: page.mediaBox
                        val pageW = box.width
                        val pageH = box.height
                        val bmpW = 1240
                        val bmpH = max(400, ((pageH / max(1f, pageW)) * bmpW).roundToInt()).coerceAtMost(2400)
                        val overlay = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        val canvas = AndroidCanvas(overlay)

                        // المسارات والتظليل
                        pageAnn.strokes.forEach { stroke ->
                            if (stroke.points.size >= 2) {
                                val baseInt = stroke.colorArgb.toInt()
                                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = baseInt
                                    if (stroke.isHighlighter) alpha = 95
                                    style = AndroidPaint.Style.STROKE
                                    strokeWidth = stroke.strokeWidthDp * 3.2f
                                    strokeCap = AndroidPaint.Cap.ROUND
                                    strokeJoin = AndroidPaint.Join.ROUND
                                }
                                val path = AndroidPath().apply {
                                    moveTo(stroke.points.first().x * bmpW, stroke.points.first().y * bmpH)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x * bmpW, stroke.points[i].y * bmpH)
                                    }
                                }
                                canvas.drawPath(path, paint)
                            }
                        }

                        // الأشكال ومربعات الطمس
                        pageAnn.shapes.forEach { shape ->
                            val sx = shape.start.x * bmpW
                            val sy = shape.start.y * bmpH
                            val ex = shape.end.x * bmpW
                            val ey = shape.end.y * bmpH
                            if (shape.kind == AnnotationShapeKind.REDACT) {
                                val fillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.parseColor("#0F172A")
                                    style = AndroidPaint.Style.FILL
                                }
                                canvas.drawRect(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), fillPaint)
                            } else {
                                val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = shape.colorArgb.toInt()
                                    style = AndroidPaint.Style.STROKE
                                    strokeWidth = shape.strokeWidthDp * 3.2f
                                    strokeCap = AndroidPaint.Cap.ROUND
                                }
                                when (shape.kind) {
                                    AnnotationShapeKind.RECT -> canvas.drawRect(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), paint)
                                    AnnotationShapeKind.OVAL -> canvas.drawOval(AndroidRectF(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey)), paint)
                                    AnnotationShapeKind.ARROW -> {
                                        canvas.drawLine(sx, sy, ex, ey, paint)
                                        val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                                        val len = max(28f, paint.strokeWidth * 3.5f)
                                        canvas.drawLine(
                                            ex,
                                            ey,
                                            (ex - len * cos(angle - Math.PI / 6)).toFloat(),
                                            (ey - len * sin(angle - Math.PI / 6)).toFloat(),
                                            paint
                                        )
                                        canvas.drawLine(
                                            ex,
                                            ey,
                                            (ex - len * cos(angle + Math.PI / 6)).toFloat(),
                                            (ey - len * sin(angle + Math.PI / 6)).toFloat(),
                                            paint
                                        )
                                    }
                                    AnnotationShapeKind.REDACT -> {}
                                }
                            }
                        }

                        // مربعات النص المدرجة مباشرة (T)
                        pageAnn.textBoxes.forEach { boxItem ->
                            val bx = (boxItem.x * bmpW).coerceIn(16f, bmpW - 220f)
                            val by = (boxItem.y * bmpH).coerceIn(16f, bmpH - 80f)
                            val textPaint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = boxItem.colorArgb.toInt()
                                textSize = 30f
                                this.typeface = typeface
                            }
                            val layoutW = 280
                            val staticLayout = StaticLayout.Builder
                                .obtain(boxItem.text, 0, boxItem.text.length, textPaint, layoutW)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .build()
                            canvas.save()
                            canvas.translate(bx, by)
                            staticLayout.draw(canvas)
                            canvas.restore()
                        }

                        // الأختام الدراسية العربية
                        pageAnn.stamps.forEach { stamp ->
                            val sx = stamp.x * bmpW
                            val sy = stamp.y * bmpH
                            val textPaint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = stamp.stampType.badgeColor.toInt()
                                textSize = 30f
                                this.typeface = typeface
                            }
                            val tw = textPaint.measureText(stamp.stampType.textAr)
                            val padX = 22f
                            val padY = 14f
                            val rect = AndroidRectF(sx - tw / 2f - padX, sy - 22f - padY, sx + tw / 2f + padX, sy + 14f + padY)
                            val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = stamp.stampType.badgeColor.toInt()
                                alpha = 35
                                style = AndroidPaint.Style.FILL
                            }
                            val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = stamp.stampType.badgeColor.toInt()
                                strokeWidth = 4f
                                style = AndroidPaint.Style.STROKE
                            }
                            canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
                            canvas.drawRoundRect(rect, 18f, 18f, borderPaint)
                            canvas.drawText(stamp.stampType.textAr, rect.left + padX, sy + 6f, textPaint)
                        }

                        // الملاحظات اللاصقة الصفراء
                        pageAnn.notes.forEach { note ->
                            val nx = (note.x * bmpW).coerceIn(20f, bmpW - 280f)
                            val ny = (note.y * bmpH).coerceIn(20f, bmpH - 120f)
                            val noteTextPaint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#713F12")
                                textSize = 24f
                                this.typeface = typeface
                            }
                            val layoutW = 240
                            val staticLayout = StaticLayout.Builder
                                .obtain(note.text, 0, note.text.length, noteTextPaint, layoutW)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .build()
                            val boxRect = AndroidRectF(
                                nx,
                                ny,
                                nx + layoutW + 24f,
                                ny + staticLayout.height + 24f
                            )
                            val noteBg = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.parseColor("#FEF08A")
                                alpha = 242
                                style = AndroidPaint.Style.FILL
                            }
                            canvas.drawRoundRect(boxRect, 16f, 16f, noteBg)
                            canvas.save()
                            canvas.translate(nx + 12f, ny + 12f)
                            staticLayout.draw(canvas)
                            canvas.restore()
                        }

                        val pdImg = LosslessFactory.createFromImage(pdDoc, overlay)
                        PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.drawImage(pdImg, box.lowerLeftX, box.lowerLeftY, pageW, pageH)
                        }
                        overlay.recycle()
                    }

                    if (flatten && page.annotations.isNotEmpty()) {
                        page.annotations.clear()
                    }
                }
                pdDoc.save(outFile)
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
