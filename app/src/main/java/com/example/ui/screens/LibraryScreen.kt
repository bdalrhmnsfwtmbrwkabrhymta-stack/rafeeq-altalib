package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.CurvedHeader
import com.example.ui.components.Premium3DIcon
import com.example.ui.screens.library.LocalStudyLibraryPalette
import com.example.ui.screens.library.StudyLibraryColors
import com.example.ui.screens.library.StudyLibraryScreen
import com.example.ui.screens.library.StudyLibraryTypography
import com.example.ui.theme.*

data class LibraryCategory(
    val id: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val route: String,
    val bgPastel: Color,
    val accentColor: Color,
    val countBadge: String
)

@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit,
    onOpenDrawer: () -> Unit
) {
    // Tab 0: المكتبة الدراسية (المناهج والكتب - الفولدرات الزجاجية)
    // Tab 1: أدوات وموارد المكتبة (جميع أدوات التطبيق الحالية محفوظة 100%)
    var selectedSectionTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf(
            LibraryCategory(
                id = "pdf",
                title = "استوديو الـ PDF والكتب",
                desc = "فتح وتظليل وتلخيص الكتب والملازم الدراسية",
                icon = Icons.Default.PictureAsPdf,
                route = "pdf_studio",
                bgPastel = PastelBlue,
                accentColor = LibraryBluePrimary,
                countBadge = "قارئ متقدم"
            ),
            LibraryCategory(
                id = "docs",
                title = "مستنداتي وملفاتي",
                desc = "إدارة وتنظيم كافة ملفات المواد والمحاضرات",
                icon = Icons.Default.Folder,
                route = "documents",
                bgPastel = PastelCyan,
                accentColor = Color(0xFF0284C7),
                countBadge = "ملفات الطالب"
            ),
            LibraryCategory(
                id = "quran",
                title = "المصحف الشريف",
                desc = "قراءة القرآن الكريم مع التفاسير والبحث في الآيات",
                icon = Icons.Default.MenuBook,
                route = "quran",
                bgPastel = PastelMint,
                accentColor = EmeraldPrimary,
                countBadge = "114 سورة"
            ),
            LibraryCategory(
                id = "quran_audio",
                title = "التلاوات والاستماع",
                desc = "تلاوات عذبة لكبار القراء مع التحميل والمتابعة",
                icon = Icons.Default.Headphones,
                route = "quran_audio",
                bgPastel = PastelPurple,
                accentColor = NavPurple,
                countBadge = "صوتيات"
            ),
            LibraryCategory(
                id = "flashcards",
                title = "البطاقات التعليمية (Anki)",
                desc = "مراجعة ذكية بالـ Spaced Repetition وتثبيت الحفظ",
                icon = Icons.Default.Style,
                route = "flashcards",
                bgPastel = PastelYellow,
                accentColor = ToolsOrangePrimary,
                countBadge = "تكرار متباعد"
            ),
            LibraryCategory(
                id = "notes",
                title = "المذكرات والملاحظات",
                desc = "تدوين ملخصات الدروس وأفكار المحاضرات واستخراج النصوص",
                icon = Icons.Default.EditNote,
                route = "notes",
                bgPastel = PastelPink,
                accentColor = GamesCrimsonPrimary,
                countBadge = "ملاحظاتك"
            ),
            LibraryCategory(
                id = "dictionary",
                title = "قاموس الطالب الشامل",
                desc = "أكثر من 2500 كلمة ومصطلح باللغتين العربية والإنجليزية",
                icon = Icons.Default.Translate,
                route = "dictionary",
                bgPastel = PastelBlue,
                accentColor = LibraryBluePrimary,
                countBadge = "2500+ كلمة"
            ),
            LibraryCategory(
                id = "ocr",
                title = "الماسح الضوئي الذكي (OCR)",
                desc = "تصوير الصفحات واستخراج النصوص وتلخيصها فورياً",
                icon = Icons.Default.DocumentScanner,
                route = "image_ocr_summary",
                bgPastel = PastelCyan,
                accentColor = Color(0xFF0D9488),
                countBadge = "ذكاء اصطناعي"
            )
        )
    }

    val filtered = categories.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery) || it.desc.contains(searchQuery)
    }

    if (selectedSectionTab == 0) {
        StudyLibraryScreen(
            viewModel = viewModel,
            onNavigate = onNavigate,
            onOpenDrawer = onOpenDrawer,
            headerExtraAction = {
                val palette = LocalStudyLibraryPalette.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.surfaceCard.copy(alpha = 0.86f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { selectedSectionTab = 0 },
                        shape = RoundedCornerShape(10.dp),
                        color = StudyLibraryColors.AccentYellow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_study_library_books")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = StudyLibraryColors.AccentYellowInk,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المناهج والفولدرات",
                                fontFamily = StudyLibraryTypography.BodyFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = StudyLibraryColors.AccentYellowInk
                            )
                        }
                    }

                    Surface(
                        onClick = { selectedSectionTab = 1 },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_study_library_tools")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                tint = palette.inkSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "أدوات المكتبة (8)",
                                fontFamily = StudyLibraryTypography.BodyFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = palette.inkSecondary
                            )
                        }
                    }
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = BackgroundLight,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Curved Header (Library Blue Theme)
            item {
                CurvedHeader(
                    title = "المكتبة الدراسية 📚",
                    subtitle = "كل المواد، الكتب، المذكرات والمصاحف في مكان واحد",
                    gradientColors = listOf(LibraryBlueDark, LibraryBluePrimary),
                    onMenuClick = onOpenDrawer,
                    extraContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = { selectedSectionTab = 0 },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "المناهج والفولدرات",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { selectedSectionTab = 1 },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "أدوات المكتبة (8)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = LibraryBlueDark
                                        )
                                    }
                                }
                            }

                            // Search bar inside curved header
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث في المكتبة والملفات والكتب...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White.copy(alpha = 0.6f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            }

            // Quick Stats Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LibraryStatBadge("📖 114 سورة", "المصحف", PastelMint, EmeraldPrimary, Modifier.weight(1f))
                    LibraryStatBadge("📑 كتب ومراجع", "الـ PDF", PastelBlue, LibraryBluePrimary, Modifier.weight(1f))
                    LibraryStatBadge("💡 بطاقات حفظ", "Anki", PastelYellow, ToolsOrangePrimary, Modifier.weight(1f))
                }
            }

            // Category Cards
            items(filtered.chunked(2).size) { rowIndex ->
                val rowItems = filtered.chunked(2)[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (cat in rowItems) {
                        LibraryCard(
                            category = cat,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(cat.route) }
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    category: LibraryCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Premium3DIcon(
                    icon = category.icon,
                    primaryColor = category.accentColor,
                    containerSize = 40.dp,
                    iconSize = 20.dp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = category.bgPastel
                ) {
                    Text(
                        text = category.countBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = category.accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = category.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category.desc,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun LibraryStatBadge(
    title: String,
    subtitle: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(text = subtitle, fontSize = 10.sp, color = textColor.copy(alpha = 0.8f))
        }
    }
}
