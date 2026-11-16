package com.hawas.study.dofaati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.hawas.study.dofaati.models.DofaatiAudioLesson
import com.hawas.study.dofaati.ui.theme.DofaatiAdaptiveScreenContainer
import com.hawas.study.dofaati.ui.theme.DofaatiAudioWaveform
import com.hawas.study.dofaati.ui.theme.DofaatiCard
import com.hawas.study.dofaati.ui.theme.DofaatiColors
import com.hawas.study.dofaati.ui.theme.DofaatiLtrText
import com.hawas.study.dofaati.ui.theme.DofaatiRoyalBlueHeader
import com.hawas.study.dofaati.ui.theme.formatDurationLtr

@Composable
fun AudioTranscriptSummaryScreen(viewModel: DofaatiViewModel) {
    val lessons by viewModel.audioLessons.collectAsStateWithLifecycle()
    val activeLessonId by viewModel.activeLessonId.collectAsStateWithLifecycle()
    val playingAudioId by viewModel.playingAudioId.collectAsStateWithLifecycle()
    val audioProgress by viewModel.audioProgressFraction.collectAsStateWithLifecycle()

    val lesson = remember(lessons, activeLessonId) {
        lessons.firstOrNull { it.id == activeLessonId } ?: lessons.first()
    }
    var isConverting by remember { mutableStateOf(false) }

    val triggerConvertTo50: () -> Unit = {
        if (!isConverting) {
            isConverting = true
            viewModel.convertCurrentTranscriptTo50Questions { generated ->
                isConverting = false
                viewModel.launchQuizFromQuestions(
                    title = "تحدي ${lesson.title} (50 سؤال)",
                    questions = generated
                )
            }
        }
    }

    DofaatiAdaptiveScreenContainer(
        useSafeDrawing = false,
        landscapeLeftWeight = 0.48f,
        portraitContent = {
            Scaffold(
                containerColor = DofaatiColors.Background,
                topBar = {
                    DofaatiRoyalBlueHeader(
                        title = "تفريغ الريكورد والملخص الذكي",
                        subtitle = lesson.title,
                        onNavigateBack = { viewModel.navigateBack() },
                        testTag = "audio_transcript_header"
                    )
                },
                bottomBar = {
                    Surface(
                        color = Color.White,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, DofaatiColors.BorderSoft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            ConvertTranscriptTo50Button(
                                isConverting = isConverting,
                                onClick = triggerConvertTo50
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag("audio_transcript_root"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SyncedTranscriptAudioCard(
                            lesson = lesson,
                            isPlaying = playingAudioId == lesson.id,
                            progress = if (playingAudioId == lesson.id) audioProgress else 0.32f,
                            onTogglePlay = { viewModel.toggleAudioPlayback(lesson.id) }
                        )
                    }
                    item {
                        ExtractedLawsCard(laws = lesson.extractedLaws)
                    }
                    item {
                        ImportantExamPointsCard(points = lesson.importantExamPoints)
                    }
                }
            }
        },
        landscapeLeftPane = {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DofaatiRoyalBlueHeader(
                    title = "تفريغ الريكورد والملخص الذكي",
                    subtitle = lesson.title,
                    onNavigateBack = { viewModel.navigateBack() },
                    testTag = "audio_transcript_header_land"
                )
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        item {
                            SyncedTranscriptAudioCard(
                                lesson = lesson,
                                isPlaying = playingAudioId == lesson.id,
                                progress = if (playingAudioId == lesson.id) audioProgress else 0.32f,
                                onTogglePlay = { viewModel.toggleAudioPlayback(lesson.id) }
                            )
                        }
                    }
                }
                ConvertTranscriptTo50Button(
                    isConverting = isConverting,
                    onClick = triggerConvertTo50
                )
            }
        },
        landscapeRightPane = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item { ExtractedLawsCard(laws = lesson.extractedLaws) }
                item { ImportantExamPointsCard(points = lesson.importantExamPoints) }
            }
        }
    )
}

@Composable
private fun SyncedTranscriptAudioCard(
    lesson: DofaatiAudioLesson,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit
) {
    DofaatiCard(
        borderColor = DofaatiColors.RoyalBlueTint,
        borderWidth = 1.5.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DofaatiColors.RoyalBlue)
                            .testTag("btn_transcript_play_audio")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل الدرس",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            text = lesson.title,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DofaatiColors.NavyInk
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = DofaatiColors.RoyalBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تفريغ نصي متزامن مع الصوت",
                                fontFamily = ReadexProFontFamily,
                                fontSize = 12.sp,
                                color = DofaatiColors.SlateMuted
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DofaatiColors.RoyalBlueSoft
                ) {
                    DofaatiLtrText(
                        text = formatDurationLtr(lesson.durationSec),
                        fontSize = 12.5.sp,
                        color = DofaatiColors.RoyalBlueDark,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            DofaatiAudioWaveform(
                peaks = lesson.waveformPeaks,
                progressFraction = progress,
                activeColor = DofaatiColors.RoyalBlue,
                inactiveColor = DofaatiColors.RoyalBlueTint
            )

            HorizontalDivider(color = DofaatiColors.BorderSoft)

            Text(
                text = "النص الكامل للتفريغ:",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = DofaatiColors.RoyalBlueDark
            )
            Text(
                text = lesson.transcriptText,
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 22.sp,
                color = DofaatiColors.NavyInk,
                modifier = Modifier.testTag("transcript_full_text")
            )
        }
    }
}

@Composable
private fun ExtractedLawsCard(laws: List<String>) {
    DofaatiCard(modifier = Modifier.testTag("card_extracted_laws")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DofaatiColors.RoyalBlueSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Balance,
                        contentDescription = null,
                        tint = DofaatiColors.RoyalBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "أهم القوانين المستخرجة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            laws.forEachIndexed { idx, law ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DofaatiColors.RoyalBlueSoft,
                    border = BorderStroke(1.dp, DofaatiColors.RoyalBlueTint)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DofaatiColors.RoyalBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontFamily = ReadexProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = law,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = DofaatiColors.NavyInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportantExamPointsCard(points: List<String>) {
    DofaatiCard(modifier = Modifier.testTag("card_important_exam_points")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DofaatiColors.EmeraldGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FactCheck,
                        contentDescription = null,
                        tint = DofaatiColors.EmeraldGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "نقاط الامتحانات الهامة",
                    fontFamily = ReadexProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = DofaatiColors.NavyInk
                )
            }

            points.forEach { pt ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DofaatiColors.EmeraldGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, DofaatiColors.EmeraldGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "★",
                            fontSize = 14.sp,
                            color = DofaatiColors.AmberGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pt,
                            fontFamily = ReadexProFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = DofaatiColors.NavyInk
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConvertTranscriptTo50Button(
    isConverting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isConverting,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DofaatiColors.RoyalBlue),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("btn_convert_transcript_to_50")
    ) {
        if (isConverting) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "جاري تقسيم التفريغ وتوليد 50 سؤالاً...",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تحويل التفريغ إلى 50 سؤال تفاعلي >",
                fontFamily = ReadexProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp
            )
        }
    }
}
