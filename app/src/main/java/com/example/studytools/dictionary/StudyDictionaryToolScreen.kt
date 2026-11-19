package com.example.studytools.dictionary

import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.dictionary.DictionaryAssetLoader
import com.example.domain.dictionary.OfflineDictionaryBundle
import com.example.ui.MainViewModel
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import java.util.Locale

private enum class DictionarySectionTab(val labelAr: String) {
    ALL_WORDS("القاموس الشامل"),
    COMMON_TOPICS("موضوعات شائعة"),
    CONFUSABLES("متشابهات مربكة"),
    IRREGULAR_VERBS("أفعال شاذة"),
    PREPOSITIONS("حروف الجر"),
    COLLOCATIONS("متلازمات لفظية"),
    TRANSLATION("مصطلحات الترجمة")
}

@Composable
fun StudyDictionaryToolScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var bundle by remember { mutableStateOf<OfflineDictionaryBundle?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(DictionarySectionTab.ALL_WORDS) }
    var selectedTopic by remember { mutableStateOf("الكل") }
    var selectedPosFilter by remember { mutableStateOf("الكل") }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        bundle = DictionaryAssetLoader.loadBundle(context)
    }

    fun speakEnglish(text: String) {
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dict_${text.hashCode()}")
        } catch (_: Throwable) {
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp),
                    color = Color(0xFF141E30),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dict_tool_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع",
                                    tint = Color.White
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "القَامُوسُ الشَّامِلُ",
                                    fontFamily = HeaderBrandAmiriFontFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF5B041)
                                )
                                Text(
                                    text = bundle?.let { "${it.totalItemCount} مادة تعليمية بدون إنترنت" } ?: "جاري تحميل القاموس...",
                                    fontFamily = DisplayArabicFontFamily,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Spacer(modifier = Modifier.size(48.dp))
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ابحث بالإنجليزية أو العربية...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (searchQuery.isNotBlank()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dict_tool_search_input")
                        )
                    }
                }

                // Category tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DictionarySectionTab.entries.forEach { tab ->
                        val count = when (tab) {
                            DictionarySectionTab.ALL_WORDS -> bundle?.curriculumWords?.size ?: 0
                            DictionarySectionTab.COMMON_TOPICS -> bundle?.commonWords?.size ?: 0
                            DictionarySectionTab.CONFUSABLES -> bundle?.confusables?.size ?: 0
                            DictionarySectionTab.IRREGULAR_VERBS -> bundle?.irregularVerbs?.size ?: 0
                            DictionarySectionTab.PREPOSITIONS -> bundle?.prepositions?.size ?: 0
                            DictionarySectionTab.COLLOCATIONS -> bundle?.collocations?.size ?: 0
                            DictionarySectionTab.TRANSLATION -> bundle?.translationTerms?.size ?: 0
                        }
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = {
                                activeTab = tab
                                selectedTopic = "الكل"
                            },
                            label = {
                                Text("${tab.labelAr} ($count)", fontWeight = FontWeight.SemiBold)
                            },
                            modifier = Modifier.testTag("dict_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                val currentBundle = bundle
                if (currentBundle == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val q = searchQuery.trim()
                    when (activeTab) {
                        DictionarySectionTab.ALL_WORDS -> {
                            val posOptions = listOf("الكل", "noun", "verb", "adj", "adv")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                posOptions.forEach { pos ->
                                    AssistChip(
                                        onClick = { selectedPosFilter = pos },
                                        label = {
                                            Text(
                                                when (pos) {
                                                    "noun" -> "أسماء"
                                                    "verb" -> "أفعال"
                                                    "adj" -> "صفات"
                                                    "adv" -> "ظروف"
                                                    else -> "الكل"
                                                }
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selectedPosFilter == pos) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                    )
                                }
                            }
                            val filtered = remember(currentBundle, q, selectedPosFilter) {
                                currentBundle.curriculumWords.filter { item ->
                                    val matchesPos = selectedPosFilter == "الكل" || item.pos.equals(selectedPosFilter, ignoreCase = true)
                                    val matchesQuery = q.isEmpty() ||
                                        item.en.contains(q, ignoreCase = true) ||
                                        item.ar.contains(q, ignoreCase = true)
                                    matchesPos && matchesQuery
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.en }) { item ->
                                    DictionaryWordCard(
                                        title = item.en,
                                        subtitle = item.phonetic,
                                        badge = item.pos,
                                        arabicMeaning = item.ar,
                                        example = item.ex,
                                        onSpeak = { speakEnglish(item.en) },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString("${item.en} — ${item.ar}"))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToAnki = {
                                            viewModel.quickConvertWordToAnkiDeck(item.en, item.ar, item.ex)
                                            Toast.makeText(context, "أُضيفت لبطاقات المراجعة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        DictionarySectionTab.COMMON_TOPICS -> {
                            val topics = remember(currentBundle) {
                                listOf("الكل") + currentBundle.commonWords.map { it.topic }.filter { it.isNotBlank() }.distinct()
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                topics.forEach { t ->
                                    FilterChip(
                                        selected = selectedTopic == t,
                                        onClick = { selectedTopic = t },
                                        label = { Text(t) }
                                    )
                                }
                            }
                            val filtered = remember(currentBundle, q, selectedTopic) {
                                currentBundle.commonWords.filter { w ->
                                    (selectedTopic == "الكل" || w.topic == selectedTopic) &&
                                        (q.isEmpty() || w.en.contains(q, ignoreCase = true) || w.ar.contains(q, ignoreCase = true))
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { item ->
                                    DictionaryWordCard(
                                        title = item.en,
                                        subtitle = "",
                                        badge = item.topic,
                                        arabicMeaning = item.ar,
                                        example = "",
                                        onSpeak = { speakEnglish(item.en) },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString("${item.en} — ${item.ar}"))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToAnki = {
                                            viewModel.quickConvertWordToAnkiDeck(item.en, item.ar)
                                            Toast.makeText(context, "أُضيفت لبطاقات المراجعة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        DictionarySectionTab.CONFUSABLES -> {
                            val filtered = remember(currentBundle, q) {
                                currentBundle.confusables.filter {
                                    q.isEmpty() || it.pair.contains(q, ignoreCase = true) || it.explanationAr.contains(q, ignoreCase = true)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.pair,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                IconButton(onClick = { speakEnglish(item.pair.replace("/", " versus ")) }) {
                                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "نطق الكلمات")
                                                }
                                            }
                                            Text(
                                                text = item.explanationAr,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        DictionarySectionTab.IRREGULAR_VERBS -> {
                            val filtered = remember(currentBundle, q) {
                                currentBundle.irregularVerbs.filter {
                                    q.isEmpty() || it.present.contains(q, true) || it.past.contains(q, true) ||
                                        it.pp.contains(q, true) || it.ar.contains(q, true)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { v ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${v.present}  →  ${v.past}  →  ${v.pp}",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = v.ar,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(onClick = { speakEnglish("${v.present}, ${v.past}, ${v.pp}") }) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "نطق التصريفات")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DictionarySectionTab.PREPOSITIONS -> {
                            val filtered = remember(currentBundle, q) {
                                currentBundle.prepositions.filter {
                                    q.isEmpty() || it.expr.contains(q, true) || it.ar.contains(q, true) || it.type.contains(q, true)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { p ->
                                    DictionaryWordCard(
                                        title = p.expr,
                                        subtitle = "",
                                        badge = p.type,
                                        arabicMeaning = p.ar,
                                        example = "",
                                        onSpeak = { speakEnglish(p.expr) },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString("${p.expr} — ${p.ar}"))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToAnki = {
                                            viewModel.quickConvertWordToAnkiDeck(p.expr, p.ar)
                                            Toast.makeText(context, "أُضيفت لبطاقات المراجعة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        DictionarySectionTab.COLLOCATIONS -> {
                            val filtered = remember(currentBundle, q) {
                                currentBundle.collocations.filter {
                                    q.isEmpty() || it.phrase.contains(q, true) || it.verb.contains(q, true) || it.ar.contains(q, true)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { c ->
                                    DictionaryWordCard(
                                        title = c.phrase,
                                        subtitle = c.verb,
                                        badge = "متلازمة",
                                        arabicMeaning = c.ar,
                                        example = "",
                                        onSpeak = { speakEnglish(c.phrase) },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString("${c.phrase} — ${c.ar}"))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToAnki = {
                                            viewModel.quickConvertWordToAnkiDeck(c.phrase, c.ar)
                                            Toast.makeText(context, "أُضيفت لبطاقات المراجعة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        DictionarySectionTab.TRANSLATION -> {
                            val filtered = remember(currentBundle, q) {
                                currentBundle.translationTerms.filter {
                                    q.isEmpty() || it.en.contains(q, true) || it.ar.contains(q, true) || it.topic.contains(q, true)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered) { t ->
                                    DictionaryWordCard(
                                        title = t.en,
                                        subtitle = "",
                                        badge = t.topic,
                                        arabicMeaning = t.ar,
                                        example = "",
                                        onSpeak = { speakEnglish(t.en) },
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString("${t.en} — ${t.ar}"))
                                            Toast.makeText(context, "تم النسخ", Toast.LENGTH_SHORT).show()
                                        },
                                        onAddToAnki = {
                                            viewModel.quickConvertWordToAnkiDeck(t.en, t.ar)
                                            Toast.makeText(context, "أُضيفت لبطاقات المراجعة", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryWordCard(
    title: String,
    subtitle: String,
    badge: String,
    arabicMeaning: String,
    example: String,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onAddToAnki: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (badge.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onSpeak, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "نطق", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ")
                    }
                    IconButton(onClick = onAddToAnki, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "حفظ للبطاقات", tint = Color(0xFF059669))
                    }
                }
            }

            Text(
                text = arabicMeaning,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (example.isNotBlank()) {
                Text(
                    text = example,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
