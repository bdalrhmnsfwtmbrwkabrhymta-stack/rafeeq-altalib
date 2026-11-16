package com.hawas.study.dofaati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Repeat
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
import com.hawas.study.dofaati.DofaatiViewModel
import com.hawas.study.dofaati.models.DofaatiAudioLesson
import com.hawas.study.dofaati.models.DofaatiRoute
import com.hawas.study.dofaati.models.DofaatiStudyFile
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiAudioWaveform
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader
import com.hawas.study.dofaati.ui.theme.formatDurationLtr
import com.example.ui.theme.ReadexProFontFamily

@Composable
fun SubjectResourcesScreen(viewModel: DofaatiViewModel) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()
    val activeBatchId by viewModel.activeBatchId.collectAsStateWithLifecycle()
    val selectedSubjectId by viewModel.selectedSubjectId.collectAsStateWithLifecycle()
    val allLessons by viewModel.audioLessons.collectAsStateWithLifecycle()
    val allFiles by viewModel.studyFiles.collectAsStateWithLifecycle()
    val playingAudioId by viewModel.playingAudioId.collectAsStateWithLifecycle()
    val progressFraction by viewModel.audioProgressFraction.collectAsStateWithLifecycle()

    val batch = remember(batches, activeBatchId) {
        batches.firstOrNull { it.id == activeBatchId } ?: batches.first()
    }
    val subject = remember(batch, selectedSubjectId) {
        batch.subjects.firstOrNull { it.id == selectedSubjectId } ?: batch.subjects.first()
    }

    var activeResourceTab by remember { mutableIntStateOf(0) } // 0 = Recordings (4), 1 = Files (3)
    var selectedFileForAiSheet by remember { mutableStateOf<DofaatiStudyFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DofaatiColors.Background)
            .testTag("subject_resources_screen")
    ) {
        DofaatiRoyalBlueHeader(
            title = subject.name,
            subtitle = "${batch.name} • ريكوردات الدروس والملازم",
            onNavigateBack = { viewModel.navigateBack() },
            testTag = "subject_resources_header"
        )

        DofaatiAdaptiveScreenContainer(
            modifier = Modifier.weight(1f),
            useSafeDrawing = false,
            landscapeLeftWeight = 0.52f,
            portraitContent = {
                Column(modifier = Modifier.fillMaxSize()) {
                    SegmentedResourcesToggle(
                        selectedIndex = activeResourceTab,
                        recordingsCount = allLessons.size,
                        filesCount = allFiles.size,
                        onSelect = { activeResourceTab = it }
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeResourceTab == 0) {
                            items(allLessons, key = { it.id }) { lesson ->
                                AudioLessonPlayerCard(
                                    lesson = lesson,
                                    isPlaying = playingAudioId == lesson.id,
                                    progressFraction = if (playingAudioId == lesson.id) progressFraction else 0.32f,
                                    onTogglePlay = { viewModel.toggleAudioPlayback(lesson.id) },
                                    onOpenAiTranscript = { viewModel.openAudioTranscript(lesson.id) }
                                )
                            }
                            items(allFiles.take(1), key = { "preview_${it.id}" }) { file ->
                                StudyFileRowCard(
                                    file = file,
                                    onOpenAiSheet = { selectedFileForAiSheet = file }
                                )
                            }
                        } else {
                            items(allFiles, key = { it.id }) { file ->
                                StudyFileRowCard(
                                    file = file,
                                    onOpenAiSheet = { selectedFileForAiSheet = file }
                                )
                            }
                        }
                    }
                }
            },
            landscapeLeftPane = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allLessons, key = { it.id }) { lesson ->
                        AudioLessonPlayerCard(
                            lesson = lesson,
                            isPlaying = playingAudioId == lesson.id,
                            progressFraction = if (playingAudioId == lesson.id) progressFraction else 0.32f,
                            onTogglePlay = { viewModel.toggleAudioPlayback(lesson.id) },
                            onOpenAiTranscript = { viewModel.openAudioTranscript(lesson.id) }
                        )
                    }
                }
            },
            landscapeRightPane = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allFiles, key = { it.id }) { file ->
                        StudyFileRowCard(
                            file = file,
                            onOpenAiSheet = { selectedFileForAiSheet = file }
                        )
                    }
                }
            }
        )
    }

    selectedFileForAiSheet?.let { file ->
        AiStudyActionsBottomSheet(
            fileTitle = file.title,
            onDismiss = { selectedFileForAiSheet = null },
            onInteractiveQuiz = {
                selectedFileForAiSheet = null
                viewModel.navigateTo(DofaatiRoute.BULK_AI_GENERATOR)
            },
            onQuestionBankAndFlashcards = {
                selectedFileForAiSheet = null
                viewModel.navigateTo(DofaatiRoute.MIND_MAP_FLASHCARDS)
            },
            onMindMap = {
                selectedFileForAiSheet = null
                viewModel.navigateTo(DofaatiRoute.MIND_MAP_FLASHCARDS)
            }
        )
    }
}

