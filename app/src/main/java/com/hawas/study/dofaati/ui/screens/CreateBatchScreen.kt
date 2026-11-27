package com.hawas.study.dofaati.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReadexProFontFamily
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiBatch
import com.hawas.study.dofaati.models.UploadPermissionMode
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBatchScreen(viewModel: DofaatiViewModel) {
    val context = LocalContext.current
    val logoPalette = remember {
        listOf(
            0xFF1A6FE3L,
            0xFF0F172AL,
            0xFF10B981L,
            0xFFF97316L,
            0xFF8B5CF6L,
            0xFFDC2626L
        )
    }
    var colorIndex by remember { mutableIntStateOf(0) }
    var batchName by remember { mutableStateOf("") }
    var newSubjectText by remember { mutableStateOf("") }
    val subjects = remember { mutableStateListOf<String>() }
    var uploadPermission by remember { mutableStateOf(UploadPermissionMode.ADMIN_ONLY) }
    var requireApproval by remember { mutableStateOf(true) }
    var createdBatchDialog by remember { mutableStateOf<DofaatiBatch?>(null) }

    val logoColor = Color(logoPalette[colorIndex])
    val displayChar = batchName.trim().firstOrNull()?.toString() ?: "د"

    val submitBatch = {
        val pendingSubject = newSubjectText.trim()
        if (pendingSubject.isNotEmpty() && !subjects.contains(pendingSubject)) {
            subjects.add(pendingSubject)
            newSubjectText = ""
        }
        viewModel.createBatch(
            batchName = batchName,
            logoColorHex = logoPalette[colorIndex],
            subjectNames = subjects.toList(),
            uploadPermission = uploadPermission,
            requireApproval = requireApproval,
            onCreated = { created -> createdBatchDialog = created }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DofaatiColors.Background)
            .testTag("create_batch_screen")
    ) {
        DofaatiRoyalBlueHeader(
            title = "إنشاء دفعة جديدة",
            onNavigateBack = { viewModel.navigateBack() },
            testTag = "create_batch_header"
        )

        DofaatiAdaptiveScreenContainer(
            modifier = Modifier.weight(1f),
            useSafeDrawing = false,
            landscapeLeftWeight = 0.42f,
            portraitContent = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        BatchLogoPickerSection(
                            logoColor = logoColor,
                            displayChar = displayChar,
                            onCycleColor = { colorIndex = (colorIndex + 1) % logoPalette.size }
                        )
                    }
                    item {
                        BatchNameAndSubjectsSection(
                            batchName = batchName,
                            onBatchNameChange = { batchName = it },
                            subjects = subjects,
                            newSubjectText = newSubjectText,
                            onNewSubjectChange = { newSubjectText = it },
                            onAddSubject = {
                                val trimmed = newSubjectText.trim()
                                if (trimmed.isNotEmpty() && !subjects.contains(trimmed)) {
                                    subjects.add(trimmed)
                                    newSubjectText = ""
                                }
                            },
                            onRemoveSubject = { s -> subjects.remove(s) }
                        )
                    }
                    item {
                        BatchPermissionsSection(
                            uploadPermission = uploadPermission,
                            onPermissionChange = { uploadPermission = it },
                            requireApproval = requireApproval,
                            onRequireApprovalChange = { requireApproval = it }
                        )
                    }
                    item {
                        Button(
                            onClick = submitBatch,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_submit_create_batch")
                        ) {
                            Text(
                                text = "إنشاء الدفعة",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            landscapeLeftPane = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    BatchLogoPickerSection(
                        logoColor = logoColor,
                        displayChar = displayChar,
                        onCycleColor = { colorIndex = (colorIndex + 1) % logoPalette.size }
                    )
                    Button(
                        onClick = submitBatch,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("إنشاء الدفعة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            },
            landscapeRightPane = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        BatchNameAndSubjectsSection(
                            batchName = batchName,
                            onBatchNameChange = { batchName = it },
                            subjects = subjects,
                            newSubjectText = newSubjectText,
                            onNewSubjectChange = { newSubjectText = it },
                            onAddSubject = {
                                val trimmed = newSubjectText.trim()
                                if (trimmed.isNotEmpty() && !subjects.contains(trimmed)) {
                                    subjects.add(trimmed)
                                    newSubjectText = ""
                                }
                            },
                            onRemoveSubject = { s -> subjects.remove(s) }
                        )
                    }
                    item {
                        BatchPermissionsSection(
                            uploadPermission = uploadPermission,
                            onPermissionChange = { uploadPermission = it },
                            requireApproval = requireApproval,
                            onRequireApprovalChange = { requireApproval = it }
                        )
                    }
                }
            }
        )
    }

    createdBatchDialog?.let { created ->
        AlertDialog(
            onDismissRequest = {
                createdBatchDialog = null
                viewModel.navigateBack()
            },
            containerColor = Color.White,
            title = {
                Text(
                    text = "تم إنشاء الدفعة بنجاح",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = DofaatiColors.NavyInk
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = created.name,
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DofaatiColors.NavyInk
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DofaatiColors.RoyalBlueSoft,
                        border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlueTint)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("كود الدفعة", fontFamily = ReadexProFontFamily, fontSize = 12.sp, color = DofaatiColors.SlateMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            DofaatiLtrText(
                                text = created.code,
                                fontSize = 24.sp,
                                color = DofaatiColors.RoyalBlue,
                                modifier = Modifier.testTag("created_batch_code_text")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("batch_code", created.code))
                                Toast.makeText(context, "تم نسخ الكود ${created.code}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                        }

                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "انضم لدفعة «${created.name}» في تطبيق مُتعلِّم عبر الكود: ${created.code}"
                                    )
                                }
                                runCatching { context.startActivity(Intent.createChooser(shareIntent, "مشاركة كود الدفعة")) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.EmeraldGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مشاركة", fontFamily = ReadexProFontFamily, fontSize = 12.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        createdBatchDialog = null
                        viewModel.navigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue)
                ) {
                    Text("فتح الدفعة", fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun BatchLogoPickerSection(
    logoColor: Color,
    displayChar: String,
    onCycleColor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(logoColor)
                .clickable { onCycleColor() }
                .testTag("batch_logo_circle"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayChar,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = Color.White
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "تغيير الشعار",
                    tint = DofaatiColors.RoyalBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = "دوس على الشعار تغيّر لونه",
            fontFamily = ReadexProFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = DofaatiColors.SlateMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchNameAndSubjectsSection(
    batchName: String,
    onBatchNameChange: (String) -> Unit,
    subjects: List<String>,
    newSubjectText: String,
    onNewSubjectChange: (String) -> Unit,
    onAddSubject: () -> Unit,
    onRemoveSubject: (String) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = batchName,
                onValueChange = onBatchNameChange,
                label = { Text("اسم الدفعة", fontFamily = ReadexProFontFamily) },
                placeholder = { Text("اكتب اسم دفعتك أو شعبتك...", fontFamily = ReadexProFontFamily) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DofaatiColors.RoyalBlue,
                    unfocusedBorderColor = DofaatiColors.Border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("batch_name_input")
            )

            Text(
                text = "المواد الدراسية (${subjects.size}):",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = DofaatiColors.NavyInk
            )

            if (subjects.isEmpty()) {
                Text(
                    text = "أضف المواد الخاصة بدفعتك بالأسفل.",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 12.sp,
                    color = DofaatiColors.SlateMuted
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.forEach { subject ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveSubject(subject) },
                            label = { Text(subject, fontFamily = ReadexProFontFamily, fontWeight = FontWeight.Medium) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "حذف $subject", modifier = Modifier.size(16.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = DofaatiColors.RoyalBlueSoft,
                                selectedLabelColor = DofaatiColors.RoyalBlueDark
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newSubjectText,
                    onValueChange = onNewSubjectChange,
                    placeholder = { Text("أضف مادة...", fontFamily = ReadexProFontFamily, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("create_batch_subject_input")
                )
                FilledIconButton(
                    onClick = onAddSubject,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DofaatiColors.RoyalBlue),
                    modifier = Modifier.size(48.dp).testTag("btn_add_subject_chip")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة مادة")
                }
            }
        }
    }
}

@Composable
private fun BatchPermissionsSection(
    uploadPermission: UploadPermissionMode,
    onPermissionChange: (UploadPermissionMode) -> Unit,
    requireApproval: Boolean,
    onRequireApprovalChange: (Boolean) -> Unit
) {
    DofaatiCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "صلاحية رفع الملفات والريكوردات:",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = DofaatiColors.NavyInk
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UploadPermissionMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPermissionChange(mode) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("perm_radio_${mode.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uploadPermission == mode,
                            onClick = { onPermissionChange(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = DofaatiColors.RoyalBlue)
                        )
                        Text(
                            text = mode.labelAr,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = DofaatiColors.NavyInk
                        )
                    }
                }
            }

            HorizontalDivider(color = DofaatiColors.BorderSoft)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "الدخول بموافقتي",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = DofaatiColors.NavyInk
                    )
                    Text(
                        text = "أي عضو يطلب الانضمام وتوافق عليه أولاً",
                        fontFamily = ReadexProFontFamily,
                        fontSize = 12.sp,
                        color = DofaatiColors.SlateMuted
                    )
                }
                Switch(
                    checked = requireApproval,
                    onCheckedChange = onRequireApprovalChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DofaatiColors.RoyalBlue
                    ),
                    modifier = Modifier.testTag("switch_require_approval")
                )
            }
        }
    }
}
