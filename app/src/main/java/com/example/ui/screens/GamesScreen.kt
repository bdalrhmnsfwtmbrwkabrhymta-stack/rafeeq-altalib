package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.CurvedHeader
import com.example.ui.components.Premium3DIcon
import com.example.ui.theme.*

data class GameCardItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val badge: String,
    val pastelColor: Color
)

@Composable
fun GamesScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    val games = listOf(
        GameCardItem(
            title = "مركز الألعاب التعليمية • ركض المتفوقين 🏆",
            description = "لعبة جري ثلاثية المسارات بـ 12 مرحلة متدرجة وزعماء وصناديق كنز ومتجر 6 شخصيات ومناهج الثانوية",
            icon = Icons.Default.SportsEsports,
            route = "educational_games_hub",
            badge = "12 مرحلة",
            pastelColor = PastelBlue
        ),
        GameCardItem(
            title = "الشطرنج الذكي ضد الـ AI ♟️",
            description = "مباراة شطرنج تفاعلية بمستويات متعددة لتنمية مهارات التفكير والتخطيط",
            icon = Icons.Default.SportsEsports,
            route = "chess",
            badge = "لعبة استراتيجية",
            pastelColor = PastelPink
        ),
        GameCardItem(
            title = "ألعاب العقل وتنشيط الذاكرة 🧠",
            description = "تحديات الذاكرة وسرعة البديهة والتركيز وألعاب الربط الذهني",
            icon = Icons.Default.Psychology,
            route = "brain_games",
            badge = "تنشيط الذهن",
            pastelColor = PastelPurple
        ),
        GameCardItem(
            title = "تحديات وبنك أسئلة المذاكرة 🎯",
            description = "اختبر معلوماتك في المواد الدراسية واجمع النقاط والجوائز",
            icon = Icons.Default.EmojiEvents,
            route = "quests",
            badge = "نقاط ومستويات",
            pastelColor = PastelYellow
        ),
        GameCardItem(
            title = "تحدي الكلمات الإنجليزية والترجمة ⚡",
            description = "لعبة ممتعة لتعلم وحفظ الكلمات الإنجليزية الشائعة بالمطابقة",
            icon = Icons.Default.Translate,
            route = "english_learning",
            badge = "لغة إنجليزية",
            pastelColor = PastelBlue
        ),
        GameCardItem(
            title = "أرقام ومعادلات سريعة 🔢",
            description = "تدريب على الحساب الذهني السريع والعمليات الرياضية المسلية",
            icon = Icons.Default.Calculate,
            route = "numbers",
            badge = "رياضيات",
            pastelColor = PastelMint
        )
    )

    Scaffold(
        containerColor = BackgroundLight
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                CurvedHeader(
                    title = "ألعاب المذاكرة والذكاء 🎮",
                    subtitle = "نمّ تركيزك وقدراتك العقلية واستمتع باستراحة مفيدة بين فترات المذاكرة",
                    gradientColors = listOf(GamesCrimsonDark, GamesCrimsonPrimary),
                    onMenuClick = onOpenDrawer
                )
            }

            // Gamification highlight banner
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GamesCrimsonContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GamesCrimsonLight.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Premium3DIcon(
                            icon = Icons.Default.MilitaryTech,
                            primaryColor = GamesCrimsonPrimary,
                            containerSize = 46.dp,
                            enablePulse = true
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "العب بذكاء واجمع النقاط! 🏆",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = GamesCrimsonDark
                            )
                            Text(
                                text = "كل لعبة تكملها تمنحك نقاطاً ترفع تصنيفك وتفتح لك مكافآت جديدة.",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Games list
            items(games.size) { index ->
                val game = games[index]
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clickable { onNavigate(game.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Premium3DIcon(
                            icon = game.icon,
                            primaryColor = GamesCrimsonPrimary,
                            containerSize = 46.dp,
                            iconSize = 24.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = game.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = game.pastelColor
                                ) {
                                    Text(
                                        text = game.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GamesCrimsonPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = game.description,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                lineHeight = 17.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = GamesCrimsonLight,
                            modifier = Modifier.size(18.dp).padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