@Composable
private fun SegmentedResourcesToggle(
    selectedIndex: Int,
    recordingsCount: Int,
    filesCount: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = DofaatiColors.RoyalBlueTint
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            listOf("الريكوردات ($recordingsCount)", "الملفات ($filesCount)").forEachIndexed { idx, label ->
                val active = selectedIndex == idx
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(idx) }
                        .testTag("subject_resource_toggle_$idx"),
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) DofaatiColors.RoyalBlue else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (active) Color.White else DofaatiColors.RoyalBlueDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLessonPlayerCard(
    lesson: DofaatiAudioLesson,
    isPlaying: Boolean,
    progressFraction: Float,
    onTogglePlay: () -> Unit,
    onOpenAiTranscript: () -> Unit
) {
    DofaatiCard(modifier = Modifier.testTag("audio_lesson_card_${lesson.id}")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lesson.title,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = DofaatiColors.NavyInk,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Outlined Royal Blue pill CTA "فرّغه AI" with AI Brain icon matching Mockup 4
                OutlinedButton(
                    onClick = onOpenAiTranscript,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, DofaatiColors.RoyalBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DofaatiColors.RoyalBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("btn_transcribe_ai_${lesson.id}")
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "فرّغه AI",
                        fontFamily = ReadexProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = DofaatiColors.RoyalBlue),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_play_lesson_${lesson.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = Color.White
                    )
                }
                DofaatiAudioWaveform(
                    peaks = lesson.waveformPeaks,
                    progressFraction = progressFraction,
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom audio scrubber + Repeat / Volume controls matching Mockup 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(Icons.Default.Repeat, contentDescription = "تكرار", tint = DofaatiColors.SlateMuted, modifier = Modifier.size(20.dp))
                DofaatiLtrText(
                    text = "${formatDurationLtr((lesson.durationSec * progressFraction).toInt())} / ${formatDurationLtr(lesson.durationSec)}",
                    fontSize = 12.sp,
                    color = DofaatiColors.SlateMuted
                )
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "مستوى الصوت", tint = DofaatiColors.RoyalBlue, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StudyFileRowCard(
    file: DofaatiStudyFile,
    onOpenAiSheet: () -> Unit
) {
    DofaatiCard(
        modifier = Modifier.testTag("study_file_card_${file.id}"),
        onClick = onOpenAiSheet
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = DofaatiColors.RedAlert, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.title,
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DofaatiColors.NavyInk
                )
                Text(
                    text = "${file.pageCount} صفحة · ${file.fileSizeLabel}",
                    fontFamily = ReadexProFontFamily,
                    fontSize = 12.sp,
                    color = DofaatiColors.SlateMuted
                )
            }
            FilledTonalIconButton(
                onClick = onOpenAiSheet,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = DofaatiColors.RoyalBlueSoft,
                    contentColor = DofaatiColors.RoyalBlue
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_open_ai_sheet_${file.id}")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "إجراءات الذكاء الاصطناعي")
            }
        }
    }
}
