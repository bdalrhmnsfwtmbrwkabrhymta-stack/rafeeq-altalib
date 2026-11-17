package com.example.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RealisticQuickShortcut(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val titleFr: String? = null,
    val icon: ImageVector,
    val route: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    val glowColor: Color,
    val badgeEmoji: String? = null
)

@Composable
fun QuickShortcutsGrid(
    isEnglish: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    lang: String = if (isEnglish) "en" else "ar"
) {
    // Precise shortcuts matching Screenshot 3 & user instructions
    val shortcuts = listOf(
        // Row 1: عاداتي, حاسبة, جدول AI, الملاحظات
        RealisticQuickShortcut(
            id = "habits",
            titleAr = "عاداتي",
            titleEn = "My Habits",
            titleFr = "Habitudes",
            icon = Icons.Default.Spa,
            route = "habits",
            gradientTop = Color(0xFF8B5CF6),
            gradientBottom = Color(0xFF6D28D9),
            glowColor = Color(0xFF7C3AED),
            badgeEmoji = "🌱"
        ),
        RealisticQuickShortcut(
            id = "calculator",
            titleAr = "حاسبة",
            titleEn = "Calculator",
            titleFr = "Calculatrice",
            icon = Icons.Default.Calculate,
            route = "calculator",
            gradientTop = Color(0xFF3B82F6),
            gradientBottom = Color(0xFF1D4ED8),
            glowColor = Color(0xFF2563EB)
        ),
        RealisticQuickShortcut(
            id = "study_schedule_ai",
            titleAr = "جدول AI",
            titleEn = "AI Schedule",
            titleFr = "Planning IA",
            icon = Icons.Default.AutoAwesome,
            route = "study_schedule_ai",
            gradientTop = Color(0xFF8B5CF6),
            gradientBottom = Color(0xFF6D28D9),
            glowColor = Color(0xFF7C3AED)
        ),
        RealisticQuickShortcut(
            id = "notes",
            titleAr = "الملاحظات",
            titleEn = "Notes",
            titleFr = "Notes",
            icon = Icons.Default.EditNote,
            route = "notes",
            gradientTop = Color(0xFF34D399),
            gradientBottom = Color(0xFF059669),
            glowColor = Color(0xFF10B981)
        ),
        RealisticQuickShortcut(
            id = "idea_vault",
            titleAr = "مخزن الأفكار",
            titleEn = "Idea Vault",
            titleFr = "Boîte à Idées",
            icon = Icons.Default.Lightbulb,
            route = "idea_vault",
            gradientTop = Color(0xFFFBBF24),
            gradientBottom = Color(0xFFD97706),
            glowColor = Color(0xFFF59E0B)
        ),

        // Row 2: القاموس, تعلم الإنجليزية, بطاقات, مرحلتي
        RealisticQuickShortcut(
            id = "dictionary",
            titleAr = "القاموس",
            titleEn = "Dictionary",
            titleFr = "Dictionnaire",
            icon = Icons.Default.AutoStories,
            route = "dictionary",
            gradientTop = Color(0xFF2563EB),
            gradientBottom = Color(0xFF1D4ED8),
            glowColor = Color(0xFF3B82F6)
        ),
        RealisticQuickShortcut(
            id = "english_learning",
            titleAr = "تعلم الإنجليزية",
            titleEn = "Learn English",
            titleFr = "Apprendre l'anglais",
            icon = Icons.Default.Language,
            route = "english_learning",
            gradientTop = Color(0xFFF43F5E),
            gradientBottom = Color(0xFFBE123C),
            glowColor = Color(0xFFE11D48)
        ),
        RealisticQuickShortcut(
            id = "flashcards",
            titleAr = "بطاقات",
            titleEn = "Flashcards",
            titleFr = "Cartes",
            icon = Icons.Default.Style,
            route = "flashcards",
            gradientTop = Color(0xFFFB923C),
            gradientBottom = Color(0xFFC2410C),
            glowColor = Color(0xFFEA580C)
        ),
        RealisticQuickShortcut(
            id = "grade_selection",
            titleAr = "مرحلتي",
            titleEn = "My Grade",
            titleFr = "Niveau",
            icon = Icons.Default.School,
            route = "grade_selection",
            gradientTop = Color(0xFF2DD4BF),
            gradientBottom = Color(0xFF0F766E),
            glowColor = Color(0xFF14B8A6)
        ),

        // Row 3: مواقيت, المصحف, محرر ومراجع, حاسبة الزكاة
        RealisticQuickShortcut(
            id = "prayer",
            titleAr = "مواقيت",
            titleEn = "Prayer Times",
            titleFr = "Heures Prière",
            icon = Icons.Default.Mosque,
            route = "prayer",
            gradientTop = Color(0xFF059669),
            gradientBottom = Color(0xFF064E3B),
            glowColor = Color(0xFF10B981)
        ),
        RealisticQuickShortcut(
            id = "quran",
            titleAr = "المصحف",
            titleEn = "Holy Quran",
            titleFr = "Saint Coran",
            icon = Icons.Default.MenuBook,
            route = "quran",
            gradientTop = Color(0xFF10B981),
            gradientBottom = Color(0xFF064E3B),
            glowColor = Color(0xFF047857)
        ),
        RealisticQuickShortcut(
            id = "pdf_studio",
            titleAr = "محرر ومراجع",
            titleEn = "PDF Studio",
            titleFr = "Studio PDF",
            icon = Icons.Default.PictureAsPdf,
            route = "pdf_studio",
            gradientTop = Color(0xFFEF4444),
            gradientBottom = Color(0xFFB91C1C),
            glowColor = Color(0xFFDC2626)
        ),
        RealisticQuickShortcut(
            id = "zakat",
            titleAr = "حاسبة الزكاة",
            titleEn = "Zakat Calc",
            titleFr = "Calcul Zakat",
            icon = Icons.Default.Paid,
            route = "zakat",
            gradientTop = Color(0xFFF59E0B),
            gradientBottom = Color(0xFFB45309),
            glowColor = Color(0xFFD97706)
        ),

        // Row 4: بوصلة القبلة, المساعد الذكي, ألعاب الذكاء, شطرنج
        RealisticQuickShortcut(
            id = "qibla",
            titleAr = "بوصلة القبلة",
            titleEn = "Qibla",
            icon = Icons.Default.Explore,
            route = "qibla",
            gradientTop = Color(0xFF38BDF8),
            gradientBottom = Color(0xFF0369A1),
            glowColor = Color(0xFF0284C7)
        ),
        RealisticQuickShortcut(
            id = "ai",
            titleAr = "المساعد الذكي",
            titleEn = "AI Assistant",
            icon = Icons.Default.SmartToy,
            route = "ai",
            gradientTop = Color(0xFF6366F1),
            gradientBottom = Color(0xFF3730A3),
            glowColor = Color(0xFF4F46E5)
        ),
        RealisticQuickShortcut(
            id = "brain_games",
            titleAr = "ألعاب الذكاء",
            titleEn = "Brain Games",
            icon = Icons.Default.Psychology,
            route = "brain_games",
            gradientTop = Color(0xFFA855F7),
            gradientBottom = Color(0xFF581C87),
            glowColor = Color(0xFF7E22CE)
        ),
        RealisticQuickShortcut(
            id = "chess",
            titleAr = "شطرنج",
            titleEn = "Chess",
            titleFr = "Échecs",
            icon = Icons.Default.Extension,
            route = "chess",
            gradientTop = Color(0xFF10B981),
            gradientBottom = Color(0xFF047857),
            glowColor = Color(0xFF059669)
        ),

        // Row 5: أسماء الله, حصن الأذكار, إعراب الجمل, مهام المغامرة
        RealisticQuickShortcut(
            id = "god_names",
            titleAr = "أسماء الله",
            titleEn = "Names of Allah",
            icon = Icons.Default.AutoStories,
            route = "god_names",
            gradientTop = Color(0xFF84CC16),
            gradientBottom = Color(0xFF365314),
            glowColor = Color(0xFF4D7C0F)
        ),
        RealisticQuickShortcut(
            id = "azkar",
            titleAr = "حصن الأذكار",
            titleEn = "Daily Azkar",
            icon = Icons.Default.VolunteerActivism,
            route = "azkar",
            gradientTop = Color(0xFF2DD4BF),
            gradientBottom = Color(0xFF115E59),
            glowColor = Color(0xFF0D9488)
        ),
        RealisticQuickShortcut(
            id = "irab",
            titleAr = "إعراب الجمل",
            titleEn = "Grammar Parser",
            titleFr = "Analyse Arabe",
            icon = Icons.Default.Spellcheck,
            route = "irab",
            gradientTop = Color(0xFF8B5CF6),
            gradientBottom = Color(0xFF635BFF),
            glowColor = Color(0xFF635BFF),
            badgeEmoji = "✍️"
        ),
        RealisticQuickShortcut(
            id = "quests",
            titleAr = "مهام المغامرة",
            titleEn = "Quests",
            titleFr = "Quêtes",
            icon = Icons.Default.EmojiEvents,
            route = "quests",
            gradientTop = Color(0xFFA855F7),
            gradientBottom = Color(0xFF6B21A8),
            glowColor = Color(0xFF9333EA)
        ),

        // Row 6: بحث جوجل AI, تفريغ الصوت, موسيقى التركيز, حساب فايربيس
        RealisticQuickShortcut(
            id = "search_grounding",
            titleAr = "بحث جوجل AI",
            titleEn = "Google Search",
            icon = Icons.Default.TravelExplore,
            route = "ai",
            gradientTop = Color(0xFF4285F4),
            gradientBottom = Color(0xFF1A73E8),
            glowColor = Color(0xFF4285F4)
        ),
        RealisticQuickShortcut(
            id = "audio_transcribe",
            titleAr = "تفريغ الصوت",
            titleEn = "Transcribe",
            icon = Icons.Default.Mic,
            route = "ai",
            gradientTop = Color(0xFFE91E63),
            gradientBottom = Color(0xFFAD1457),
            glowColor = Color(0xFFD81B60)
        ),
        RealisticQuickShortcut(
            id = "music_generator",
            titleAr = "موسيقى التركيز",
            titleEn = "Focus Music",
            icon = Icons.Default.MusicNote,
            route = "ai",
            gradientTop = Color(0xFFEC4899),
            gradientBottom = Color(0xFF8B5CF6),
            glowColor = Color(0xFFA855F7)
        ),
        RealisticQuickShortcut(
            id = "rafid_downloader",
            titleAr = "رَافِد الذكي",
            titleEn = "Rafid Media",
            titleFr = "Rafid Médias",
            icon = Icons.Default.CloudDownload,
            route = "rafid",
            gradientTop = Color(0xFF1E88E5),
            gradientBottom = Color(0xFF1565C0),
            glowColor = Color(0xFF1565C0)
        )
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerTitle = when (lang) {
                "ar" -> "⚡ أدوات وميزات التطبيق"
                "fr" -> "⚡ Outils & Fonctionnalités"
                else -> "⚡ Quick Tools & Features"
            }
            val toolsCountText = when (lang) {
                "ar" -> "${shortcuts.size} أداة مفعلة"
                "fr" -> "${shortcuts.size} Outils actifs"
                else -> "${shortcuts.size} Active Tools"
            }
            Text(
                text = headerTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = toolsCountText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2574C)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val rows = shortcuts.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowShortcuts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowShortcuts.forEach { shortcut ->
                        Card(
                            onClick = { onNavigate(shortcut.route) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 96.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 2.dp,
                                pressedElevation = 6.dp
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 3D Glowing Squircle Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = shortcut.glowColor.copy(alpha = 0.4f),
                                            spotColor = shortcut.glowColor
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(shortcut.gradientTop, shortcut.gradientBottom)
                                            )
                                        )
                                        .border(1.2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = shortcut.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    shortcut.badgeEmoji?.let { emoji ->
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                        ) {
                                            Text(text = emoji, fontSize = 9.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(7.dp))

                                val itemTitle = when (lang) {
                                    "ar" -> shortcut.titleAr
                                    "fr" -> shortcut.titleFr ?: shortcut.titleEn
                                    else -> shortcut.titleEn
                                }

                                Text(
                                    text = itemTitle,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    repeat(4 - rowShortcuts.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
