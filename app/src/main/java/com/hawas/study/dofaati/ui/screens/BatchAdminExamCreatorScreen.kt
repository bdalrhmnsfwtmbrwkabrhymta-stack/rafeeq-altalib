package com.hawas.study.dofaati.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
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
        batches.firstOrNull { it.id == activeBatchId } ?: batches.first()
    }
    val parsedQuestions by viewModel.adminParsedQuestions.collectAsStateWithLifecycle()
    val members by viewModel.batchMembers.collectAsStateWithLifecycle()

    var activeTopTab by remember { mutableStateOf(ExamAdminTopTab.SETTINGS) }
    var examTitle by remember { mutableStateOf("كويز الكهربية — الأسبوع 3") }
    var bulkText by remember { mutableStateOf("") }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importQuestionsFromPdfUri(uri)
        }
    }

    LaunchedEffect(bulkText) {
        if (bulkText.isNotBlank()) {
            viewModel.parseAdminBulkPastePreview(bulkText)
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
                    subtitle = batch.name,
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
                            parsedCount = parsedQuestions.size,
                            onImportPdf = {
                                pdfLauncher.launch(arrayOf("application/pdf", "text/plain"))
                            },
                            onInsertSampleBatch = {
                                bulkText = buildSampleBulkPasteText()
                            },
                            onPublish = {
                                viewModel.publishBulkExamToBatch(bulkText, examTitle)
                            }
                        )
                    }

                    item {
                        AdminContextTabPanel(
                            tab = activeTopTab,
                            batchSubjectsCount = batch.subjects.size,
                            membersCount = members.size,
                            uploadPermissionLabel = batch.uploadPermission.labelAr,
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
                    subtitle = batch.name,
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "admin_exam_header_land"
                )
                AdminThreeSegmentBar(
                    selected = activeTopTab,
                    onSelect = { activeTopTab = it }
                )
                AdminContextTabPanel(
                    tab = activeTopTab,
                    batchSubjectsCount = batch.subjects.size,
                    membersCount = members.size,
                    uploadPermissionLabel = batch.uploadPermission.labelAr,
                    onOpenBatchManagement = { viewModel.navigateTo(DofaatiRoute.CHAT_AND_ADMIN) }
                )
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    BulkExamCreatorCard(
                        examTitle = examTitle,
                        onExamTitleChange = { examTitle = it },
                        bulkText = bulkText,
                        onBulkTextChange = { bulkText = it },
                        parsedCount = parsedQuestions.size,
                        onImportPdf = {
                            pdfLauncher.launch(arrayOf("application/pdf", "text/plain"))
                        },
                        onInsertSampleBatch = {
                            bulkText = buildSampleBulkPasteText()
                        },
                        onPublish = {
                            viewModel.publishBulkExamToBatch(bulkText, examTitle)
                        }
                    )
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
    parsedCount: Int,
    onImportPdf: () -> Unit,
    onInsertSampleBatch: () -> Unit,
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
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_exam_title_input")
            )

            OutlinedTextField(
                value = bulkText,
                onValueChange = onBulkTextChange,
                placeholder = {
                    Text(
                        text = "لصق أو استيراد 100 سؤال دفعة واحدة",
                        fontFamily = ReadexProFontFamily,
                        color = DofaatiColors.SlateMuted
                    )
                },
                minLines = 6,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_bulk_paste_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInsertSampleBatch,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("btn_fill_sample_bulk")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("نموذج سريع", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onImportPdf,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("btn_admin_import_pdf")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ملف PDF", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DofaatiColors.RoyalBlueSoft,
                border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
            ) {
                Text(
                    text = if (parsedCount > 0) {
                        "تم التعرف تلقائياً على $parsedCount سؤالاً جاهزاً للنشر المباشر"
                    } else {
                        "يدعم الصيغ المرقمة (1- / س1:) والاختيارات (أ/ب/ج/د) أو (A/B/C/D)"
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
                    text = "نشر الامتحان للدفعة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
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
                ExamAdminTopTab.MEMBERS -> "$membersCount أعضاء نشطين جاهزين لدخول التحدي الحي"
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

private fun buildSampleBulkPasteText(): String = """
1- وحدة قياس شدة التيار الكهربي هي:
أ) الفولت
ب) الأمبير *
ج) الأوم
د) الوات

2- المقاومة المكافئة لثلاث مقاومات متساوية قيمة كل منها 9 أوم متصلة على التوازي تساوي:
أ) 27 أوم
ب) 3 أوم *
ج) 9 أوم
د) 6 أوم

3- عند زيادة طول موصل للضعف مع ثبوت مساحة مقطعه فإن مقاومته:
أ) تقل للنصف
ب) تظل ثابتة
ج) تزداد للضعف *
د) تزداد أربع مرات
""".trimIndent()
