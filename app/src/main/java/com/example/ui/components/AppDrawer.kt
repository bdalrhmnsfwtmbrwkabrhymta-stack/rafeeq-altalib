package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserSettings
import com.example.ui.theme.*

data class DrawerMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null,
    val color: Color
)

/**
 * AppDrawer:
 * Material 3 ModalNavigationDrawer sheet with student stats and shortcuts.
 */
@Composable
fun AppDrawerContent(
    userSettings: UserSettings,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    val menuItems = listOf(
        DrawerMenuItem("home", "الرئيسية", Icons.Default.Home, null, EmeraldPrimary),
        DrawerMenuItem("library", "المكتبة الدراسية", Icons.Default.LocalLibrary, "جديد", LibraryBluePrimary),
        DrawerMenuItem("games", "ألعاب المذاكرة", Icons.Default.SportsEsports, null, GamesCrimsonPrimary),
        DrawerMenuItem("plans", "خطتي والجداول", Icons.Default.CalendarMonth, null, PlansTealPrimary),
        DrawerMenuItem("tools", "أدوات المذاكرة", Icons.Default.Construction, null, ToolsOrangePrimary),
        DrawerMenuItem("community", "المجتمع والطلاب", Icons.Default.People, null, NavPurple),
        DrawerMenuItem("quran", "المصحف الشريف", Icons.Default.MenuBook, null, EmeraldPrimary),
        DrawerMenuItem("prayer", "الإسلاميات", Icons.Default.Mosque, null, Color(0xFF0284C7)),
        DrawerMenuItem("ai", "فهيم • اسأل واشرح", Icons.Default.TipsAndUpdates, "اسأل", VibrantEmerald),
        DrawerMenuItem("pdf_studio", "استوديو الـ PDF", Icons.Default.PictureAsPdf, null, PlayRed),
        DrawerMenuItem("account", "حسابي والإعدادات", Icons.Default.Settings, null, TextSecondary)
    )

    ModalDrawerSheet(
        modifier = Modifier.width(310.dp),
        drawerContainerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            // Header: Student Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AccentRoyalBlue, VibrantPurple)
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎓", fontSize = 22.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = userSettings.userName.ifBlank { "طالب مجتهد" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = userSettings.studentGradeYear.ifBlank { "المرحلة الثانوية" },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "⭐ ${userSettings.studentPoints} نقطة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EnergyGold.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = "🔥 مستمر",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Navigation items list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(menuItems) { item ->
                    val isSelected = currentRoute == item.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) item.color.copy(alpha = 0.12f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigate(item.id)
                                onCloseDrawer()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isSelected) item.color else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) item.color else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.badge != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = item.color,
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Text(
                                        text = item.badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom note
            Text(
                text = "هوس الدراسة • طريقك نحو مستقبل أفضل",
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            )
        }
    }
}
