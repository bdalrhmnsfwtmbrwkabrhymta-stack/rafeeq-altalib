package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.pdf.PenType
import com.example.ui.MainViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class WhiteboardTool {
    PEN, HIGHLIGHTER, ERASER, TEXT, SHAPE
}

enum class CanvasBackground {
    WHITE, GRID, RULED, BLACKBOARD
}

data class DrawingPoint(val offset: Offset)

data class DrawingPathData(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val isEraser: Boolean = false,
    val penType: PenType = PenType.BALLPOINT
)

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val position: Offset,
    val color: Color,
    val fontSize: Float = 18f
)

enum class WhiteboardActionType {
    DRAW_PATH, ERASE_STROKES
}

data class WhiteboardHistoryAction(
    val type: WhiteboardActionType,
    val pathsAdded: List<DrawingPathData> = emptyList(),
    val pathsErased: List<Pair<Int, DrawingPathData>> = emptyList()
)

data class WhiteboardPage(
    val pageNumber: Int,
    val paths: MutableList<DrawingPathData> = mutableListOf(),
    val undonePaths: MutableList<DrawingPathData> = mutableListOf(),
    val actionUndoStack: MutableList<WhiteboardHistoryAction> = mutableListOf(),
    val actionRedoStack: MutableList<WhiteboardHistoryAction> = mutableListOf(),
    val textAnnotations: MutableList<TextAnnotation> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Whiteboard Pages
    var pages by remember {
        mutableStateOf(listOf(WhiteboardPage(pageNumber = 1)))
    }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    // Active tool state
    var selectedTool by remember { mutableStateOf(WhiteboardTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFF1E293B)) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    var canvasBackground by remember { mutableStateOf(CanvasBackground.WHITE) }

    // Current drawing path in progress
    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var eraserIndicatorPos by remember { mutableStateOf<Offset?>(null) }
    val currentlyErasedInDrag = remember { mutableStateListOf<Pair<Int, DrawingPathData>>() }

    // Dialogs
    var showClearDialog by remember { mutableStateOf(false) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(Offset(200f, 300f)) }

    val currentPage = pages.getOrNull(currentPageIndex) ?: pages.first()

    val canUndo = currentPage.actionUndoStack.isNotEmpty() || currentPage.paths.isNotEmpty()
    val canRedo = currentPage.actionRedoStack.isNotEmpty() || currentPage.undonePaths.isNotEmpty()

    fun undoAction() {
        if (currentPage.actionUndoStack.isNotEmpty()) {
            val action = currentPage.actionUndoStack.removeAt(currentPage.actionUndoStack.size - 1)
            when (action.type) {
                WhiteboardActionType.DRAW_PATH -> {
                    action.pathsAdded.forEach { p -> currentPage.paths.remove(p) }
                }
                WhiteboardActionType.ERASE_STROKES -> {
                    action.pathsErased.forEach { (idx, p) ->
                        if (idx in 0..currentPage.paths.size) {
                            currentPage.paths.add(idx, p)
                        } else {
                            currentPage.paths.add(p)
                        }
                    }
                }
            }
            currentPage.actionRedoStack.add(action)
            pages = pages.toList()
        } else if (currentPage.paths.isNotEmpty()) {
            val removed = currentPage.paths.removeAt(currentPage.paths.size - 1)
            currentPage.undonePaths.add(removed)
            pages = pages.toList()
        }
    }

    fun redoAction() {
        if (currentPage.actionRedoStack.isNotEmpty()) {
            val action = currentPage.actionRedoStack.removeAt(currentPage.actionRedoStack.size - 1)
            when (action.type) {
                WhiteboardActionType.DRAW_PATH -> {
                    action.pathsAdded.forEach { p -> currentPage.paths.add(p) }
                }
                WhiteboardActionType.ERASE_STROKES -> {
                    action.pathsErased.forEach { (_, p) -> currentPage.paths.remove(p) }
                }
            }
            currentPage.actionUndoStack.add(action)
            pages = pages.toList()
        } else if (currentPage.undonePaths.isNotEmpty()) {
            val restored = currentPage.undonePaths.removeAt(currentPage.undonePaths.size - 1)
            currentPage.paths.add(restored)
            pages = pages.toList()
        }
    }

    val colorsList = remember {
        listOf(
            Color(0xFF0F172A), // Black / Slate
            Color(0xFF2563EB), // Blue
            Color(0xFFDC2626), // Red
            Color(0xFF16A34A), // Green
            Color(0xFFF59E0B), // Amber / Yellow
            Color(0xFF9333EA), // Purple
            Color(0xFFEA580C), // Orange
            Color(0xFF06B6D4)  // Cyan
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Previous page button
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0) currentPageIndex--
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الصفحة السابقة")
                        }

                        // Page indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "صفحة ${currentPageIndex + 1} / ${pages.size}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Next page or Add page button
                        IconButton(
                            onClick = {
                                if (currentPageIndex < pages.size - 1) {
                                    currentPageIndex++
                                } else {
                                    val newPage = WhiteboardPage(pageNumber = pages.size + 1)
                                    pages = pages + newPage
                                    currentPageIndex = pages.size - 1
                                    Toast.makeText(context, "تمت إضافة صفحة جديدة 📝", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                if (currentPageIndex < pages.size - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Add,
                                contentDescription = "الصفحة التالية"
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Menu, contentDescription = "القائمة")
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { undoAction() },
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "تراجع للخلف ↩️",
                            tint = if (canUndo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = { redoAction() },
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "إعادة للأمام ↪️",
                            tint = if (canRedo) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f)
                        )
                    }

                    // Clear Canvas
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "مسح الكل", tint = MaterialTheme.colorScheme.error)
                    }

                    // Save / Export
                    IconButton(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            viewModel.insertDocument(
                                title = "سبورة ذكية - $timeStamp",
                                uriString = "",
                                fileType = "WHITEBOARD",
                                extractedText = "ملاحظات سبورة صفحة ${currentPageIndex + 1} مع ${currentPage.paths.size} رسم و ${currentPage.textAnnotations.size} نص"
                            )
                            Toast.makeText(context, "تم حفظ وتثبيت السبورة في المستندات بنجاح 💾✅", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "حفظ السبورة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Interactive Drawing Controls Toolbar (Matches Image 17)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                // 1. Tool Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolButton(
                        icon = Icons.Default.Edit,
                        label = "رسم",
                        isSelected = selectedTool == WhiteboardTool.PEN,
                        onClick = { selectedTool = WhiteboardTool.PEN }
                    )

                    ToolButton(
                        icon = Icons.Default.Brush,
                        label = "تظليل",
                        isSelected = selectedTool == WhiteboardTool.HIGHLIGHTER,
                        onClick = { selectedTool = WhiteboardTool.HIGHLIGHTER }
                    )

                    ToolButton(
                        icon = Icons.Default.AutoFixNormal,
                        label = "ممحاة",
                        isSelected = selectedTool == WhiteboardTool.ERASER,
                        onClick = { selectedTool = WhiteboardTool.ERASER }
                    )

                    ToolButton(
                        icon = Icons.Default.TextFields,
                        label = "نص",
                        isSelected = selectedTool == WhiteboardTool.TEXT,
                        onClick = {
                            selectedTool = WhiteboardTool.TEXT
                            showAddTextDialog = true
                        }
                    )

                    ToolButton(
                        icon = Icons.Default.GridOn,
                        label = "الخلفية",
                        isSelected = false,
                        onClick = {
                            canvasBackground = when (canvasBackground) {
                                CanvasBackground.WHITE -> CanvasBackground.GRID
                                CanvasBackground.GRID -> CanvasBackground.RULED
                                CanvasBackground.RULED -> CanvasBackground.BLACKBOARD
                                CanvasBackground.BLACKBOARD -> CanvasBackground.WHITE
                            }
                            val bgName = when (canvasBackground) {
                                CanvasBackground.WHITE -> "بيضاء"
                                CanvasBackground.GRID -> "مربعات كشكول"
                                CanvasBackground.RULED -> "أسطر مسطرة"
                                CanvasBackground.BLACKBOARD -> "سبورة سوداء"
                            }
                            Toast.makeText(context, "الخلفية: $bgName", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Color Palette and Stroke Sizes Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Color Dots
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(colorsList) { color ->
                            val isColorSelected = selectedColor == color && selectedTool != WhiteboardTool.ERASER
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isColorSelected) 3.dp else 1.dp,
                                        color = if (isColorSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColor = color
                                        if (selectedTool == WhiteboardTool.ERASER) selectedTool = WhiteboardTool.PEN
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Stroke Width Presets (Fine, Medium, Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val strokeSizes = listOf(4f to "رفيع", 10f to "متوسط", 22f to "عريض")
                        strokeSizes.forEach { (size, label) ->
                            val isSelectedSize = strokeWidth == size
                            FilterChip(
                                selected = isSelectedSize,
                                onClick = { strokeWidth = size },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    when (canvasBackground) {
                        CanvasBackground.WHITE -> Color(0xFFFCFCFD)
                        CanvasBackground.GRID -> Color(0xFFF8FAFC)
                        CanvasBackground.RULED -> Color(0xFFFDFBF7)
                        CanvasBackground.BLACKBOARD -> Color(0xFF1E293B)
                    }
                )
                .pointerInput(selectedTool, selectedColor, strokeWidth, currentPageIndex) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (selectedTool == WhiteboardTool.ERASER) {
                                eraserIndicatorPos = offset
                                currentlyErasedInDrag.clear()
                                val eraseRadius = (strokeWidth * 4f).coerceIn(25f, 85f)
                                eraseWhiteboardStrokesAt(currentPage, offset, eraseRadius, currentlyErasedInDrag)
                                pages = pages.toList()
                            } else {
                                currentPage.undonePaths.clear()
                                currentPathPoints = listOf(offset)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (selectedTool == WhiteboardTool.ERASER) {
                                eraserIndicatorPos = change.position
                                val eraseRadius = (strokeWidth * 4f).coerceIn(25f, 85f)
                                eraseWhiteboardStrokesAt(currentPage, change.position, eraseRadius, currentlyErasedInDrag)
                                pages = pages.toList()
                            } else {
                                currentPathPoints = currentPathPoints + change.position
                            }
                        },
                        onDragEnd = {
                            if (selectedTool == WhiteboardTool.ERASER) {
                                eraserIndicatorPos = null
                                if (currentlyErasedInDrag.isNotEmpty()) {
                                    currentPage.actionUndoStack.add(
                                        WhiteboardHistoryAction(
                                            type = WhiteboardActionType.ERASE_STROKES,
                                            pathsErased = currentlyErasedInDrag.toList()
                                        )
                                    )
                                    currentPage.actionRedoStack.clear()
                                    currentlyErasedInDrag.clear()
                                }
                                pages = pages.toList()
                            } else {
                                if (currentPathPoints.isNotEmpty()) {
                                    val newPath = DrawingPathData(
                                        points = currentPathPoints,
                                        color = selectedColor,
                                        strokeWidth = if (selectedTool == WhiteboardTool.HIGHLIGHTER) strokeWidth * 3f else strokeWidth,
                                        isHighlighter = selectedTool == WhiteboardTool.HIGHLIGHTER,
                                        isEraser = false
                                    )
                                    currentPage.paths.add(newPath)
                                    currentPage.actionUndoStack.add(
                                        WhiteboardHistoryAction(
                                            type = WhiteboardActionType.DRAW_PATH,
                                            pathsAdded = listOf(newPath)
                                        )
                                    )
                                    currentPage.actionRedoStack.clear()
                                    currentPathPoints = emptyList()
                                    pages = pages.toList() // trigger recomposition
                                }
                            }
                        },
                        onDragCancel = {
                            eraserIndicatorPos = null
                            currentlyErasedInDrag.clear()
                            currentPathPoints = emptyList()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        if (selectedTool == WhiteboardTool.TEXT) {
                            textPosition = tapOffset
                            showAddTextDialog = true
                        }
                    }
                }
        ) {
            // Main Drawing Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 1. Draw Background Grid/Lines if active
                when (canvasBackground) {
                    CanvasBackground.GRID -> drawNotebookGrid(this)
                    CanvasBackground.RULED -> drawNotebookRuledLines(this)
                    else -> {}
                }

                // 2. Render Existing Paths on current page
                currentPage.paths.forEach { pathData ->
                    if (!pathData.isEraser) {
                        drawSmoothPath(pathData)
                    }
                }

                // 3. Render In-progress Path
                if (currentPathPoints.isNotEmpty() && selectedTool != WhiteboardTool.ERASER) {
                    val inProgressPath = DrawingPathData(
                        points = currentPathPoints,
                        color = selectedColor,
                        strokeWidth = if (selectedTool == WhiteboardTool.HIGHLIGHTER) strokeWidth * 3f else strokeWidth,
                        isHighlighter = selectedTool == WhiteboardTool.HIGHLIGHTER,
                        isEraser = false
                    )
                    drawSmoothPath(inProgressPath)
                }

                // 4. Render Eraser Visual Indicator
                if (selectedTool == WhiteboardTool.ERASER) {
                    eraserIndicatorPos?.let { pos ->
                        val eraseRadius = (strokeWidth * 4f).coerceIn(25f, 85f)
                        drawCircle(
                            color = Color(0x22EF4444),
                            radius = eraseRadius,
                            center = pos
                        )
                        drawCircle(
                            color = Color(0xFFEF4444),
                            radius = eraseRadius,
                            center = pos,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                            )
                        )
                        drawCircle(
                            color = Color(0xFFEF4444),
                            radius = 3.dp.toPx(),
                            center = pos
                        )
                    }
                }
            }

            // 4. Render Text Annotations on Canvas
            currentPage.textAnnotations.forEach { annotation ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (annotation.position.x / 2.75f).dp,
                            y = (annotation.position.y / 2.75f).dp
                        )
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = annotation.text,
                        color = annotation.color,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Sample Helper Math Equation Watermark if empty
            if (currentPage.paths.isEmpty() && currentPathPoints.isEmpty() && currentPage.textAnnotations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "السبورة الذكية جاهزة للرسم وحل المسائل 📐",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اختر القلم أو التظليل وابدأ في تدوين المعادلات والملاحظات بحرية",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Clear Board Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح محتوى السبورة 🗑️", fontWeight = FontWeight.Bold) },
            text = { Text("هل تريد مسح كل الرسومات والملاحظات في الصفحة الحالية؟") },
            confirmButton = {
                Button(
                    onClick = {
                        currentPage.paths.clear()
                        currentPage.undonePaths.clear()
                        currentPage.textAnnotations.clear()
                        pages = pages.toList()
                        showClearDialog = false
                        Toast.makeText(context, "تم مسح الصفحة ✅", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("مسح الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Add Text Dialog
    if (showAddTextDialog) {
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text("إضافة نص على السبورة ✍️", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("اكتب النص أو المعادلة...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            currentPage.textAnnotations.add(
                                TextAnnotation(
                                    text = textInput,
                                    position = textPosition,
                                    color = selectedColor
                                )
                            )
                            pages = pages.toList()
                            textInput = ""
                        }
                        showAddTextDialog = false
                    }
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.drawNotebookGrid(drawScope: DrawScope) {
    val gridSize = 40f
    val gridColor = Color(0xFFCBD5E1).copy(alpha = 0.4f)
    var x = 0f
    while (x < drawScope.size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, drawScope.size.height),
            strokeWidth = 1f
        )
        x += gridSize
    }
    var y = 0f
    while (y < drawScope.size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(drawScope.size.width, y),
            strokeWidth = 1f
        )
        y += gridSize
    }
}

private fun DrawScope.drawNotebookRuledLines(drawScope: DrawScope) {
    val lineSpacing = 50f
    val lineColor = Color(0xFF93C5FD).copy(alpha = 0.5f)
    var y = 80f
    while (y < drawScope.size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(drawScope.size.width, y),
            strokeWidth = 1.2f
        )
        y += lineSpacing
    }
}

private fun DrawScope.drawSmoothPath(pathData: DrawingPathData) {
    val points = pathData.points
    if (points.isEmpty()) return

    val path = Path()
    if (points.size == 1) {
        drawCircle(
            color = if (pathData.isHighlighter) pathData.color.copy(alpha = 0.4f) else pathData.color,
            radius = pathData.strokeWidth / 2f,
            center = points[0]
        )
        return
    }

    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val current = points[i]
        val midX = (prev.x + current.x) / 2f
        val midY = (prev.y + current.y) / 2f
        path.quadraticTo(prev.x, prev.y, midX, midY)
    }

    drawPath(
        path = path,
        color = if (pathData.isHighlighter) pathData.color.copy(alpha = 0.4f) else pathData.color,
        style = Stroke(
            width = pathData.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun eraseWhiteboardStrokesAt(
    page: WhiteboardPage,
    touch: Offset,
    radius: Float,
    erasedCollector: MutableList<Pair<Int, DrawingPathData>>
) {
    val toRemove = mutableListOf<Pair<Int, DrawingPathData>>()
    page.paths.forEachIndexed { index, pathData ->
        if (!pathData.isEraser && isWhiteboardStrokeNearPoint(pathData, touch, radius)) {
            toRemove.add(index to pathData)
        }
    }
    for (i in toRemove.indices.reversed()) {
        val pair = toRemove[i]
        page.paths.remove(pair.second)
        erasedCollector.add(pair)
    }
}

private fun isWhiteboardStrokeNearPoint(pathData: DrawingPathData, touch: Offset, radius: Float): Boolean {
    val totalRadius = radius + pathData.strokeWidth / 2f
    val rSq = totalRadius * totalRadius
    val points = pathData.points
    if (points.isEmpty()) return false
    if (points.size == 1) {
        val dx = points[0].x - touch.x
        val dy = points[0].y - touch.y
        return (dx * dx + dy * dy) <= rSq
    }
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val distSq = whiteboardDistanceSqToSegment(touch, p1, p2)
        if (distSq <= rSq) return true
    }
    return false
}

private fun whiteboardDistanceSqToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l2 = dx * dx + dy * dy
    if (l2 == 0f) {
        val dpx = p.x - a.x
        val dpy = p.y - a.y
        return dpx * dpx + dpy * dpy
    }
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    val diffX = p.x - projX
    val diffY = p.y - projY
    return diffX * diffX + diffY * diffY
}
