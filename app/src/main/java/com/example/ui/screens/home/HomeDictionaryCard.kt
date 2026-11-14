package com.example.ui.screens.home

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DictionaryWordEntity
import com.example.ui.MainViewModel
import java.util.Locale

@Composable
fun HomeDictionaryCard(
    viewModel: MainViewModel,
    onOpenDictionary: () -> Unit,
    onSearchWord: (String) -> Unit,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var quickQuery by remember { mutableStateOf("") }
    val favoriteWords by viewModel.favoriteWords.collectAsState()

    // Sample curated daily words
    val dailyWords = remember {
        listOf(
            DictionaryWordEntity(
                wordEnglish = "Perseverance",
                wordArabic = "المثابرة والإصرار على النجاح",
                phonetic = "/ˌpɜː.sɪˈvɪə.rəns/",
                partOfSpeech = "noun",
                exampleSentence = "Perseverance is the secret of all study triumphs.",
                emoji = "🔥",
                category = "القيم والتحفيز"
            ),
            DictionaryWordEntity(
                wordEnglish = "Hypothesis",
                wordArabic = "فرضية علمية قابلة للاختبار",
                phonetic = "/haɪˈpɒθ.ə.sɪs/",
                partOfSpeech = "noun",
                exampleSentence = "The scientist proposed a bold new hypothesis.",
                emoji = "🧪",
                category = "مصطلحات علمية"
            ),
            DictionaryWordEntity(
                wordEnglish = "Accomplish",
                wordArabic = "يُنجز / يُحقق هدفاً بنجاح",
                phonetic = "/əˈkʌm.plɪʃ/",
                partOfSpeech = "verb",
                exampleSentence = "You can accomplish great goals with daily effort.",
                emoji = "🎯",
                category = "أفعال أكاديمية"
            ),
            DictionaryWordEntity(
                wordEnglish = "Equilibrium",
                wordArabic = "حالة اتزان واستقرار كيميائي أو فيزيائي",
                phonetic = "/ˌek.wɪˈlɪb.ri.əm/",
                partOfSpeech = "noun",
                exampleSentence = "The chemical reaction reached dynamic equilibrium.",
                emoji = "⚖️",
                category = "العلوم والكيمياء"
            )
        )
    }

    val dayIndex = remember { (System.currentTimeMillis() / (1000 * 60 * 60 * 24) % dailyWords.size).toInt() }
    val todayWord = dailyWords[dayIndex]

    // TextToSpeech State
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speak(text: String) {
        ttsEngine?.let { engine ->
            engine.language = Locale.US
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "home_dict_tts")
        }
    }

    val primaryBlue = Color(0xFF1E40AF)
    val royalIndigo = Color(0xFF3B82F6)
    val accentCyan = Color(0xFF06B6D4)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_dictionary_card")
            .clickable { onOpenDictionary() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, royalIndigo.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(primaryBlue, royalIndigo)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (isEnglish) "Student Dictionary & Vocab 📚" else "قاموس الطالب ومفردات اليوم 📚",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isEnglish) "2500+ Words, Pronunciation & Translation" else "أكثر من 2500 كلمة، نطق صوتي وترجمة فورية",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenDictionary,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Open" else "فتح",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Body: Word of the Day Highlight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Word of the Day Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = if (isEnglish) "⭐ Word of the Day" else "⭐ كلمة اليوم للطالب",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            IconButton(
                                onClick = { speak(todayWord.wordEnglish) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Pronounce",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = todayWord.wordEnglish,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (todayWord.phonetic.isNotBlank()) {
                                Text(
                                    text = todayWord.phonetic,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = todayWord.partOfSpeech,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Text(
                            text = todayWord.wordArabic,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (todayWord.exampleSentence.isNotBlank()) {
                            Text(
                                text = "💡 \"${todayWord.exampleSentence}\"",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Search Bar
                OutlinedTextField(
                    value = quickQuery,
                    onValueChange = {
                        quickQuery = it
                        viewModel.updateSearchQuery(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_dictionary_search_input"),
                    placeholder = {
                        Text(
                            text = if (isEnglish) "Search 2500+ words or translate..." else "ابحث في أكثر من 2500 كلمة أو مصطلح...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (quickQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onSearchWord(quickQuery)
                                    onOpenDictionary()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick Category Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickFilters = if (isEnglish) {
                        listOf("Academic 🎓" to "Academic", "Verbs ⚡" to "verb", "Saved ⭐" to "favorites")
                    } else {
                        listOf("مصطلحات أكاديمية 🎓" to "Academic", "أفعال شائعة ⚡" to "verb", "المفضلة ⭐" to "favorites")
                    }

                    quickFilters.forEach { (label, filterKey) ->
                        SuggestionChip(
                            onClick = {
                                if (filterKey == "favorites") {
                                    onOpenDictionary()
                                } else {
                                    viewModel.updateSearchQuery(filterKey)
                                    onOpenDictionary()
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
