package com.hawas.study.dofaati.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiQuestionItem
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader

private enum class ExamAdminTopTab(val label: String) {
    SETTINGS("الإعدادات"),
    SUBJECTS("المواد"),
    MEMBERS("الأعضاء")
}

@Composable
fun BatchAdminExamCreatorScreen(viewModel: DofaatiViewModel) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val batch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.firstOrNull()
    }
    val parsedQuestions by viewModel.adminParsedQuestions.collectAsStateWithLifecycle()
    val allMembers by viewModel.batchMembers.collectAsStateWithLifecycle()
    val batchMembers = remember(allMembers, batch) {
        if (batch == null) emptyList() else allMembers.filter { it.batchId == batch.id || it.batchId.isBlank() }
    }

    var activeTopTab by remember { mutableStateOf(ExamAdminTopTab.SETTINGS) }
    var examTitle by remember { mutableStateOf("") }
    var bulkText by remember { mutableStateOf("") }
    var allowAiFallback by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importQuestionsFromPdfUri(uri)
        }
    }

    LaunchedEffect(bulkText, allowAiFallback) {
        if (bulkText.isNotBlank()) {
            viewModel.parseAdminBulkPastePreview(bulkText, allowAiFallback = allowAiFallback)
        } else {
            viewModel.parseAdminBulkPastePreview("", allowAiFallback = false)
        }
    }

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.45f,
        portraitContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DofaatiColors.Background)
                    .testTag("admin_exam_creator_root")
            ) {
                DofaatiRoyalBlueHeader(
                    title = "إدارة الدفعة ومنشئ الامتحانات",
                    subtitle = batch?.name ?: "منشئ الامتحانات",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "admin_exam_header"
                )
                AdminThreeSegmentBar(
                    selected = activeTopTab,
                    onSelect = { activeTopTab = it }
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        BulkExamCreatorCard(
                            examTitle = examTitle,
                            onExamTitleChange = { examTitle = it },
                            bulkText = bulkText,
                            onBulkTextChange = { bulkText = it },
                            allowAiFallback = allowAiFallback,
                            onToggleAiFallback = { allowAiFallback = it },
                            parsedCount = parsedQuestions.size,
                            onImportPdf = {
                                pdfLauncher.launch(arrayOf("application/pdf", "text/plain"))
                            },
                            onPublish = {
                                viewModel.publishBulkExamToBatch(examTitle)
                            }
                        )
                    }

                    if (parsedQuestions.isNotEmpty()) {
                        items(parsedQuestions, key = { it.id }) { q ->
                            EditableParsedQuestionPreviewCard(
                                item = q,
                                onSelectCorrect = { idx ->
                                    viewModel.updateAdminParsedQuestionCorrectOption(q.id, idx)
                                },
                                onDelete = { viewModel.removeAdminParsedQuestion(q.id) }
                            )
                        }
                    }

                    item {
                        AdminContextTabPanel(
                            tab = activeTopTab,
                            batchSubjectsCount = batch?.subjects?.size ?: 0,
                            membersCount = batchMembers.size,
                            uploadPermissionLabel = batch?.uploadPermission?.labelAr ?: "—",
                            onOpenBatchManagement = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                        )
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DofaatiRoyalBlueHeader(
                    title = "إدارة الدفعة ومنشئ الامتحانات",
                    subtitle = batch?.name ?: "منشئ الامتحانات",
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "admin_exam_header_land"
                )
                AdminThreeSegmentBar(
                    selected = activeTopTab,
                    onSelect = { activeTopTab = it }
                )
                AdminContextTabPanel(
                    tab = activeTopTab,
                    batchSubjectsCount = batch?.subjects?.size ?: 0,
                    membersCount = batchMembers.size,
                    uploadPermissionLabel = batch?.uploadPermission?.labelAr ?: "—",
                    onOpenBatchManagement = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                )
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BulkExamCreatorCard(
                        examTitle = examTitle,
                        onExamTitleChange = { examTitle = it },
                        bulkText = bulkText,
                        onBulkTextChange = { bulkText = it },
                        allowAiFallback = allowAiFallback,
                        onToggleAiFallback = { allowAiFallback = it },
                        parsedCount = parsedQuestions.size,
                        onImportPdf = {
                            pdfLauncher.launch(arrayOf("application/pdf", "text/plain"))
                        },
                        onPublish = {
                            viewModel.publishBulkExamToBatch(examTitle)
                        }
                    )
                }
                if (parsedQuestions.isNotEmpty()) {
                    items(parsedQuestions, key = { it.id }) { q ->
                        EditableParsedQuestionPreviewCard(
                            item = q,
                            onSelectCorrect = { idx ->
                                viewModel.updateAdminParsedQuestionCorrectOption(q.id, idx)
                            },
                            onDelete = { viewModel.removeAdminParsedQuestion(q.id) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AdminThreeSegmentBar(
    selected: ExamAdminTopTab,
    onSelect: (ExamAdminTopTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DofaatiColors.RoyalBlueDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExamAdminTopTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(tab) }
                        .testTag("admin_top_tab_${tab.name}"),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = tab.label,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) DofaatiColors.RoyalBlue else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkExamCreatorCard(
    examTitle: String,
    onExamTitleChange: (String) -> Unit,
    bulkText: String,
    onBulkTextChange: (String) -> Unit,
    allowAiFallback: Boolean,
    onToggleAiFallback: (Boolean) -> Unit,
    parsedCount: Int,
    onImportPdf: () -> Unit,
    onPublish: () -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Quiz, contentDescription = null, tint = DofaatiColors.RoyalBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "إنشاء تحدي مباشر أو امتحان للدفعة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            OutlinedTextField(
                value = examTitle,
                onValueChange = onExamTitleChange,
                label = { Text("عنوان التحدي أو الامتحان", fontFamily = ReadexProFontFamily) },
                placeholder = { Text("مثال: اختبار الفصل الأول", fontFamily = ReadexProFontFamily) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_exam_title_input")
            )

            OutlinedTextField(
                value = bulkText,
                onValueChange = onBulkTextChange,
                placeholder = {
                    Text(
                        text = "الصق أسئلتك المرقمة هنا:\n1- ما وحدة شدة التيار؟\nأ) الفولت\nب) الأمبير *\nالإجابة: ب",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.5.sp,
                        color = DofaatiColors.SlateMuted
                    )
                },
                minLines = 6,
                maxLines = 12,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_bulk_paste_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleAiFallback(!allowAiFallback) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = allowAiFallback,
                    onCheckedChange = onToggleAiFallback,
                    colors = CheckboxDefaults.colors(checkedColor = DofaatiColors.RoyalBlue)
                )
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "استخراج ذكي بالـ AI إذا كان النص غير منسق",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 12.5.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            OutlinedButton(
                onClick = onImportPdf,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_admin_import_pdf")
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استيراد أسئلة من ملف PDF أو نص", fontFamily = ReadexProFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DofaatiColors.RoyalBlueSoft,
                border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
            ) {
                Text(
                    text = if (parsedCount > 0) {
                        "تم تحليل $parsedCount سؤالاً بنجاح — راجع الإجابة الصحيحة أدناه قبل النشر"
                    } else {
                        "يدعم الصيغ المرقمة (1- / س1:) والاختيارات (أ/ب/ج/د) أو (A/B/C/D) ووسم الإجابة الصحيحة (* أو الإجابة:)"
                    },
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = DofaatiColors.RoyalBlueDark,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("admin_parsed_preview_text")
                )
            }

            Button(
                onClick = onPublish,
                enabled = parsedCount > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_publish_exam_to_batch")
            ) {
                Icon(Icons.Default.Publish, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (parsedCount > 0) "نشر الامتحان ($parsedCount سؤال)" else "نشر الامتحان للدفعة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
            }
        }
    }
}

@Composable
private fun EditableParsedQuestionPreviewCard(
    item: DofaatiQuestionItem,
    onSelectCorrect: (Int) -> Unit,
    onDelete: () -> Unit
) {
    DofaatiCard(
        borderColor = DofaatiColors.RoyalBlueTint,
        borderWidth = 1.2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "س${item.questionNumber}: ${item.questionText}",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = DofaatiColors.NavyInk,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "إزالة السؤال", tint = DofaatiColors.RedAlert)
                }
            }
            item.options.forEachIndexed { idx, opt ->
                val isCorrect = idx == item.correctOptionIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectCorrect(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCorrect) DofaatiColors.EmeraldGreen.copy(alpha = 0.12f) else DofaatiColors.Background,
                    border = BorderStroke(1.dp, if (isCorrect) DofaatiColors.EmeraldGreen else DofaatiColors.BorderSoft)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = opt,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.5.sp,
                            color = DofaatiColors.NavyInk
                        )
                        if (isCorrect) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "إجابة صحيحة", tint = DofaatiColors.EmeraldGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminContextTabPanel(
    tab: ExamAdminTopTab,
    batchSubjectsCount: Int,
    membersCount: Int,
    uploadPermissionLabel: String,
    onOpenBatchManagement: () -> Unit
) {
    DofaatiCard {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (tab) {
                ExamAdminTopTab.SETTINGS -> Icons.Default.Settings
                ExamAdminTopTab.SUBJECTS -> Icons.Default.LibraryBooks
                ExamAdminTopTab.MEMBERS -> Icons.Default.Groups
            }
            val desc = when (tab) {
                ExamAdminTopTab.SETTINGS -> "صلاحية الرفع الحالية: $uploadPermissionLabel • تقييم تلقائي فوري"
                ExamAdminTopTab.SUBJECTS -> "$batchSubjectsCount مواد مفعلة لربط الامتحانات المباشرة"
                ExamAdminTopTab.MEMBERS -> "$membersCount أعضاء في الدفعة"
            }
            Icon(icon, contentDescription = null, tint = DofaatiColors.RoyalBlue)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = desc,
                fontFamily = ReadexProFontFamily,
                fontSize = 12.5.sp,
                color = DofaatiColors.SlateBody,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenBatchManagement) {
                Text("إدارة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}
