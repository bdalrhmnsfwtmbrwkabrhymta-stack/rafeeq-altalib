package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.NoteEntity
import com.example.data.database.SubjectEntity
import com.example.data.database.VoiceNoteEntity
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesScreen(
    viewModel: MainViewModel,
    onNavigateToOcr: () -> Unit = {}
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val voiceNotes by viewModel.voiceNotes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()

    var selectedTab by remember { mutableStateOf("VOICE") } // "VOICE" or "TEXT"
    var showAddTextNoteDialog by remember { mutableStateOf(false) }
    var showVoiceRecorderDialog by remember { mutableStateOf(false) }
    var selectedSubjectFilterId by remember { mutableStateOf<Long?>(null) }

    // TextToSpeech for listening back to notes
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeakingNoteId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialize Arabic / System locale
                ttsEngine?.language = Locale("ar")
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speakText(noteId: Long, text: String) {
        if (isSpeakingNoteId == noteId) {
            ttsEngine?.stop()
            isSpeakingNoteId = null
        } else {
            ttsEngine?.stop()
            ttsEngine?.language = Locale("ar")
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NoteTTS_$noteId")
            isSpeakingNoteId = noteId
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == "VOICE") {
                        showVoiceRecorderDialog = true
                    } else {
                        showAddTextNoteDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (selectedTab == "VOICE") Icons.Default.Mic else Icons.Default.Add,
                    contentDescription = "إضافة"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الملاحظات وتسجيلات الدروس 🎙️",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                FilledTonalButton(
                    onClick = onNavigateToOcr,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مسح ملخص (ML Kit) 📸", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Selector Row (Voice Notes vs Text Notes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedTab == "VOICE") MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { selectedTab = "VOICE" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (selectedTab == "VOICE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الملاحظات الصوتية 🎙️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedTab == "VOICE") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedTab == "TEXT") MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { selectedTab = "TEXT" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Note,
                            contentDescription = null,
                            tint = if (selectedTab == "TEXT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الملاحظات النصية 📝",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedTab == "TEXT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subjects Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedSubjectFilterId == null,
                        onClick = { selectedSubjectFilterId = null },
                        label = { Text("الكل") }
                    )
                }

                items(subjects) { subject ->
                    FilterChip(
                        selected = selectedSubjectFilterId == subject.id,
                        onClick = { selectedSubjectFilterId = subject.id },
                        label = { Text(subject.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == "VOICE") {
                val filteredVoiceNotes = if (selectedSubjectFilterId == null) {
                    voiceNotes
                } else {
                    voiceNotes.filter { it.subjectId == selectedSubjectFilterId }
                }

                if (filteredVoiceNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "لا توجد ملاحظات صوتية مسجلة بعد.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "اضغط على الميكروفون بالأسفل لتسجيل ملاحظة صوتية سريعة لدروسك!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredVoiceNotes, key = { it.id }) { voiceNote ->
                            VoiceNoteCardItem(
                                voiceNote = voiceNote,
                                isSpeaking = isSpeakingNoteId == voiceNote.id,
                                onSpeakToggle = { speakText(voiceNote.id, voiceNote.transcribedText) },
                                onCopy = {
                                    viewModel.copyTextToClipboard(
                                        voiceNote.title,
                                        "${voiceNote.title}\nالمادة: ${voiceNote.subjectName}\n\n${voiceNote.transcribedText}"
                                    )
                                },
                                onDelete = { viewModel.deleteVoiceNote(voiceNote) },
                                onConvertToFlashcard = {
                                    viewModel.quickConvertWordToAnkiDeck(
                                        com.example.data.database.DictionaryWordEntity(
                                            wordEnglish = voiceNote.title,
                                            wordArabic = voiceNote.transcribedText,
                                            partOfSpeech = voiceNote.subjectName
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                val filteredNotes = if (selectedSubjectFilterId == null) {
                    notes
                } else {
                    notes.filter { it.subjectId == selectedSubjectFilterId }
                }

                if (filteredNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد ملاحظات نصية حالياً. أضف ملاحظة جديدة للبدء!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredNotes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onCopy = { viewModel.copyTextToClipboard("ملاحظة: ${note.title}", "${note.title}\n\n${note.content}") },
                                onDelete = { viewModel.deleteNote(note) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal 1: Speech-To-Text Voice Recorder Dialog
    if (showVoiceRecorderDialog) {
        VoiceNoteRecorderDialog(
            subjects = subjects,
            onDismiss = { showVoiceRecorderDialog = false },
            onSaveVoiceNote = { title, transcribedText, subjectId, subjectName, durationSec ->
                viewModel.addVoiceNote(title, transcribedText, subjectId, subjectName, durationSec)
                showVoiceRecorderDialog = false
            }
        )
    }

    // Modal 2: Add Standard Text Note Dialog
    if (showAddTextNoteDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var selectedSubjectId by remember { mutableStateOf<Long?>(null) }

        AlertDialog(
            onDismissRequest = { showAddTextNoteDialog = false },
            title = { Text("إضافة ملاحظة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان الملاحظة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("محتوى الملاحظة") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.addNote(title, content, selectedSubjectId)
                        showAddTextNoteDialog = false
                    }
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextNoteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNoteRecorderDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSaveVoiceNote: (title: String, transcribedText: String, subjectId: Long?, subjectName: String, durationSec: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var transcribedText by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull()) }
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                transcribedText = if (transcribedText.isBlank()) spokenText else "$transcribedText $spokenText"
                if (title.isBlank()) {
                    title = "ملاحظة: " + spokenText.take(20) + "..."
                }
            }
        }
    }

    fun startSpeechRecognition() {
        isListening = true
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن لتسجيل الملاحظة الدراسية...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("تسجيل ملاحظة صوتية للمادة 🎙️")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Subject Dropdown Selector
                Text("اختر المادة الدراسية:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedSubject == null,
                            onClick = { selectedSubject = null },
                            label = { Text("عام") }
                        )
                    }
                    items(subjects) { subject ->
                        FilterChip(
                            selected = selectedSubject?.id == subject.id,
                            onClick = { selectedSubject = subject },
                            label = { Text(subject.name) }
                        )
                    }
                }

                // Voice Recording Trigger Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .clickable { startSpeechRecognition() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = if (isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                    contentDescription = "تسجيل",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isListening) "جاري الاستماع لصوتك..." else "انقر هنا لبدء التحدث والتحويل لنص",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الملاحظة") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = transcribedText,
                    onValueChange = { transcribedText = it },
                    label = { Text("النص المحول من الصوت (أو تعديله هنا)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (transcribedText.isNotBlank()) {
                        onSaveVoiceNote(
                            title.ifBlank { "ملاحظة صوتية - ${selectedSubject?.name ?: "عام"}" },
                            transcribedText,
                            selectedSubject?.id,
                            selectedSubject?.name ?: "عام",
                            15
                        )
                    }
                },
                enabled = transcribedText.isNotBlank()
            ) {
                Text("حفظ في Room 💾")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun VoiceNoteCardItem(
    voiceNote: VoiceNoteEntity,
    isSpeaking: Boolean,
    onSpeakToggle: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onConvertToFlashcard: () -> Unit
) {
    val dateFormatted = remember(voiceNote.createdAtMillis) {
        val sdf = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale("ar"))
        sdf.format(Date(voiceNote.createdAtMillis))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = voiceNote.subjectName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = dateFormatted,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = voiceNote.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Transcribed Speech Text Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = voiceNote.transcribedText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speech Playback Button
                OutlinedButton(
                    onClick = onSpeakToggle,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                        contentDescription = "استماع",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSpeaking) "إيقاف" else "استماع للصوت 🔊", fontSize = 11.sp)
                }

                Row {
                    IconButton(onClick = onConvertToFlashcard, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = "تحويل لأنكي", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCardItem(
    note: NoteEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف", tint = Color.Gray)
                }
            }
        }
    }
}
