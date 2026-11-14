package com.example.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.IdeaVaultEntity
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

val IDEA_CATEGORIES = listOf("الكل", "مشروع", "بحث", "فكرة دراسية", "ابتكار", "حل مسألة", "كتابة", "عام")
val IDEA_PRIORITIES = listOf("عاجلة", "عالية", "متوسطة", "منخفضة")
val IDEA_STATUSES = listOf("جديدة", "قيد التنفيذ", "مكتملة", "مؤجلة")
val IDEA_COLORS = listOf(
    "#3B82F6", // Blue
    "#10B981", // Emerald
    "#F59E0B", // Amber
    "#EC4899", // Pink
    "#8B5CF6", // Purple
    "#06B6D4", // Cyan
    "#EF4444"  // Rose
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaVaultScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTasks: () -> Unit = {}
) {
    val context = LocalContext.current
    val ideas by viewModel.ideas.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }
    var selectedStatusFilter by remember { mutableStateOf("الكل") }
    var onlyFavorites by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingIdea by remember { mutableStateOf<IdeaVaultEntity?>(null) }
    var ideaToDelete by remember { mutableStateOf<IdeaVaultEntity?>(null) }
    var ideaToConvert by remember { mutableStateOf<IdeaVaultEntity?>(null) }

    // Filter list
    val filteredIdeas = remember(ideas, searchQuery, selectedCategoryFilter, selectedStatusFilter, onlyFavorites) {
        ideas.filter { idea ->
            val matchesQuery = searchQuery.isBlank() ||
                    idea.title.contains(searchQuery, ignoreCase = true) ||
                    idea.description.contains(searchQuery, ignoreCase = true) ||
                    idea.tags.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "الكل" || idea.category == selectedCategoryFilter
            val matchesStatus = selectedStatusFilter == "الكل" || idea.status == selectedStatusFilter
            val matchesFav = !onlyFavorites || idea.isFavorite
            matchesQuery && matchesCategory && matchesStatus && matchesFav
        }.sortedWith(
            compareByDescending<IdeaVaultEntity> { it.isPinned }
                .thenByDescending { it.updatedAtMillis }
        )
    }

    val totalIdeasCount = ideas.size
    val inProgressCount = ideas.count { it.status == "قيد التنفيذ" }
    val completedCount = ideas.count { it.status == "مكتملة" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💡 مخزن الأفكار",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${filteredIdeas.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onlyFavorites = !onlyFavorites }) {
                        Icon(
                            imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "المفضلة",
                            tint = if (onlyFavorites) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "فكرة جديدة") },
                text = { Text("فكرة جديدة", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Stats Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IdeaStatBadge(label = "الإجمالي", count = totalIdeasCount, icon = Icons.Default.Lightbulb, tint = Color(0xFF3B82F6))
                    IdeaStatBadge(label = "قيد التنفيذ", count = inProgressCount, icon = Icons.Default.TrendingUp, tint = Color(0xFFF59E0B))
                    IdeaStatBadge(label = "تم إنجازها", count = completedCount, icon = Icons.Default.CheckCircle, tint = Color(0xFF10B981))
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                placeholder = { Text("ابحث في عنوان أو تفاصيل أو وسم الفكرة...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(IDEA_CATEGORIES) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Status Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statusOptions = listOf("الكل") + IDEA_STATUSES
                statusOptions.forEach { st ->
                    val isSelected = selectedStatusFilter == st
                    AssistChip(
                        onClick = { selectedStatusFilter = st },
                        label = { Text(st, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            labelColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Idea List
            if (filteredIdeas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "الكل" || onlyFavorites) "لا توجد أفكار مطابقة للتصفية" else "مخزن الأفكار فارغ حالياً!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "سجل أي فكرة مشروع أو بحث أو فكرة دراسية تطرأ على ذهنك فوراً!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("أضف أول فكرة الآن")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredIdeas, key = { it.id }) { idea ->
                        IdeaVaultCard(
                            idea = idea,
                            onTogglePin = { viewModel.toggleIdeaPin(idea) },
                            onToggleFavorite = { viewModel.toggleIdeaFavorite(idea) },
                            onEdit = { editingIdea = idea },
                            onDelete = { ideaToDelete = idea },
                            onConvertToTask = { ideaToConvert = idea },
                            onStatusChange = { newStatus -> viewModel.updateIdeaStatus(idea, newStatus) }
                        )
                    }
                }
            }
        }
    }

    // Add Idea Dialog
    if (showAddDialog) {
        IdeaEditDialog(
            idea = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, cat, priority, status, colorHex, tags, isPinned, isFav ->
                viewModel.addIdea(
                    title = title,
                    description = desc,
                    category = cat,
                    priority = priority,
                    status = status,
                    colorHex = colorHex,
                    tags = tags,
                    isPinned = isPinned,
                    isFavorite = isFav
                )
                showAddDialog = false
            }
        )
    }

    // Edit Idea Dialog
    editingIdea?.let { idea ->
        IdeaEditDialog(
            idea = idea,
            onDismiss = { editingIdea = null },
            onSave = { title, desc, cat, priority, status, colorHex, tags, isPinned, isFav ->
                viewModel.updateIdea(
                    idea.copy(
                        title = title,
                        description = desc,
                        category = cat,
                        priority = priority,
                        status = status,
                        colorHex = colorHex,
                        tags = tags,
                        isPinned = isPinned,
                        isFavorite = isFav
                    )
                )
                editingIdea = null
            }
        )
    }

    // Delete Confirmation Dialog
    ideaToDelete?.let { idea ->
        AlertDialog(
            onDismissRequest = { ideaToDelete = null },
            title = { Text("حذف الفكرة", fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد من رغبتك في حذف فكرة: \"${idea.title}\"؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteIdea(idea)
                        ideaToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ideaToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Convert Idea to Task Confirmation Dialog
    ideaToConvert?.let { idea ->
        AlertDialog(
            onDismissRequest = { ideaToConvert = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تحويل الفكرة إلى مهمة دراسية", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column {
                    Text("سيتم إضافة الفكرة كـ مهمة في قائمة المهام الدراسية للبدء في تنفيذها ومتابعتها:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = idea.title, fontWeight = FontWeight.Bold)
                            if (idea.description.isNotBlank()) {
                                Text(text = idea.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.convertIdeaToTask(idea)
                        ideaToConvert = null
                        onNavigateToTasks()
                    }
                ) {
                    Text("تحويل والانتقال للمهام")
                }
            },
            dismissButton = {
                TextButton(onClick = { ideaToConvert = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun IdeaStatBadge(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun IdeaVaultCard(
    idea: IdeaVaultEntity,
    onTogglePin: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvertToTask: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val cardAccentColor = try {
        Color(android.graphics.Color.parseColor(idea.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    val formattedDate = remember(idea.updatedAtMillis) {
        dateFormatter.format(Date(idea.updatedAtMillis))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (idea.isPinned) 1.5.dp else 0.8.dp,
            color = if (idea.isPinned) cardAccentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Color bar + Category + Status + Pin + Fav
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cardAccentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = cardAccentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = idea.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cardAccentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Priority Badge
                    val priorityColor = when (idea.priority) {
                        "عاجلة" -> Color(0xFFEF4444)
                        "عالية" -> Color(0xFFF97316)
                        "متوسطة" -> Color(0xFF3B82F6)
                        else -> Color(0xFF6B7280)
                    }
                    Surface(
                        color = priorityColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = idea.priority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Actions: Pin, Fav, Options
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (idea.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                            contentDescription = "تثبيت",
                            tint = if (idea.isPinned) cardAccentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (idea.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "المفضلة",
                            tint = if (idea.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = idea.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Description
            if (idea.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = idea.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Tags
            if (idea.tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    idea.tags.split(",", "،").map { it.trim() }.filter { it.isNotBlank() }.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Footer: Status Dropdown/Selector + Convert to Task + Delete + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status selector menu
                var showStatusMenu by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { showStatusMenu = true },
                        label = { Text("الحالة: ${idea.status}", fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (idea.status) {
                                "مكتملة" -> Color(0xFF10B981).copy(alpha = 0.15f)
                                "قيد التنفيذ" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            labelColor = when (idea.status) {
                                "مكتملة" -> Color(0xFF047857)
                                "قيد التنفيذ" -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        IDEA_STATUSES.forEach { statusOption ->
                            DropdownMenuItem(
                                text = { Text(statusOption) },
                                onClick = {
                                    onStatusChange(statusOption)
                                    showStatusMenu = false
                                },
                                leadingIcon = {
                                    if (idea.status == statusOption) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = cardAccentColor)
                                    }
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Convert to Task Button
                    IconButton(
                        onClick = onConvertToTask,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAlarm,
                            contentDescription = "تحويل لمهمة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaEditDialog(
    idea: IdeaVaultEntity?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        category: String,
        priority: String,
        status: String,
        colorHex: String,
        tags: String,
        isPinned: Boolean,
        isFavorite: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(idea?.title ?: "") }
    var description by remember { mutableStateOf(idea?.description ?: "") }
    var category by remember { mutableStateOf(idea?.category ?: "مشروع") }
    var priority by remember { mutableStateOf(idea?.priority ?: "متوسطة") }
    var status by remember { mutableStateOf(idea?.status ?: "جديدة") }
    var colorHex by remember { mutableStateOf(idea?.colorHex ?: "#3B82F6") }
    var tags by remember { mutableStateOf(idea?.tags ?: "") }
    var isPinned by remember { mutableStateOf(idea?.isPinned ?: false) }
    var isFavorite by remember { mutableStateOf(idea?.isFavorite ?: false) }

    var titleError by remember { mutableStateOf(false) }

    // Speech-to-Text Voice Dictation Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                if (title.isBlank()) {
                    title = spokenText
                } else {
                    description = if (description.isBlank()) spokenText else "$description $spokenText"
                }
            }
        }
    }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (idea == null) "💡 فكرة جديدة" else "✏️ تعديل الفكرة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث لتسجيل فكرتك صوتياً...")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            // ignore if no speech engine
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "إملاء صوتي",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (titleError && it.isNotBlank()) titleError = false
                    },
                    label = { Text("عنوان الفكرة *") },
                    placeholder = { Text("مثال: تطبيق ذكي لحل واجبات الفيزياء") },
                    isError = titleError,
                    supportingText = if (titleError) { { Text("يرجى إدخال عنوان للفكرة", color = MaterialTheme.colorScheme.error) } } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("تفاصيل وملاحظات الفكرة") },
                    placeholder = { Text("اكتب الخطوات أو الهدف من الفكرة أو المراجع...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category Selector
                Text("التصنيف:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableCats = IDEA_CATEGORIES.filter { it != "الكل" }
                    availableCats.forEach { catOption ->
                        FilterChip(
                            selected = category == catOption,
                            onClick = { category = catOption },
                            label = { Text(catOption, fontSize = 11.sp) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Priority & Status in Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Priority
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الأهمية:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        var priorityExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedCard(
                                onClick = { priorityExpanded = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(priority, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = priorityExpanded,
                                onDismissRequest = { priorityExpanded = false }
                            ) {
                                IDEA_PRIORITIES.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p) },
                                        onClick = {
                                            priority = p
                                            priorityExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الحالة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        var statusExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedCard(
                                onClick = { statusExpanded = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(status, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                IDEA_STATUSES.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            status = s
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Tags
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("الوسوم (مفصولة بفواصل)") },
                    placeholder = { Text("مثال: ثانوية عامة، برمجة، مسابقة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Color Picker
                Text("لون تمييز الفكرة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IDEA_COLORS.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { colorHex = hex }
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Pin & Favorite Checkboxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                        Text("تثبيت في الأعلى 📌", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                        Text("إضافة للمفضلة ⭐", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                    } else {
                        onSave(title, description, category, priority, status, colorHex, tags, isPinned, isFavorite)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (idea == null) "حفظ الفكرة" else "تحديث الفكرة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
