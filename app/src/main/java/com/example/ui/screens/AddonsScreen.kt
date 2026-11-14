package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

data class AddonItem(
    val id: String,
    val title: String,
    val route: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val iconTintColor: Color = Color.White,
    val badge: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val addonsList = listOf(
        AddonItem(
            id = "dictionary",
            title = "القاموس الإنجليزي",
            route = "dictionary",
            icon = Icons.Default.AutoStories,
            iconBgColor = Color(0xFF2563EB) // Royal Blue Dictionary
        ),
        AddonItem(
            id = "translate",
            title = "المترجم الفوري",
            route = "dictionary",
            icon = Icons.Default.Translate,
            iconBgColor = Color(0xFF0284C7) // Sky Blue Translator
        ),
        AddonItem(
            id = "english_learning",
            title = "تعلم الإنجليزية",
            route = "english_learning",
            icon = Icons.Default.Language,
            iconBgColor = Color(0xFFE11D48) // Rose Red English Hub
        ),
        AddonItem(
            id = "pomodoro",
            title = "مؤقت الدراسة",
            route = "pomodoro",
            icon = Icons.Default.AccessTime,
            iconBgColor = Color(0xFFEF4444) // Red Alarm Clock
        ),
        AddonItem(
            id = "documents",
            title = "مستنداتي",
            route = "documents",
            icon = Icons.Default.MenuBook,
            iconBgColor = Color(0xFFF472B6) // Coral/Pink Stacked Books
        ),
        AddonItem(
            id = "notes",
            title = "المذكرة",
            route = "notes",
            icon = Icons.Default.EditNote,
            iconBgColor = Color(0xFF06B6D4) // Cyan Notebook & Pen
        ),
        AddonItem(
            id = "flashcards",
            title = "بطاقة الدراسة",
            route = "flashcards",
            icon = Icons.Default.HelpOutline,
            iconBgColor = Color(0xFFD946EF) // Magenta Question Mark
        ),
        AddonItem(
            id = "calculator",
            title = "الآلة الحاسبة",
            route = "calculator",
            icon = Icons.Default.Calculate,
            iconBgColor = Color(0xFF84CC16) // Yellow/Green Calculator
        ),
        AddonItem(
            id = "zakat",
            title = "حاسبة الزكاة",
            route = "zakat",
            icon = Icons.Default.Paid,
            iconBgColor = Color(0xFFF59E0B) // Amber Gold Coins
        ),
        AddonItem(
            id = "qibla",
            title = "بوصلة القبلة",
            route = "qibla",
            icon = Icons.Default.Explore,
            iconBgColor = Color(0xFF0284C7) // Blue Compass
        ),
        AddonItem(
            id = "quran",
            title = "المصحف الشريف",
            route = "quran",
            icon = Icons.Default.MenuBook,
            iconBgColor = Color(0xFF047857) // Deep Emerald Quran
        ),
        AddonItem(
            id = "azkar",
            title = "أذكار ومواقيت",
            route = "prayer",
            icon = Icons.Default.Mosque,
            iconBgColor = Color(0xFF10B981) // Emerald Green Dhikr
        ),
        AddonItem(
            id = "chemistry",
            title = "معمل الكيمياء",
            route = "chemistry",
            icon = Icons.Default.Science,
            iconBgColor = Color(0xFF8B5CF6) // Purple Chemistry
        ),
        AddonItem(
            id = "whiteboard",
            title = "السبورة الذكية",
            route = "whiteboard",
            icon = Icons.Default.Draw,
            iconBgColor = Color(0xFFEC4899) // Pink Board
        ),
        AddonItem(
            id = "brain_games",
            title = "ألعاب الذكاء",
            route = "brain_games",
            icon = Icons.Default.Psychology,
            iconBgColor = Color(0xFF6366F1) // Indigo Brain
        ),
        AddonItem(
            id = "habits",
            title = "العادات اليومية",
            route = "habits",
            icon = Icons.Default.Spa,
            iconBgColor = Color(0xFF8B5CF6) // Elegant Purple
        ),
        AddonItem(
            id = "idea_vault",
            title = "مخزن الأفكار",
            route = "idea_vault",
            icon = Icons.Default.Lightbulb,
            iconBgColor = Color(0xFFF59E0B), // Warm Amber Lightbulb
            badge = "جديد"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إضافات",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header Helper Box (Matches Image 4)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "مساعده على الدراسه",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "أدوات تساعدك في دراستك",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2-Column Grid matching Image 4
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(addonsList) { addon ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clickable { onNavigate(addon.route) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = addon.iconBgColor,
                                modifier = Modifier.size(54.dp),
                                shadowElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = addon.icon,
                                        contentDescription = addon.title,
                                        tint = addon.iconTintColor,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = addon.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
