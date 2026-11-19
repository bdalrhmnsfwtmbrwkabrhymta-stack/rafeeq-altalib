package com.example.studytools.translator

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.domain.ai.FirebaseStudyChatRepository
import com.example.domain.dictionary.DictionaryAssetLoader
import com.example.domain.dictionary.OfflineDictionaryBundle
import com.example.ui.theme.DisplayArabicFontFamily
import com.example.ui.theme.HeaderBrandAmiriFontFamily
import kotlinx.coroutines.launch
import java.util.Locale

private data class TranslatorOutput(
    val primaryTranslation: String,
    val sourceBadge: String,
    val wordMatches: List<Pair<String, String>> = emptyList()
)

@Composable
fun StudyTranslatorToolScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var bundle by remember { mutableStateOf<OfflineDictionaryBundle?>(null) }
    var isEnToAr by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<TranslatorOutput?>(null) }
    var isTranslatingAi by remember { mutableStateOf(false) }

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

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputText = spoken
                bundle?.let { b ->
                    output = translateOfflineFirst(b, spoken, isEnToAr)
                }
            }
        }
    }

    fun executeTranslate(tryAiForSentences: Boolean) {
        val text = inputText.trim()
        if (text.isBlank()) {
            Toast.makeText(context, "أدخل كلمة أو جملة للترجمة", Toast.LENGTH_SHORT).show()
            return
        }
        val b = bundle ?: return
        val localResult = translateOfflineFirst(b, text, isEnToAr)
        output = localResult

        val isSinglePhrase = text.split(Regex("\\s+")).size <= 2 && localResult.wordMatches.isNotEmpty()
        if (tryAiForSentences && !isSinglePhrase) {
            coroutineScope.launch {
                isTranslatingAi = true
                val prompt = if (isEnToAr) {
                    "ترجم النص التالي إلى لغة عربية فصحى واضحة وأكاديمية دون مقدمات أو تعليقات إضافية:\n$text"
                } else {
                    "Translate the following Arabic text into clear natural academic English without extra commentary:\n$text"
                }
                val aiRes = runCatching {
                    FirebaseStudyChatRepository(context).askStudentAssistant(prompt)
                }.getOrNull()?.getOrNull()?.trim()

                if (!aiRes.isNullOrBlank() && !aiRes.contains("لم أتمكن") && !aiRes.contains("API")) {
                    output = localResult.copy(
                        primaryTranslation = aiRes,
                        sourceBadge = "ترجمة ذكية دقيقة"
                    )
                }
                isTranslatingAi = false
            }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141E30),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("translator_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "المُتَرْجِمُ الدِّرَاسِيُّ",
                                fontFamily = HeaderBrandAmiriFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF5B041)
                            )
                            Text(
                                text = "إنجليزي ⇄ عربي بمصطلحات المنهج والأكاديميا",
                                fontFamily = DisplayArabicFontFamily,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                // Direction Switcher
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isEnToAr) "الإنجليزية" else "العربية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                isEnToAr = !isEnToAr
                                output = null
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("translator_swap_button")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "تبديل اللغة", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            text = if (isEnToAr) "العربية" else "الإنجليزية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF059669)
                        )
                    }
                }

                // Input Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = {
                                Text(if (isEnToAr) "اكتب كلمة أو نصاً بالإنجليزية..." else "اكتب كلمة أو جملة بالعربية...")
                            },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translator_input_field")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isEnToAr) "en-US" else "ar-EG")
                                        }
                                        voiceLauncher.launch(intent)
                                    } catch (_: Throwable) {
                                        Toast.makeText(context, "الإملاء الصوتي غير مدعوم على هذا الجهاز", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "إدخال صوتي")
                            }

                            if (inputText.isNotBlank()) {
                                IconButton(
                                    onClick = { inputText = ""; output = null },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = { executeTranslate(tryAiForSentences = true) },
                                enabled = !isTranslatingAi && bundle != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("translator_submit_button")
                            ) {
                                if (isTranslatingAi) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.Translate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("ترجمة الآن", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Quick curriculum phrase chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("sustainable development", "artificial intelligence", "knowledge").forEach { chip ->
                        AssistChip(
                            onClick = {
                                isEnToAr = true
                                inputText = chip
                                executeTranslate(tryAiForSentences = false)
                            },
                            label = { Text(chip, fontSize = 11.5.sp) }
                        )
                    }
                }

                // Result Card
                output?.let { res ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, Color(0xFF059669).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("translator_result_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = res.sourceBadge,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Row {
                                    val spokenTarget = if (isEnToAr) inputText else res.primaryTranslation
                                    IconButton(
                                        onClick = {
                                            runCatching {
                                                tts.language = Locale.US
                                                tts.speak(spokenTarget, TextToSpeech.QUEUE_FLUSH, null, "tr_${spokenTarget.hashCode()}")
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "نطق بالإنجليزية")
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(res.primaryTranslation))
                                            Toast.makeText(context, "تم نسخ الترجمة", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ")
                                    }
                                }
                            }

                            Text(
                                text = res.primaryTranslation,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (res.wordMatches.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    text = "تطابقات ومعانٍ ذات صلة من القاموس:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                res.wordMatches.take(8).forEach { (term, gloss) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = term,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = gloss,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1.2f)
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
}

private fun translateOfflineFirst(
    bundle: OfflineDictionaryBundle,
    rawQuery: String,
    enToAr: Boolean
): TranslatorOutput {
    val q = rawQuery.trim()
    val matches = mutableListOf<Pair<String, String>>()
    val seen = HashSet<String>()

    fun record(term: String, meaning: String) {
        val key = term.lowercase().trim()
        if (seen.add(key)) {
            matches.add(term to meaning)
        }
    }

    if (enToAr) {
        val qLower = q.lowercase()
        // 1. Exact phrase match in Translation Terms, Curriculum Words, Common Words, Prepositions, Collocations
        bundle.translationTerms.firstOrNull { it.en.equals(qLower, ignoreCase = true) }?.let {
            return TranslatorOutput(
                primaryTranslation = it.ar,
                sourceBadge = "مصطلح ترجمة فوري (${it.topic})",
                wordMatches = listOf(it.en to it.ar)
            )
        }
        bundle.curriculumWords.firstOrNull { it.en.equals(qLower, ignoreCase = true) }?.let {
            val extra = bundle.collocations.filter { c -> c.phrase.contains(qLower, true) }.map { c -> c.phrase to c.ar }
            return TranslatorOutput(
                primaryTranslation = it.ar,
                sourceBadge = "القاموس الشامل (${it.pos})",
                wordMatches = listOf(it.en to it.ar) + extra
            )
        }
        bundle.commonWords.firstOrNull { it.en.equals(qLower, ignoreCase = true) }?.let {
            return TranslatorOutput(
                primaryTranslation = it.ar,
                sourceBadge = "المفردات الشائعة (${it.topic})",
                wordMatches = listOf(it.en to it.ar)
            )
        }

        // 2. Word-by-word gloss + partial phrase lookup
        val words = qLower.split(Regex("[^a-zA-Z-]+")).filter { it.isNotBlank() }
        val wordLookup = buildMap {
            bundle.curriculumWords.forEach { put(it.en.lowercase(), it.ar.substringBefore("/").trim()) }
            bundle.commonWords.forEach { putIfAbsent(it.en.lowercase(), it.ar.substringBefore("/").trim()) }
            bundle.irregularVerbs.forEach {
                val base = it.ar.substringBefore("/").trim()
                putIfAbsent(it.present.lowercase(), base)
                putIfAbsent(it.past.lowercase(), base)
                putIfAbsent(it.pp.lowercase(), base)
            }
        }
        val glossPieces = mutableListOf<String>()
        words.forEach { w ->
            val found = wordLookup[w]
                ?: wordLookup[w.removeSuffix("s")]
                ?: wordLookup[w.removeSuffix("ed")]
                ?: wordLookup[w.removeSuffix("ing")]
            if (found != null) {
                glossPieces.add("$w ($found)")
                record(w, found)
            }
        }

        bundle.translationTerms.filter { it.en.contains(qLower, true) }.take(5).forEach { record(it.en, it.ar) }
        bundle.curriculumWords.filter { it.en.contains(qLower, true) }.take(5).forEach { record(it.en, it.ar) }

        val combined = if (glossPieces.isNotEmpty()) {
            glossPieces.joinToString(" • ")
        } else if (matches.isNotEmpty()) {
            matches.first().second
        } else {
            "لم يطابق هذا التعبير كلمة مفردة في القاموس المحلي — عند الاتصال بالإنترنت يتم استخدام المترجم الذكي تلقائياً."
        }
        return TranslatorOutput(
            primaryTranslation = combined,
            sourceBadge = "المترجم الفوري بدون إنترنت",
            wordMatches = matches
        )
    } else {
        // Arabic -> English lookup
        bundle.translationTerms.filter { it.ar.contains(q, true) }.forEach { record(it.ar, it.en) }
        bundle.curriculumWords.filter { it.ar.contains(q, true) }.take(8).forEach { record(it.ar, it.en) }
        bundle.commonWords.filter { it.ar.contains(q, true) }.take(8).forEach { record(it.ar, it.en) }

        val primary = matches.firstOrNull()?.second
            ?: "اكتب الكلمة الأساسية أو اتصل بالإنترنت للترجمة السياقية الكاملة."
        return TranslatorOutput(
            primaryTranslation = primary,
            sourceBadge = "بحث عربي - إنجليزي محلي",
            wordMatches = matches
        )
    }
}
