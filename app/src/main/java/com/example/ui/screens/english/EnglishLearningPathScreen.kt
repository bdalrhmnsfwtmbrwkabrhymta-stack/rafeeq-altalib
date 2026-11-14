package com.example.ui.screens.english

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LearningPathUnitItem(
    val id: String,
    val title: String,
    val isUnlocked: Boolean,
    val statusText: String, // e.g. "الأنسب دلوقتي", "بدأت فيه", "مقفول"
    val progressPercentage: Int, // e.g. 80, 94, 100, 0
    val prerequisiteText: String = "",
    val isRecommendedNow: Boolean = false
)

data class LearningPathSection(
    val sectionTitle: String,
    val badgeLabel: String,
    val badgeColor: Color,
    val hasPlacementTest: Boolean = true,
    val units: List<LearningPathUnitItem>
)

/**
 * Full Hierarchical Learning Path Screen matching Screenshot 7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishLearningPathScreen(
    userLevelCode: Int,
    onNavigateBack: () -> Unit,
    onSelectUnit: (String) -> Unit,
    onStartPlacementTest: () -> Unit
) {
    val purpleGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFF9F7FF), Color(0xFFF4F0FF), Color(0xFFFFFFFF))
    )

    // Build the exact sections from Screenshot 7
    val sections = remember(userLevelCode) {
        listOf(
            // 1. أول خطوة (مبتدئ تماماً)
            LearningPathSection(
                sectionTitle = "أول خطوة",
                badgeLabel = "مبتدئ تماماً",
                badgeColor = Color(0xFFC084FC),
                units = listOf(
                    LearningPathUnitItem("letter_a", "حرف A", true, "الأنسب دلوقتي", 80, isRecommendedNow = true),
                    LearningPathUnitItem("letter_b", "حرف B", true, "بدأت فيه", 94),
                    LearningPathUnitItem("letter_c", "حرف C", true, "بدأت فيه", 100),
                    LearningPathUnitItem("letter_sounds", "أصوات الحروف", true, "بدأت فيه", 93),
                    LearningPathUnitItem("first_words", "أول تلات كلمات", true, "بدأت فيه", 100)
                )
            ),
            // 2. أنا وإنت (ما قبل A1)
            LearningPathSection(
                sectionTitle = "أنا وإنت",
                badgeLabel = "ما قبل A1",
                badgeColor = Color(0xFF818CF8),
                units = listOf(
                    LearningPathUnitItem("pronoun_i", "الضمير I", true, "بدأت فيه", 50),
                    LearningPathUnitItem("pronoun_you", "الضمير You", true, "بدأت فيه", 82),
                    LearningPathUnitItem("am_with_i", "am مع I", true, "بدأت فيه", 80),
                    LearningPathUnitItem("are_with_you", "are مع You", true, "بدأت فيه", 93),
                    LearningPathUnitItem("first_sentence", "أول جملة", true, "بدأت فيه", 0)
                )
            ),
            // 3. قواعد A1 (A1)
            LearningPathSection(
                sectionTitle = "قواعد A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFF34D399),
                units = listOf(
                    LearningPathUnitItem("subject_pronouns", "ضمائر الفاعل", true, "بدأت فيه", 87),
                    LearningPathUnitItem("is_with_he_she", "is مع هو وهي", true, "بدأت فيه", 99),
                    LearningPathUnitItem("negation_be", "النفي مع be", true, "بدأت فيه", 80),
                    LearningPathUnitItem("question_be", "السؤال مع be", true, "بدأت فيه", 100),
                    LearningPathUnitItem("a_and_an", "a و an", true, "بدأت فيه", 100),
                    LearningPathUnitItem("plurals", "الجمع", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: a و an"),
                    LearningPathUnitItem("that_and_this", "that و this", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: is مع هو وهي، الجمع"),
                    LearningPathUnitItem("has_and_have", "has و have", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: ضمائر الفاعل"),
                    LearningPathUnitItem("present_simple", "المضارع البسيط", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: ضمائر الفاعل"),
                    LearningPathUnitItem("s_he_she", "الـ s مع هو وهي", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: المضارع البسيط، is مع هو وهي"),
                    LearningPathUnitItem("negation_dont_doesnt", "النفي: don't / doesn't", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: الـ s مع هو وهي"),
                    LearningPathUnitItem("question_do_does", "السؤال: Do / Does", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: النفي: don't / doesn't"),
                    LearningPathUnitItem("possessives", "الملكية: my, his, her", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: ضمائر الفاعل"),
                    LearningPathUnitItem("there_is_are", "there is / there are", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: is مع هو وهي، الجمع"),
                    LearningPathUnitItem("modal_can", "can: أقدر", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: المضارع البسيط"),
                    LearningPathUnitItem("prepositions_place", "حروف الجر للمكان", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: there is / there are"),
                    LearningPathUnitItem("translate_a1_sentences", "ترجم جمل A1", true, "بدأت فيه", 80)
                )
            ),
            // 4. نطق A1
            LearningPathSection(
                sectionTitle = "نطق A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFFF472B6),
                units = listOf(
                    LearningPathUnitItem("pronounce_b_p", "b و p", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_v_f", "v و f", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_th_s", "(th) ث و s", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_th_d", "(th) ذ و d", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_sheep_ship", "sheep و ship", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_bed_bad", "bed و bad", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_ch_sh", "ch و sh", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_ng", "ng في الآخر", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف"),
                    LearningPathUnitItem("pronounce_r_l", "r و l", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أصوات الحروف")
                )
            ),
            // 5. استماع A1
            LearningPathSection(
                sectionTitle = "استماع A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFF38BDF8),
                units = listOf(
                    LearningPathUnitItem("listening_numbers_time", "أرقام ووقت بالسمع", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أول جملة"),
                    LearningPathUnitItem("listening_dictation", "إملاء جمل قصيرة", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: أول جملة"),
                    LearningPathUnitItem("listening_short_dialogue", "حوار قصير", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: إملاء جمل قصيرة"),
                    LearningPathUnitItem("listening_directions", "اتجاهات", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: حوار قصير، حروف الجر للمكان"),
                    LearningPathUnitItem("listening_normal_speed", "سرعة عادية", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: حوار قصير")
                )
            ),
            // 6. تحدث A1
            LearningPathSection(
                sectionTitle = "تحدث A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFFA78BFA),
                units = listOf(
                    LearningPathUnitItem("speaking_cafe", "في الكافيه", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: السؤال مع be، الأكل والشرب"),
                    LearningPathUnitItem("speaking_introduce_self", "عرف نفسك", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: السؤال مع be، have و has"),
                    LearningPathUnitItem("speaking_ask_place", "اسأل عن مكان", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: حروف الجر للمكان، اتجاهات"),
                    LearningPathUnitItem("speaking_doctor", "عند الدكتور", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: النفي: don't / doesn't، have و has")
                )
            ),
            // 7. قراءة A1
            LearningPathSection(
                sectionTitle = "قراءة A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFFFBBF24),
                units = listOf(
                    LearningPathUnitItem("reading_family", "عيلتي", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: have و has، العيلة والناس"),
                    LearningPathUnitItem("reading_day_in_life", "يوم في حياة سارة", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: الـ s مع هو وهي، أفعال كل يوم"),
                    LearningPathUnitItem("reading_market", "في السوق", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: الجمع، الأكل والشرب"),
                    LearningPathUnitItem("reading_my_room", "أوضتي", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: there is / there are، البيت")
                )
            ),
            // 8. كتابة A1
            LearningPathSection(
                sectionTitle = "كتابة A1",
                badgeLabel = "A1",
                badgeColor = Color(0xFF4ADE80),
                units = listOf(
                    LearningPathUnitItem("writing_self", "اكتب عن نفسك", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: have و has، النفي مع be"),
                    LearningPathUnitItem("writing_family", "اكتب عن عيلتك", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: الملكية my, his, her"),
                    LearningPathUnitItem("writing_day", "اكتب عن يومك", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: الأيام والوقت"),
                    LearningPathUnitItem("writing_room", "اكتب عن أوضتك", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0, "محتاج الأول: حروف الجر للمكان")
                )
            ),
            // 9. قواعد A2
            LearningPathSection(
                sectionTitle = "قواعد A2",
                badgeLabel = "A2",
                badgeColor = Color(0xFFFB923C),
                units = listOf(
                    LearningPathUnitItem("past_was_were", "الماضي: was / were", userLevelCode >= 3, if (userLevelCode >= 3) "بدأت فيه" else "مقفول", if (userLevelCode >= 3) 100 else 0),
                    LearningPathUnitItem("past_simple_ed", "الماضي البسيط: ed-", userLevelCode >= 3, if (userLevelCode >= 3) "بدأت فيه" else "مقفول", if (userLevelCode >= 3) 100 else 0),
                    LearningPathUnitItem("past_irregular", "أفعال الماضي الشاذة", userLevelCode >= 3, if (userLevelCode >= 3) "بدأت فيه" else "مقفول", if (userLevelCode >= 3) 100 else 0),
                    LearningPathUnitItem("past_did_didnt", "الماضي: didn't / Did", userLevelCode >= 3, if (userLevelCode >= 3) "بدأت فيه" else "مقفول", if (userLevelCode >= 3) 100 else 0),
                    LearningPathUnitItem("present_continuous", "المضارع المستمر", userLevelCode >= 3, if (userLevelCode >= 3) "بدأت فيه" else "مقفول", if (userLevelCode >= 3) 100 else 0),
                    LearningPathUnitItem("future_going_to", "المستقبل: going to", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: المضارع المستمر"),
                    LearningPathUnitItem("future_will", "المستقبل: will", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: المستقبل: going to"),
                    LearningPathUnitItem("comparatives", "المقارنة: more / er-", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: الجمع"),
                    LearningPathUnitItem("superlatives", "التفضيل: the most / -est", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: المقارنة: more / er-"),
                    LearningPathUnitItem("quantifiers", "some / any / much / many", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: a و an، الجمع"),
                    LearningPathUnitItem("object_pronouns", "ضمائر المفعول: me, him, her", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: الملكية: my, his, her"),
                    LearningPathUnitItem("prepositions_time", "حروف الجر للوقت: at / on / in", userLevelCode >= 3, if (userLevelCode >= 3) "مفتوح" else "مقفول", 0, "محتاج الأول: الأيام والوقت")
                )
            ),
            // 10. أول كلمات (ما قبل A1)
            LearningPathSection(
                sectionTitle = "أول كلمات",
                badgeLabel = "ما قبل A1",
                badgeColor = Color(0xFF818CF8),
                units = listOf(
                    LearningPathUnitItem("words_greetings", "التحيات", true, "مفتوح", 100),
                    LearningPathUnitItem("words_numbers_1_10", "الأرقام من ١ لـ ١٠", true, "مفتوح", 100),
                    LearningPathUnitItem("words_family", "العيلة والناس", true, "مفتوح", 100),
                    LearningPathUnitItem("words_colors", "الألوان", true, "مفتوح", 100)
                )
            ),
            // 11. كلمات الحياة اليومية (A1)
            LearningPathSection(
                sectionTitle = "كلمات الحياة اليومية",
                badgeLabel = "A1",
                badgeColor = Color(0xFF34D399),
                units = listOf(
                    LearningPathUnitItem("words_food_drink", "الأكل والشرب", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_days_time", "الأيام والوقت", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_daily_verbs", "أفعال كل يوم", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_home", "البيت", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_restaurant", "في المطعم", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_taxi_travel", "تاكسي وسفر", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0),
                    LearningPathUnitItem("words_shopping_health", "تسوق وصحة", userLevelCode >= 2, if (userLevelCode >= 2) "مفتوح" else "مقفول", 0)
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "مسار التعلم",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B),
                            modifier = Modifier.padding(end = 40.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = Color(0xFF1E1B4B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF9F7FF))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(purpleGradient)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            sections.forEach { section ->
                // Section Header matching Screenshot 7
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = section.badgeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = section.badgeLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = section.badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اختبار",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C5CFC),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onStartPlacementTest() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = section.sectionTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E1B4B)
                        )
                    }
                }

                // Section Unit Cards
                items(section.units.size) { uIndex ->
                    val unit = section.units[uIndex]
                    val isUnlocked = unit.isUnlocked

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isUnlocked) { onSelectUnit(unit.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnlocked) Color.White else Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(
                            1.dp,
                            if (unit.isRecommendedNow) Color(0xFF7C5CFC) else if (isUnlocked) Color(0xFFE2E8F0) else Color(0xFFF1F5F9)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Status Badge & Progress
                                Column(horizontalAlignment = Alignment.Start) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (unit.isRecommendedNow) Icons.Default.Star
                                            else if (isUnlocked) Icons.Default.PlayCircleOutline
                                            else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (unit.isRecommendedNow) Color(0xFF7C5CFC)
                                            else if (isUnlocked) Color(0xFFF59E0B)
                                            else Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = unit.statusText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (unit.isRecommendedNow) Color(0xFF7C5CFC)
                                            else if (isUnlocked) Color(0xFFD97706)
                                            else Color(0xFF94A3B8)
                                        )
                                    }

                                    if (unit.progressPercentage > 0) {
                                        Text(
                                            text = "• %${unit.progressPercentage} بتتعلمه",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }

                                // Right Unit Title
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = unit.title,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isUnlocked) Color(0xFF1E1B4B) else Color(0xFF64748B)
                                    )

                                    if (!isUnlocked && unit.prerequisiteText.isNotBlank()) {
                                        Text(
                                            text = unit.prerequisiteText,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Progress Bar for unlocked in-progress units
                            if (isUnlocked && unit.progressPercentage > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { unit.progressPercentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = if (unit.isRecommendedNow) Color(0xFF7C5CFC) else Color(0xFF818CF8),
                                    trackColor = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
