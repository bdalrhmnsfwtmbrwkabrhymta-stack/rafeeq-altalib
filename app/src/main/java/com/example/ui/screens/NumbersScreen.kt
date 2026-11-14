package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.utils.NumberToWordsConverter
import com.example.ui.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumbersScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsState()
    val isEnglish = userSettings.appLanguage == "en"

    var searchQuery by remember { mutableStateOf("") }
    var selectedRangeIndex by remember { mutableIntStateOf(0) }

    // TTS Setup
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        ttsInstance.language = Locale.US
        tts = ttsInstance

        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    val speakText = { text: String ->
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "number_tts")
        }
    }

    // Range definitions
    val ranges = listOf(
        "1 - 20" to (1..20),
        "21 - 100" to (21..100),
        "100 - 1,000" to listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000),
        "1,000 - 10,000" to listOf(1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000),
        "10,000 - 100,000" to listOf(10000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000),
        "100,000 - 1,000,000" to listOf(100000, 250000, 500000, 750000, 999999, 1000000)
    )

    val numbersToDisplay = remember(searchQuery, selectedRangeIndex) {
        val searchedNum = searchQuery.toIntOrNull()
        if (searchedNum != null && searchedNum in 1..1_000_000) {
            listOf(searchedNum)
        } else {
            val range = ranges[selectedRangeIndex].second
            when (range) {
                is IntRange -> range.toList()
                is List<*> -> range.filterIsInstance<Int>()
                else -> listOf(1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEnglish) "English Numbers (1 to 1,000,000) 🔢" else "الأرقام بالإنجليزية (من 1 إلى 1,000,000) 🔢",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (isEnglish) "Learn spelling, pronunciation & translations" else "تعلم كتابة، نطق، وترجمة جميع الأرقام حتى مليون",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // Search / Jump to any number input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { input ->
                if (input.isEmpty() || (input.all { it.isDigit() } && (input.toLongOrNull() ?: 0) <= 1_000_000)) {
                    searchQuery = input
                }
            },
            placeholder = { Text(if (isEnglish) "Enter any number (1 - 1,000,000)..." else "اكتب أي رقم (مثال: 54321)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("number_search_input")
        )

        // Range selector chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ranges.indices.toList()) { index ->
                FilterChip(
                    selected = selectedRangeIndex == index && searchQuery.isEmpty(),
                    onClick = {
                        searchQuery = ""
                        selectedRangeIndex = index
                    },
                    label = {
                        Text(
                            text = ranges[index].first,
                            fontSize = 12.sp,
                            fontWeight = if (selectedRangeIndex == index && searchQuery.isEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // List of Numbers
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(numbersToDisplay) { num ->
                val englishText = NumberToWordsConverter.convertToEnglish(num)
                val arabicText = NumberToWordsConverter.convertToArabic(num)

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            speakText(englishText)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%,d", num),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = englishText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = arabicText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { speakText(englishText) },
                            modifier = Modifier.testTag("speak_number_$num")
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = "Listen",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
